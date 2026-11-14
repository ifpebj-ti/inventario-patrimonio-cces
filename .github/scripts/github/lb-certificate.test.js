const test = require("node:test");
const assert = require("node:assert/strict");
const { spawnSync } = require("node:child_process");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

const helper = path.resolve(__dirname, "../../../infra/scripts/app-vm/lb-rotate-certificate.py");

function findPython() {
  for (const candidate of ["python3", "python"]) {
    const result = spawnSync(candidate, ["--version"], { encoding: "utf8" });
    if (result.status === 0 && /Python 3/.test(`${result.stdout}${result.stderr}`)) {
      return candidate;
    }
  }
  return null;
}

const python = findPython();
const skip = python ? false : "python3 indisponível";

// Montado por partes: um bloco PEM de chave privada literal faz o secretlint barrar o commit, mesmo falso.
const PRIVATE_KEY = `${["-----BEGIN", "PRIVATE KEY-----"].join(" ")}\nSEGREDO-DE-TESTE\n${["-----END", "PRIVATE KEY-----"].join(" ")}\n`;
const pem = (body) => `-----BEGIN CERTIFICATE-----\n${body}\n-----END CERTIFICATE-----\n`;
const FULLCHAIN = pem("FOLHA") + pem("INTERMEDIARIA") + pem("RAIZ");

// SDK da OCI falso: registra cada chamada e guarda o estado do LB num JSON, para exercitar o helper sem rede.
const FAKE_OCI = {
  "oci/__init__.py": `
from . import auth, exceptions, load_balancer


def wait_until(client, response, evaluate_response=None, max_wait_seconds=0):
    return response
`,
  "oci/exceptions.py": `
class ServiceError(Exception):
    def __init__(self, status, code):
        super().__init__("falso")
        self.status = status
        self.code = code
`,
  "oci/auth/__init__.py": "from . import signers\n",
  "oci/auth/signers.py": `
class InstancePrincipalsSecurityTokenSigner:
    region = "sa-saopaulo-1"
`,
  "oci/load_balancer/models.py": `
class _Model:
    def __init__(self, **kwargs):
        self.__dict__.update(kwargs)


class SSLConfigurationDetails(_Model):
    pass


class UpdateListenerDetails(_Model):
    pass


class CreateCertificateDetails(_Model):
    pass
`,
  "oci/load_balancer/__init__.py": `
import json
import os

from . import models
from .. import exceptions


class _Obj:
    def __init__(self, data):
        for key, value in (data or {}).items():
            setattr(self, key, _Obj(value) if isinstance(value, dict) else value)

    def __getattr__(self, name):
        # Como os modelos do SDK real: todo atributo existe e e None quando nao foi preenchido.
        return None


class _Response:
    def __init__(self, data=None, headers=None):
        self.data = data
        self.headers = headers or {}


class LoadBalancerClient:
    def __init__(self, config, signer=None):
        assert config["region"] == "sa-saopaulo-1"
        self.path = os.environ["FAKE_LB"]
        self.calls = os.environ["FAKE_CALLS"]

    def _state(self):
        with open(self.path) as handle:
            return json.load(handle)

    def _save(self, state):
        with open(self.path, "w") as handle:
            json.dump(state, handle)

    def _record(self, entry):
        with open(self.calls, "a") as handle:
            handle.write(json.dumps(entry) + "\\n")

    def get_load_balancer(self, lb_id):
        if os.environ.get("FAKE_FAIL_GET"):
            raise exceptions.ServiceError(int(os.environ["FAKE_FAIL_GET"]), "NotAuthorizedOrNotFound")
        state = self._state()
        lb = _Obj({})
        lb.listeners = {name: _Obj(value) for name, value in state["listeners"].items()}
        lb.backend_sets = {name: _Obj(value) for name, value in state["backend_sets"].items()}
        lb.certificates = {name: _Obj({}) for name in state["certificates"]}
        return _Response(lb)

    def create_certificate(self, details, lb_id):
        self._record({
            "op": "create_certificate",
            "lb_id": lb_id,
            "certificate_name": details.certificate_name,
            "public_certificate": details.public_certificate,
            "ca_certificate": details.ca_certificate,
            "private_key_sent": bool(details.private_key),
        })
        state = self._state()
        state["certificates"].append(details.certificate_name)
        self._save(state)
        return _Response()

    def update_listener(self, details, lb_id, listener_name):
        ssl = details.ssl_configuration
        self._record({
            "op": "update_listener",
            "listener_name": listener_name,
            "port": details.port,
            "protocol": details.protocol,
            "default_backend_set_name": details.default_backend_set_name,
            "connection_idle_timeout": details.connection_configuration.idle_timeout,
            "certificate_name": ssl.certificate_name,
            "certificate_ids": ssl.certificate_ids,
            "cipher_suite_name": ssl.cipher_suite_name,
            "protocols": ssl.protocols,
            "server_order_preference": ssl.server_order_preference,
        })
        if os.environ.get("FAKE_FAIL_UPDATE"):
            raise exceptions.ServiceError(400, "InvalidParameter")
        state = self._state()
        state["listeners"][listener_name]["ssl_configuration"]["certificate_name"] = ssl.certificate_name
        self._save(state)
        return _Response()

    def delete_certificate(self, lb_id, certificate_name):
        self._record({"op": "delete_certificate", "certificate_name": certificate_name})
        state = self._state()
        in_use = [
            listener["ssl_configuration"]["certificate_name"]
            for listener in state["listeners"].values()
            if listener.get("ssl_configuration")
        ]
        if certificate_name in in_use:
            raise exceptions.ServiceError(409, "Conflict")
        state["certificates"].remove(certificate_name)
        self._save(state)
        return _Response()

    def get_work_request(self, work_request_id):
        return _Response(_Obj({"lifecycle_state": "SUCCEEDED"}))
`,
};

