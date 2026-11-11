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
});

test("the public health goes to the management port and nothing else is proxied there", () => {
  const api = tlsServers.find(({ block }) => /server_name\s+api\.inventariumifpe\.me\s*;/.test(block));
  const health = api.block.match(/location\s+~\s+\^\/actuator\/health[^{]*\{([^}]*)\}/);

  assert.ok(health, "location do health não encontrada");
  assert.match(health[1], /proxy_pass\s+http:\/\/api_management_upstream;/);
  // A regex é exata: nenhum prefixo livre (como /actuator/ ou /actuator/prometheus) chega ao upstream.
  assert.doesNotMatch(api.block.replace(health[0], ""), /api_management_upstream/);
  assert.doesNotMatch(api.block, /prometheus/);
});

test("the management upstream prefers 8081 and keeps 8080 only as a transition backup", () => {
  const apiSite = listConf("sites-tls").find(({ text }) => /api_management_upstream/.test(text));
  const upstream = apiSite.text.match(/upstream\s+api_management_upstream\s*\{([^}]*)\}/);

  assert.ok(upstream, "upstream não encontrado");
  assert.match(upstream[1], /server\s+127\.0\.0\.1:8081\s*;/);
  assert.match(upstream[1], /server\s+127\.0\.0\.1:8080\s+backup\s*;/);
});

test("only /auth/login gets the strict per-IP limit; /auth/google and /auth/me stay on the general zone", () => {
  const api = tlsServers.find(({ block }) => /server_name\s+api\.inventariumifpe\.me\s*;/.test(block));

  assert.match(api.block, /location\s+=\s+\/auth\/login\s*\{[^}]*limit_req\s+zone=auth/);
  assert.doesNotMatch(api.block, /location\s+\/auth\//, "nao deve haver location /auth/ generica");
  assert.equal((api.block.match(/zone=auth\b/g) ?? []).length, 1, "a zona auth so vale no login");
  assert.match(api.block, /location\s+\/\s*\{[^}]*limit_req\s+zone=api/);
});

test("the nginx 429 on login carries Retry-After and CORS, and keeps the security headers", () => {
  const api = tlsServers.find(({ block }) => /server_name\s+api\.inventariumifpe\.me\s*;/.test(block));
  const named = api.block.match(/location\s+@login_rate_limited\s*\{([^}]*)\}/);
  const snippet = stripComments(read("snippets", "login-rate-limited.conf"));

  assert.match(api.block, /location\s+=\s+\/auth\/login\s*\{[^}]*error_page\s+429\s*=\s*@login_rate_limited/);
  assert.ok(named, "location @login_rate_limited ausente");
  for (const include of ["headers-common.conf", "headers-api.conf", "login-rate-limited.conf"]) {
    assert.match(named[1], new RegExp(`snippets/${include.replace(".", "\\.")}`), `sem ${include}`);
  }
  assert.match(snippet, /add_header Retry-After "\d+" always;/);
  assert.match(snippet, /add_header Access-Control-Allow-Origin "https:\/\/inventariumifpe\.me" always;/);
  assert.match(snippet, /add_header Access-Control-Expose-Headers "Retry-After" always;/);
  assert.match(snippet, /return 429 "Too many attempts";/);
});

test("proxy sets X-Forwarded-For from the peer address instead of appending the client's", () => {
  const text = stripComments(read("snippets", "proxy.conf"));

  assert.match(text, /proxy_set_header X-Forwarded-For \$remote_addr;/);
  assert.doesNotMatch(text, /proxy_add_x_forwarded_for/);
  assert.match(text, /proxy_set_header X-Forwarded-Proto \$forwarded_proto;/);
});

test("the forwarded scheme comes from the listening port, never from a client header", () => {
  const hardening = stripComments(read("conf.d", "00-hardening.conf"));
  const proxy = stripComments(read("snippets", "proxy.conf"));
  const map = hardening.match(/map\s+\$server_port\s+\$forwarded_proto\s*\{([^}]*)\}/);

  assert.ok(map, "map $server_port -> $forwarded_proto ausente");
  assert.match(map[1], /9080\s+https;/);
  assert.match(map[1], /default\s+\$scheme;/);
  assert.doesNotMatch(proxy, /\$http_x_forwarded/i, "nao confie em X-Forwarded-* vindo do cliente");
});

test("the client IP behind the LB comes from generated real_ip config, trusted only from the LB subnet", () => {
  const hardening = stripComments(read("conf.d", "00-hardening.conf"));
  const example = stripComments(read("generated.example", "real-ip.conf"));
  const allSites = listConf("sites-tls").map(({ text }) => stripComments(text)).join("\n");

  assert.match(hardening, /include \/etc\/nginx\/inventarium\/generated\/real-ip\.conf;/);
  assert.match(example, /set_real_ip_from \d+\.\d+\.\d+\.\d+\/\d+;/);
  assert.match(example, /real_ip_header X-Forwarded-For;/);
  assert.match(example, /real_ip_recursive on;/);
  for (const [name, text] of [["00-hardening.conf", hardening], ["sites-tls", allSites]]) {
    assert.doesNotMatch(text, /set_real_ip_from/, `${name}: o CIDR do LB e gerado, nao versionado`);
    assert.doesNotMatch(text, /real_ip_header/, `${name}: real_ip so no arquivo gerado`);
  }
});

