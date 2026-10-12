#!/usr/bin/env bash
# Executado DENTRO de um container oraclelinux:8 por infra/scripts/test-app-vm.sh. Exercita, no mesmo
# SO e no mesmo Nginx da VM da aplicacao, os scripts de infra/scripts/app-vm que nao dependem de
# systemd, Docker real ou internet de producao:
#   - install-nginx-config.sh: sem certificado, com certificado, rollback e validacao de entrada;
#   - bootstrap-portainer-stack.sh --dry-run: validacao do .env e mascaramento de segredos.
# Espera: /src = infra/ (somente leitura).
set -euo pipefail

fail() { printf 'FALHOU: %s\n' "$*" >&2; exit 1; }
ok() { printf 'ok - %s\n' "$*"; }

dnf -y module enable nginx:1.24 >/dev/null 2>&1 || true
dnf -y install nginx openssl tar jq >/dev/null 2>&1
nginx -v 2>&1

mkdir -p /etc/inventarium /var/www/certbot
write_env() {
  printf 'ACME_EMAIL=a@b.co\nADMIN_CIDRS="%s"\nOBSERVABILITY_IP=%s\n' "$1" "$2" > /etc/inventarium/edge.env
}
write_env "203.0.113.10/32 198.51.100.0/24" "10.42.0.74"

# O container nao tem systemd: systemctl falso (is-active = inativo, demais comandos so registram).
cat > /usr/local/bin/systemctl <<'STUB'
#!/bin/sh
case "$1" in is-active) exit 3 ;; *) echo "[systemctl-stub] $*" ;; esac
STUB
chmod +x /usr/local/bin/systemctl

install_script=/src/scripts/app-vm/install-nginx-config.sh
cp -r /src/nginx/app-vm /tmp/nginx-src

# 1) Primeiro boot: sem certificado so existe a porta 80.
"$install_script" /tmp/nginx-src >/dev/null
[ "$(ls /etc/nginx/conf.d)" = "$(printf '00-hardening.conf\n10-http.conf')" ] \
  || fail "sem certificado deveriam existir apenas 00-hardening.conf e 10-http.conf"
ok "sem certificado: apenas a porta 80"

# 2) Com certificado: todos os vhosts HTTPS e os arquivos gerados.
cert_dir=/etc/letsencrypt/live/inventariumifpe.me
mkdir -p "$cert_dir"
openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj "/CN=inventariumifpe.me" \
  -keyout "$cert_dir/privkey.pem" -out "$cert_dir/fullchain.pem" 2>/dev/null
cp "$cert_dir/fullchain.pem" "$cert_dir/chain.pem"
"$install_script" /tmp/nginx-src >/dev/null 2>&1
for f in 20-front 21-api 30-portainer 31-sonar 32-grafana; do
  [ -f "/etc/nginx/conf.d/$f.conf" ] || fail "vhost $f ausente com certificado presente"
done
ok "com certificado: vhosts HTTPS instalados"

allowlist=/etc/nginx/inventarium/generated/admin-allowlist.conf
upstreams=/etc/nginx/inventarium/generated/upstreams.conf
for line in 'allow 203.0.113.10/32;' 'allow 198.51.100.0/24;' 'deny all;'; do
  grep -qx "$line" "$allowlist" || fail "allowlist de admin sem a linha: $line"
done
for line in 'server 10.42.0.74:9000;' 'server 10.42.0.74:3000;'; do
  grep -q "$line" "$upstreams" || fail "upstreams sem a linha: $line"
done
ok "allowlist e upstreams gerados a partir do edge.env"

# 3) Configuracao invalida: o script deve falhar e restaurar a configuracao anterior.
before="$(md5sum < /etc/nginx/conf.d/21-api.conf)"
echo "diretiva_inexistente on;" >> /tmp/nginx-src/sites-tls/21-api.conf
if "$install_script" /tmp/nginx-src >/dev/null 2>&1; then fail "config invalida foi aceita"; fi
[ "$before" = "$(md5sum < /etc/nginx/conf.d/21-api.conf)" ] || fail "rollback nao restaurou 21-api.conf"
nginx -t >/dev/null 2>&1 || fail "nginx -t falha depois do rollback"
ok "config invalida: falha e rollback para a configuracao anterior"

# 4) Entradas que viram configuracao do Nginx precisam ser validadas (injecao).
cp -r /src/nginx/app-vm /tmp/nginx-src2
write_env "1.2.3.4; return 200" "10.42.0.74"
if "$install_script" /tmp/nginx-src2 >/dev/null 2>&1; then fail "CIDR malicioso foi aceito"; fi
write_env "203.0.113.10/32" "10.42.0.74; evil"
if "$install_script" /tmp/nginx-src2 >/dev/null 2>&1; then fail "OBSERVABILITY_IP malicioso foi aceito"; fi
ok "CIDR e IP invalidos sao rejeitados"

# 5) bootstrap-portainer-stack.sh --dry-run
portainer=/src/scripts/app-vm/bootstrap-portainer-stack.sh
cat > /tmp/ok.env <<'ENV'
# comentario
POSTGRES_USER=app
POSTGRES_PASSWORD=pw=com=igual
POSTGRES_DB=inventory_management
SPRING_MAIL_USERNAME=mail@example.com
SPRING_MAIL_PASSWORD=senha-do-smtp
SECURITY_TOKEN_SECRET=0123456789abcdef0123456789abcdef0123456789abcdef
GOOGLE_OAUTH_CLIENT_ID=id.apps.googleusercontent.com
IMAGE_TAG=v0.4.0
SYSTEM_ADMIN_PASSWORD=
ENV