function setup(lbState) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "lb-cert-"));
  for (const [file, content] of Object.entries(FAKE_OCI)) {
    const target = path.join(dir, "fake", file);
    fs.mkdirSync(path.dirname(target), { recursive: true });
    fs.writeFileSync(target, content);
  }
  const certDir = path.join(dir, "certs");
  fs.mkdirSync(certDir);
  fs.writeFileSync(path.join(certDir, "fullchain.pem"), FULLCHAIN);
  fs.writeFileSync(path.join(certDir, "privkey.pem"), PRIVATE_KEY);
  fs.writeFileSync(path.join(dir, "lb.json"), JSON.stringify(lbState));
  return { dir, certDir };
}

function run(lbState, env = {}) {
  const { dir, certDir } = setup(lbState);
  const result = spawnSync(python, [helper], {
    encoding: "utf8",
    env: {
      ...process.env,
      PYTHONPATH: path.join(dir, "fake"),
      PYTHONDONTWRITEBYTECODE: "1",
      FAKE_LB: path.join(dir, "lb.json"),
      FAKE_CALLS: path.join(dir, "calls.jsonl"),
      LB_ID: "ocid1.loadbalancer.oc1.sa-saopaulo-1.exemplo",
      LB_LISTENER_NAME: "https",
      LB_CERT_NAME: "inventarium-20260901-dddddddd",
      CERT_DIR: certDir,
      ...env,
    },
  });
  const callsFile = path.join(dir, "calls.jsonl");
  const calls = fs.existsSync(callsFile)
    ? fs.readFileSync(callsFile, "utf8").trim().split("\n").filter(Boolean).map((line) => JSON.parse(line))
    : [];
  const state = JSON.parse(fs.readFileSync(path.join(dir, "lb.json"), "utf8"));
  fs.rmSync(dir, { recursive: true, force: true });
  return { result, calls, state };
}

