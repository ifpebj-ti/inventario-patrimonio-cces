#!/usr/bin/env python3
"""Rotaciona o certificado do listener HTTPS do Load Balancer da OCI (ADR 0009).

Chamado por push-lb-certificate.sh dentro do container da CLI da OCI (que traz o SDK Python), com
autenticacao por instance principal. Cria um certificado novo no LB com o par Let's Encrypt da VM, troca
o certificado do listener por ele (preservando todo o resto da configuracao do listener, porque o
UpdateListener substitui o objeto inteiro) e remove os certificados antigos que nenhum listener usa.

A chave privada so passa por aqui e pela API da OCI: nunca vai para o Terraform, para o state nem para o
log. Variaveis de ambiente (todas obrigatorias): LB_ID, LB_LISTENER_NAME, LB_CERT_NAME, CERT_DIR.
Opcional: LB_KEEP_CERTS (padrao 2: o novo e o ultimo antigo, para voltar atras) e
LB_ALLOW_MISSING_LISTENER=1 (primeira vez: o listener HTTPS so pode ser criado depois do primeiro certificado,
entao este modo so cria o certificado e diz o nome a usar no Terraform).
"""
import os
import re
import sys

import oci
from oci.load_balancer import models

# Os certificados que este script cria (e so eles) podem ser apagados por ele.
NAME_PREFIX = "inventarium-"
PEM_CERTIFICATE = re.compile(r"-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----\n?", re.S)


def log(message):
    print("[lb-rotate-certificate] " + message, flush=True)


def fail(message):
    print("[lb-rotate-certificate] ERRO: " + message, file=sys.stderr, flush=True)
    sys.exit(1)


def required_env(name):
    value = os.environ.get(name, "")
    if not value:
        fail(name + " nao definido")
    return value


def split_fullchain(fullchain):
    """Separa o certificado do site (primeiro bloco) da cadeia intermediaria (os demais)."""
    blocks = PEM_CERTIFICATE.findall(fullchain)
    if not blocks:
        fail("fullchain.pem sem nenhum certificado PEM")
    return blocks[0], "".join(blocks[1:])


def ssl_details_with(ssl, certificate_name):
    return models.SSLConfigurationDetails(
        verify_depth=ssl.verify_depth,
        verify_peer_certificate=ssl.verify_peer_certificate,
        has_session_resumption=ssl.has_session_resumption,
        trusted_certificate_authority_ids=ssl.trusted_certificate_authority_ids,
        certificate_ids=None,
        certificate_name=certificate_name,
        server_order_preference=ssl.server_order_preference,
        cipher_suite_name=ssl.cipher_suite_name,
        protocols=ssl.protocols,
    )


def listener_update_details(listener, certificate_name):
    return models.UpdateListenerDetails(
        default_backend_set_name=listener.default_backend_set_name,
        port=listener.port,
        protocol=listener.protocol,
        hostname_names=listener.hostname_names,
        path_route_set_name=listener.path_route_set_name,
        ssl_configuration=ssl_details_with(listener.ssl_configuration, certificate_name),
        connection_configuration=listener.connection_configuration,
        rule_set_names=listener.rule_set_names,
        routing_policy_name=listener.routing_policy_name,
    )


def wait_for(client, response):
    """Espera o work request da operacao terminar; a rotacao so vale depois de SUCCEEDED."""
    work_request_id = response.headers.get("opc-work-request-id")
    if not work_request_id:
        return
    oci.wait_until(
        client,
        client.get_work_request(work_request_id),
        evaluate_response=lambda r: r.data.lifecycle_state in ("SUCCEEDED", "FAILED"),
        max_wait_seconds=300,
    )
    state = client.get_work_request(work_request_id).data.lifecycle_state
    if state != "SUCCEEDED":
        fail("work request " + work_request_id + " terminou em " + str(state))


def certificates_in_use(lb):
    names = set()
    for listener in (lb.listeners or {}).values():
        if listener.ssl_configuration is not None:
            names.add(listener.ssl_configuration.certificate_name)
    for backend_set in (lb.backend_sets or {}).values():
        if backend_set.ssl_configuration is not None:
            names.add(backend_set.ssl_configuration.certificate_name)
    return names


def delete_old_certificates(client, lb_id, keep):
    lb = client.get_load_balancer(lb_id).data
    in_use = certificates_in_use(lb)
    unused = sorted(
        name
        for name in (lb.certificates or {})
        if name.startswith(NAME_PREFIX) and name not in in_use
    )
    # Mantem o novo (em uso) e os `keep - 1` antigos mais recentes, para poder voltar atras.
    for name in unused[: max(0, len(unused) - max(0, keep - 1))]:
        log("removendo o certificado antigo " + name)
        wait_for(client, client.delete_certificate(lb_id, name))


def main():
    lb_id = required_env("LB_ID")
    listener_name = required_env("LB_LISTENER_NAME")
    certificate_name = required_env("LB_CERT_NAME")
    cert_dir = required_env("CERT_DIR")
    keep = int(os.environ.get("LB_KEEP_CERTS", "2"))
    allow_missing_listener = os.environ.get("LB_ALLOW_MISSING_LISTENER") == "1"

    with open(os.path.join(cert_dir, "fullchain.pem")) as handle:
        site_certificate, chain = split_fullchain(handle.read())
    with open(os.path.join(cert_dir, "privkey.pem")) as handle:
        private_key = handle.read()

    signer = oci.auth.signers.InstancePrincipalsSecurityTokenSigner()
    client = oci.load_balancer.LoadBalancerClient({"region": signer.region}, signer=signer)

    lb = client.get_load_balancer(lb_id).data
    listener = (lb.listeners or {}).get(listener_name)
    if listener is None and not allow_missing_listener:
        fail("o listener '" + listener_name + "' nao existe no load balancer (primeira vez? use --bootstrap)")
    if listener is not None and listener.ssl_configuration is None:
        fail("o listener '" + listener_name + "' nao e HTTPS (sem ssl_configuration)")

    try:
        if certificate_name in (lb.certificates or {}):
            log("o certificado " + certificate_name + " ja existe no LB; so falta trocar o listener")
        else:
            log("criando o certificado " + certificate_name)
            wait_for(
                client,
                client.create_certificate(
                    models.CreateCertificateDetails(
                        certificate_name=certificate_name,
                        public_certificate=site_certificate,
                        ca_certificate=chain or None,
                        private_key=private_key,
                    ),
                    lb_id,
                ),
            )
        if listener is None:
            log("o listener " + listener_name + " ainda nao existe: certificado criado, nada a trocar")
            log('no Terraform use lb_bootstrap_certificate_name = "' + certificate_name + '" e lb_https_enabled = true')
            return
        log("trocando o certificado do listener " + listener_name)
        wait_for(
            client,
            client.update_listener(
                listener_update_details(listener, certificate_name), lb_id, listener_name
            ),
        )
        delete_old_certificates(client, lb_id, keep)
    except oci.exceptions.ServiceError as error:
        # So status e codigo: a mensagem da API pode ecoar partes do pedido.
        fail("a API da OCI recusou a operacao (HTTP " + str(error.status) + ", " + str(error.code) + ")")
    log("concluido")


if __name__ == "__main__":
    main()