out="$("$portainer" /tmp/ok.env --dry-run)"
echo "$out" | grep -q '"repositoryURL": "https://github.com/ifpebj-ti/inventario-patrimonio-cces"' || fail "payload sem repositoryURL"
echo "$out" | grep -q '"composeFile": "docker-compose.production.yml"' || fail "payload sem composeFile"
echo "$out" | grep -q '"webhook": "' || fail "payload sem webhook"
echo "$out" | grep -q 'senha-do-smtp\|pw=com=igual\|0123456789abcdef' && fail "dry-run vazou um segredo"
ok "dry-run: payload correto e segredos mascarados"

expect_error() { # <descricao> <regex-da-mensagem> <arquivo.env>
  local msg
  if msg="$("$portainer" "$3" --dry-run 2>&1)"; then fail "$1: deveria falhar"; fi
  echo "$msg" | grep -q "$2" || fail "$1: mensagem inesperada: $msg"
  ok "$1"
}
sed '/POSTGRES_DB/d' /tmp/ok.env > /tmp/missing.env
expect_error ".env sem variavel obrigatoria e rejeitado" "POSTGRES_DB" /tmp/missing.env
sed 's/^SYSTEM_ADMIN_PASSWORD=.*/SYSTEM_ADMIN_PASSWORD=curta/' /tmp/ok.env > /tmp/shortpw.env
expect_error "senha de admin com menos de 12 caracteres e rejeitada" "12 caracteres" /tmp/shortpw.env
sed 's/^SECURITY_TOKEN_SECRET=.*/SECURITY_TOKEN_SECRET=fraco/' /tmp/ok.env > /tmp/weak.env
expect_error "SECURITY_TOKEN_SECRET curto e rejeitado" "32 caracteres" /tmp/weak.env
sed 's/^IMAGE_TAG=.*/IMAGE_TAG=/' /tmp/ok.env > /tmp/notag.env
expect_error "IMAGE_TAG vazio e rejeitado" "IMAGE_TAG" /tmp/notag.env

# Valores entre aspas (comum em .env copiados do desenvolvimento) precisam perder as aspas ANTES das
# validacoes. Casos de fronteira: 31 caracteres entre aspas valem 33 com as aspas, mas so 31 sem elas.
sed 's/^SECURITY_TOKEN_SECRET=.*/SECURITY_TOKEN_SECRET="0123456789abcdef0123456789abcde"/' /tmp/ok.env > /tmp/quoted31.env
expect_error "segredo de 31 caracteres entre aspas e rejeitado (aspas nao contam)" "32 caracteres" /tmp/quoted31.env
sed "s/^SYSTEM_ADMIN_PASSWORD=.*/SYSTEM_ADMIN_PASSWORD='onzecaracte'/" /tmp/ok.env > /tmp/quotedpw.env
expect_error "senha de admin de 11 caracteres entre aspas e rejeitada" "12 caracteres" /tmp/quotedpw.env

# Arquivo copiado do desenvolvimento: aspas em tudo, fim de linha CRLF e chaves que a producao nao usa.
{
  printf 'POSTGRES_USER="app"\r\nPOSTGRES_PASSWORD="senha-forte"\r\nPOSTGRES_DB="inventory_management"\r\n'
  printf 'SPRING_MAIL_USERNAME="mail@example.com"\r\nSPRING_MAIL_PASSWORD="senha-smtp"\r\n'
  printf 'SECURITY_TOKEN_SECRET="0123456789abcdef0123456789abcdef0123456789abcdef"\r\n'
  printf 'GOOGLE_OAUTH_CLIENT_ID="id.apps.googleusercontent.com"\r\nIMAGE_TAG=v5.0.2\r\n'
  printf 'GOOGLE_OAUTH_CLIENT_SECRET="nao-deve-ir-para-o-servidor"\r\nBACKEND_IMAGE="x"\r\nPOSTGRES_PORT="5433"\r\n'
} > /tmp/devcopy.env
out="$("$portainer" /tmp/devcopy.env --dry-run 2>&1)"
echo "$out" | grep -q 'variaveis ignoradas' || fail "dev copy: faltou o aviso de variaveis ignoradas"
echo "$out" | grep -q 'BACKEND_IMAGE, GOOGLE_OAUTH_CLIENT_SECRET, POSTGRES_PORT' || fail "dev copy: lista de ignoradas incorreta: $out"
payload="$(echo "$out" | sed -n '/^{/,$p')"
names="$(echo "$payload" | jq -r '.env[].name' | sort | tr '\n' ' ')"
for must in POSTGRES_USER POSTGRES_PASSWORD POSTGRES_DB IMAGE_TAG SECURITY_TOKEN_SECRET; do
  case " $names" in *" $must "*) ;; *) fail "dev copy: $must deveria estar no payload (tinha: $names)" ;; esac
done
for mustnot in GOOGLE_OAUTH_CLIENT_SECRET BACKEND_IMAGE POSTGRES_PORT; do
  case " $names" in *" $mustnot "*) fail "dev copy: $mustnot nao deveria estar no payload" ;; esac
done
ok "dev copy (aspas + CRLF): aceito, segredos nao usados descartados e os necessarios mantidos"

echo "todos os checks passaram"