function lbWithHttpsListener(extra = {}) {
  return {
    listeners: {
      http: { default_backend_set_name: "http", port: 80, protocol: "HTTP", ssl_configuration: null },
      https: {
        default_backend_set_name: "app",
        port: 443,
        protocol: "HTTP",
        connection_configuration: { idle_timeout: 330 },
        ssl_configuration: {
          verify_depth: null,
          verify_peer_certificate: false,
          has_session_resumption: false,
          trusted_certificate_authority_ids: null,
          certificate_ids: null,
          certificate_name: "inventarium-20260801-aaaaaaaa",
          server_order_preference: "ENABLED",
          cipher_suite_name: "oci-default-http2-ssl-cipher-suite-v1",
          protocols: ["TLSv1.2", "TLSv1.3"],
        },
      },
    },
    backend_sets: { app: { ssl_configuration: null }, http: { ssl_configuration: null } },
    certificates: [
      "inventarium-20251201-cccccccc",
      "inventarium-20260101-bbbbbbbb",
      "inventarium-20260801-aaaaaaaa",
      "certificado-manual",
    ],
    ...extra,
  };
}

test("rotates the listener certificate and keeps the rest of the listener configuration", { skip }, () => {
  const { result, calls, state } = run(lbWithHttpsListener());

  assert.equal(result.status, 0, result.stderr);
  const create = calls.find((c) => c.op === "create_certificate");
  assert.equal(create.certificate_name, "inventarium-20260901-dddddddd");
  assert.equal(create.public_certificate, pem("FOLHA"), "a folha vai em public_certificate");
  assert.equal(create.ca_certificate, pem("INTERMEDIARIA") + pem("RAIZ"), "a cadeia vai em ca_certificate");
  assert.equal(create.private_key_sent, true);

  const update = calls.find((c) => c.op === "update_listener");
  assert.equal(update.certificate_name, "inventarium-20260901-dddddddd");
  assert.equal(update.listener_name, "https");
  assert.equal(update.port, 443);
  assert.equal(update.protocol, "HTTP");
  assert.equal(update.default_backend_set_name, "app");
  assert.equal(update.connection_idle_timeout, 330, "o idle timeout do listener nao pode se perder");
  assert.deepEqual(update.protocols, ["TLSv1.2", "TLSv1.3"]);
  assert.equal(update.cipher_suite_name, "oci-default-http2-ssl-cipher-suite-v1");
  assert.equal(update.server_order_preference, "ENABLED");
  assert.equal(update.certificate_ids, null, "nome e certificate_ids nao podem coexistir");
  assert.equal(state.listeners.https.ssl_configuration.certificate_name, "inventarium-20260901-dddddddd");
});

test("never leaks the private key to the output", { skip }, () => {
  const { result } = run(lbWithHttpsListener());

  assert.doesNotMatch(`${result.stdout}${result.stderr}`, /SEGREDO-DE-TESTE|PRIVATE KEY/);
});

test("deletes only old unused certificates created by this script and keeps the previous one", { skip }, () => {
  const { result, calls, state } = run(lbWithHttpsListener());

  assert.equal(result.status, 0, result.stderr);
  const deleted = calls.filter((c) => c.op === "delete_certificate").map((c) => c.certificate_name);
  assert.deepEqual(deleted.sort(), ["inventarium-20251201-cccccccc", "inventarium-20260101-bbbbbbbb"]);
  assert.ok(state.certificates.includes("inventarium-20260801-aaaaaaaa"), "o anterior fica para rollback");
  assert.ok(state.certificates.includes("certificado-manual"), "certificado de outro nome nunca e apagado");
  assert.ok(state.certificates.includes("inventarium-20260901-dddddddd"));
});

test("does not create the certificate again when it already exists, but still switches the listener", { skip }, () => {
  const lb = lbWithHttpsListener();
  lb.certificates.push("inventarium-20260901-dddddddd");
  const { result, calls } = run(lb);

  assert.equal(result.status, 0, result.stderr);
  assert.equal(calls.filter((c) => c.op === "create_certificate").length, 0);
  assert.equal(calls.filter((c) => c.op === "update_listener").length, 1);
});

