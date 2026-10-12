const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const root = path.resolve(__dirname, "../../../infra/nginx/app-vm");

function read(...parts) {
  return fs.readFileSync(path.join(root, ...parts), "utf8");
}

function listConf(dir) {
  return fs
    .readdirSync(path.join(root, dir))
    .filter((name) => name.endsWith(".conf"))
    .map((name) => ({ name, text: read(dir, name) }));
}

// Extrai os blocos `server { ... }` de nivel superior contando chaves.
function serverBlocks(text) {
  const blocks = [];
  const pattern = /(^|\n)\s*server\s*\{/g;
  let match;
  while ((match = pattern.exec(text)) !== null) {
    let depth = 1;
    let i = pattern.lastIndex;
    while (i < text.length && depth > 0) {
      if (text[i] === "{") depth += 1;
      if (text[i] === "}") depth -= 1;
      i += 1;
    }
    blocks.push(text.slice(pattern.lastIndex, i - 1));
    pattern.lastIndex = i;
  }
  return blocks;
}

function stripComments(text) {
  return text
    .split("\n")
    .filter((line) => !line.trim().startsWith("#"))
    .join("\n");
}

const tlsSites = listConf("sites-tls");
const tlsServers = tlsSites.flatMap(({ name, text }) =>
  serverBlocks(stripComments(text)).map((block) => ({ name, block })),
);
const httpsServers = tlsServers.filter(({ block }) => /listen\s+443/.test(block));
const catchAll = (block) => /server_name\s+_\s*;/.test(block);

test("every HTTPS server loads the TLS and common security headers snippets", () => {
  assert.ok(httpsServers.length >= 6, "esperado ao menos 6 servers 443");

  for (const { name, block } of httpsServers) {
    assert.match(block, /snippets\/tls\.conf/, `${name}: sem tls.conf`);
    if (catchAll(block)) {
      assert.match(block, /return\s+444\s*;/, `${name}: catch-all deve descartar`);
      continue;
    }
    assert.match(block, /snippets\/headers-common\.conf/, `${name}: sem headers-common.conf`);
  }
});

test("sites never declare add_header (it would drop the inherited security headers)", () => {
  for (const { name, text } of tlsSites) {
    assert.doesNotMatch(stripComments(text), /\badd_header\b/, `${name}: add_header em site`);
  }
});

test("common headers: HSTS with subdomains, nosniff, frame and referrer policy, all 'always'", () => {
  const text = stripComments(read("snippets", "headers-common.conf"));

  assert.match(text, /Strict-Transport-Security "max-age=\d+; includeSubDomains" always;/);
  assert.match(text, /X-Content-Type-Options "nosniff" always;/);
  assert.match(text, /X-Frame-Options "DENY" always;/);
  assert.match(text, /Referrer-Policy "[^"]+" always;/);
  assert.doesNotMatch(text, /preload/, "preload exige decisao explicita");
});

test("every add_header in the snippets uses 'always'", () => {
  for (const { name, text } of listConf("snippets")) {
    for (const line of stripComments(text).split("\n")) {
      if (/\badd_header\b/.test(line)) {
        assert.match(line, /\balways\s*;/, `${name}: add_header sem always: ${line.trim()}`);
      }
    }
  }
});

test("frontend CSP blocks framing, plugins and base-uri hijacking and allows only Google sign-in", () => {
  const text = stripComments(read("snippets", "headers-front.conf"));
  const csp = text.match(/Content-Security-Policy(?:-Report-Only)?\s+"([^"]+)"/);

  assert.ok(csp, "CSP ausente");
  const policy = csp[1];
  for (const directive of [
    "default-src 'self'",
    "frame-ancestors 'none'",
    "object-src 'none'",
    "base-uri 'self'",
    "form-action 'self'",
    "upgrade-insecure-requests",
    "frame-src https://accounts.google.com/gsi/",
    "connect-src 'self' https://api.inventariumifpe.me https://accounts.google.com/gsi/",
  ]) {
    assert.ok(policy.includes(directive), `CSP sem: ${directive}`);
  }
  assert.doesNotMatch(policy, /\*(?!\.googleusercontent)/, "CSP nao deve usar curinga amplo");
  assert.doesNotMatch(policy, /unsafe-eval/, "unsafe-eval nao e necessario");
  assert.match(text, /Permissions-Policy "[^"]*camera=\(self\)/);
  assert.match(text, /Cross-Origin-Opener-Policy "same-origin-allow-popups"/);
});

test("frontend server includes the frontend headers; the API server uses API headers", () => {
  const front = tlsServers.find(({ block }) => /server_name\s+inventariumifpe\.me\s*;/.test(block));
  const api = tlsServers.find(({ block }) => /server_name\s+api\.inventariumifpe\.me\s*;/.test(block));

  assert.ok(front && api);
  assert.match(front.block, /snippets\/headers-front\.conf/);
  assert.match(api.block, /snippets\/headers-api\.conf/);
  assert.doesNotMatch(api.block, /headers-front\.conf/);
});