test("every named vhost also listens on 9080 for the LB; unknown hosts there are dropped", () => {
  const named = tlsServers.filter(({ block }) => /listen\s+443/.test(block) && !catchAll(block));
  const internal = tlsServers.find(({ name }) => name === "15-lb-interno.conf");

  assert.ok(named.length >= 6, "esperado ao menos 6 vhosts nomeados");
  for (const { name, block } of named) {
    assert.match(block, /listen\s+9080\s*;/, `${name}: sem listen 9080`);
    assert.doesNotMatch(block, /listen\s+9080[^;]*\bssl\b/, `${name}: a 9080 e HTTP simples`);
  }
  assert.ok(internal, "15-lb-interno.conf ausente");
  const blocks = serverBlocks(stripComments(read("sites-tls", "15-lb-interno.conf")));
  const dropAll = blocks.find((b) => /listen\s+9080\s+default_server\s*;/.test(b));
  assert.ok(dropAll && /return\s+444\s*;/.test(dropAll), "default_server da 9080 deve devolver 444");
});

test("the LB health port answers only /healthz, only to the LB subnet, without logging", () => {
  const blocks = serverBlocks(stripComments(read("sites-tls", "15-lb-interno.conf")));
  const health = blocks.find((b) => /listen\s+9081\s*;/.test(b));

  assert.ok(health, "server da 9081 ausente");
  assert.match(health, /include \/etc\/nginx\/inventarium\/generated\/lb-allowlist\.conf;/);
  assert.match(health, /location\s+=\s+\/healthz\s*\{[^}]*return\s+200/);
  assert.match(health, /location\s+\/\s*\{\s*return\s+404;/);
  assert.match(health, /access_log off;/);
  assert.doesNotMatch(health, /proxy_pass/, "o health do LB nao depende de upstream");
  assert.doesNotMatch(health, /limit_req/, "o LB consulta a cada poucos segundos: sem rate limit");

  const example = stripComments(read("generated.example", "lb-allowlist.conf"));
  assert.match(example, /allow \d+\.\d+\.\d+\.\d+\/\d+;/);
  assert.match(example, /deny all;/);
});

test("nginx keeps connections longer than the LB idle timeout", () => {
  const text = stripComments(read("nginx.conf"));
  const seconds = Number(text.match(/keepalive_timeout\s+(\d+)s;/)?.[1]);

  assert.ok(seconds >= 330, `keepalive_timeout de ${seconds}s deve superar o idle timeout do LB (330s)`);
});

test("public Grafana rate limits the login per IP and the rest of the UI with a looser zone", () => {
  const grafana = tlsServers.find(({ block }) => /grafana\.inventariumifpe\.me/.test(block));
  const hardening = stripComments(read("conf.d", "00-hardening.conf"));

  assert.match(hardening, /limit_req_zone \$binary_remote_addr zone=grafana_login:\w+ rate=\d+r\/m;/);
  assert.match(hardening, /limit_req_zone \$binary_remote_addr zone=grafana:\w+ rate=\d+r\/s;/);
  assert.match(grafana.block, /location\s+=\s+\/login\s*\{[^}]*limit_req\s+zone=grafana_login/);
  assert.match(grafana.block, /location\s+\/\s*\{[^}]*limit_req\s+zone=grafana\b/);
  assert.equal((grafana.block.match(/zone=grafana_login\b/g) ?? []).length, 1, "a zona do login so vale no /login");
});

test("Portainer is restricted to admin IPs; Grafana and Sonar are public and protected by their own login", () => {
  const portainer = tlsServers.find(({ block }) => /portainer\.inventariumifpe\.me/.test(block));
  const grafana = tlsServers.find(({ block }) => /grafana\.inventariumifpe\.me/.test(block));
  const sonar = tlsServers.find(({ block }) => /sonar\.inventariumifpe\.me/.test(block));

  assert.match(portainer.block, /generated\/admin-allowlist\.conf/);
  assert.doesNotMatch(grafana.block, /admin-allowlist/, "Grafana e publico (login do Grafana protege)");
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

  assert.deepEqual(examples.sort(), [
    "admin-allowlist.conf",
    "lb-allowlist.conf",
    "real-ip.conf",
    "upstreams.conf",
  ]);
  assert.ok(!fs.existsSync(path.join(root, "generated")), "nao versione infra/nginx/app-vm/generated");
});