test("fails without touching anything when the listener does not exist", { skip }, () => {
  const { result, calls } = run(lbWithHttpsListener(), { LB_LISTENER_NAME: "inexistente" });

  assert.equal(result.status, 1);
  assert.match(result.stderr, /listener 'inexistente' nao existe/);
  assert.deepEqual(calls, []);
});

test("bootstrap: with no HTTPS listener yet it only creates the certificate and prints the Terraform input", { skip }, () => {
  const { result, calls, state } = run(lbWithHttpsListener(), {
    LB_LISTENER_NAME: "inexistente",
    LB_ALLOW_MISSING_LISTENER: "1",
  });

  assert.equal(result.status, 0, result.stderr);
  assert.deepEqual(
    calls.map((c) => c.op),
    ["create_certificate"],
    "so cria o certificado: nao troca listener nem apaga nada",
  );
  assert.ok(state.certificates.includes("inventarium-20260901-dddddddd"));
  assert.match(result.stdout, /lb_bootstrap_certificate_name = "inventarium-20260901-dddddddd"/);
  assert.match(result.stdout, /lb_https_enabled = true/);
  assert.doesNotMatch(`${result.stdout}${result.stderr}`, /SEGREDO-DE-TESTE|PRIVATE KEY/);
});

test("without bootstrap a missing listener still fails and points to --bootstrap", { skip }, () => {
  const { result, calls } = run(lbWithHttpsListener(), { LB_LISTENER_NAME: "inexistente" });

  assert.equal(result.status, 1);
  assert.match(result.stderr, /--bootstrap/);
  assert.deepEqual(calls, []);
});

test("fails when the listener is not HTTPS", { skip }, () => {
  const { result, calls } = run(lbWithHttpsListener(), { LB_LISTENER_NAME: "http" });

  assert.equal(result.status, 1);
  assert.match(result.stderr, /nao e HTTPS/);
  assert.deepEqual(calls, []);
});

test("a rejected listener update fails with only the status and code, and deletes nothing", { skip }, () => {
  const { result, calls } = run(lbWithHttpsListener(), { FAKE_FAIL_UPDATE: "1" });

  assert.equal(result.status, 1);
  assert.match(result.stderr, /HTTP 400, InvalidParameter/);
  assert.equal(calls.filter((c) => c.op === "delete_certificate").length, 0, "nao apague nada se a troca falhou");
});

test("a 404 when reading the LB (missing IAM policy) fails with a short message and the policy hint, not a traceback", { skip }, () => {
  const { result, calls } = run(lbWithHttpsListener(), { FAKE_FAIL_GET: "404" });

  assert.equal(result.status, 1);
  assert.match(result.stderr, /HTTP 404, NotAuthorizedOrNotFound/);
  assert.match(result.stderr, /policy IAM/);
  assert.doesNotMatch(result.stderr, /Traceback/);
  assert.deepEqual(calls, [], "sem permissao nada e criado nem trocado");
});

test("other API errors on the first read fail cleanly too, without the IAM hint", { skip }, () => {
  const { result } = run(lbWithHttpsListener(), { FAKE_FAIL_GET: "500" });

  assert.equal(result.status, 1);
  assert.match(result.stderr, /HTTP 500/);
  assert.doesNotMatch(result.stderr, /Traceback|policy IAM/);
});

test("requires every input", { skip }, () => {
  for (const name of ["LB_ID", "LB_LISTENER_NAME", "LB_CERT_NAME", "CERT_DIR"]) {
    const { result } = run(lbWithHttpsListener(), { [name]: "" });

    assert.equal(result.status, 1, `${name} vazio deveria falhar`);
    assert.match(result.stderr, new RegExp(`${name} nao definido`));
  }
});