test("API exposes only the actuator health endpoints", () => {
  const api = tlsServers.find(({ block }) => /server_name\s+api\.inventariumifpe\.me\s*;/.test(block));

  assert.match(api.block, /location\s+~\s+\^\/actuator\/health/);
  assert.match(api.block, /location\s+\/actuator\/\s*\{\s*return\s+404;/);
  assert.match(api.block, /location\s+\/auth\/\s*\{[^}]*limit_req\s+zone=auth/);
});

test("proxy sets X-Forwarded-For from the peer address instead of appending the client's", () => {
  const text = stripComments(read("snippets", "proxy.conf"));

  assert.match(text, /proxy_set_header X-Forwarded-For \$remote_addr;/);
  assert.doesNotMatch(text, /proxy_add_x_forwarded_for/);
  assert.match(text, /proxy_set_header X-Forwarded-Proto \$scheme;/);
});

test("Portainer and Grafana are restricted to admin IPs; only the stack webhook is public (POST only)", () => {
  const portainer = tlsServers.find(({ block }) => /portainer\.inventariumifpe\.me/.test(block));
  const grafana = tlsServers.find(({ block }) => /grafana\.inventariumifpe\.me/.test(block));
  const sonar = tlsServers.find(({ block }) => /sonar\.inventariumifpe\.me/.test(block));

  assert.match(portainer.block, /generated\/admin-allowlist\.conf/);
  assert.match(grafana.block, /generated\/admin-allowlist\.conf/);
  assert.doesNotMatch(sonar.block, /admin-allowlist/, "Sonar e publico (login do Sonar protege)");

  const webhook = portainer.block.match(/location\s+~\s+"\^\/api\/stacks\/webhooks\/[^"]+"\s*\{([\s\S]*?)\n    \}/);
  assert.ok(webhook, "location do webhook ausente");
  assert.match(webhook[1], /allow all;/);
  assert.match(webhook[1], /limit_except POST\s*\{\s*deny all;/);
  assert.match(webhook[1], /limit_req zone=webhook/);
});

test("port 80 only serves ACME challenges and HTTPS redirects; unknown hosts are dropped", () => {
  const text = stripComments(read("conf.d", "10-http.conf"));
  const blocks = serverBlocks(text);

  assert.equal(blocks.length, 2);
  assert.ok(blocks.some((b) => /default_server/.test(b) && /return\s+444/.test(b)));
  const named = blocks.find((b) => /acme-challenge/.test(b));
  assert.ok(named);
  assert.match(named, /return\s+301\s+https:\/\/\$host\$request_uri;/);
  for (const host of ["inventariumifpe.me", "www.", "api.", "portainer.", "sonar.", "grafana."]) {
    assert.ok(named.includes(host), `10-http.conf sem ${host}`);
  }
});

test("TLS: only TLS 1.2 and 1.3, no session tickets, no useless OCSP stapling", () => {
  const text = stripComments(read("snippets", "tls.conf"));

  assert.match(text, /ssl_protocols TLSv1\.2 TLSv1\.3;/);
  assert.doesNotMatch(text, /ssl_protocols[^;]*\bTLSv1\s/);
  assert.doesNotMatch(text, /ssl_protocols[^;]*TLSv1\.1/);
  assert.match(text, /ssl_session_tickets off;/);
  // A Let's Encrypt encerrou o OCSP: stapling nao tem efeito e so geraria avisos no log.
  assert.doesNotMatch(text, /ssl_stapling\s+on/);
  assert.match(text, /ssl_certificate\s+\/etc\/letsencrypt\/live\/inventariumifpe\.me\/fullchain\.pem;/);
  assert.match(text, /ssl_certificate_key\s+\/etc\/letsencrypt\/live\/inventariumifpe\.me\/privkey\.pem;/);
  assert.doesNotMatch(text, /\b(RC4|3DES|MD5|NULL|aNULL|EXPORT)\b/);
});

test("hardening: no server tokens and access logs without credentials or query strings", () => {
  const text = stripComments(read("conf.d", "00-hardening.conf"));
  const logFormat = text.match(/log_format\s+redacted\s+([\s\S]*?);/);

  assert.match(text, /server_tokens off;/);
  assert.ok(logFormat, "log_format redacted ausente");
  assert.doesNotMatch(logFormat[1], /authorization|cookie|\$args|\$query_string|\$request\b|\$request_uri/i);
  assert.match(text, /access_log [^;]*redacted;/);
});

test("generated files are examples only: real allowlist and upstreams are never versioned", () => {
  const examples = fs.readdirSync(path.join(root, "generated.example"));

  assert.deepEqual(examples.sort(), ["admin-allowlist.conf", "upstreams.conf"]);
  assert.ok(!fs.existsSync(path.join(root, "generated")), "nao versione infra/nginx/app-vm/generated");
});
