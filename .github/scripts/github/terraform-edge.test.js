const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const repoRoot = path.resolve(__dirname, "../../..");
const tfDir = path.join(repoRoot, "infra/terraform/oci");

function tf(name) {
  return fs.readFileSync(path.join(tfDir, name), "utf8").replace(/\r\n/g, "\n");
}

// Remove comentarios para os asserts nao casarem com texto explicativo.
function code(name) {
  return tf(name)
    .split("\n")
    .filter((line) => !line.trim().startsWith("#"))
    .join("\n");
}

// Bloco `kind "type" "name" { ... }` contando chaves.
function block(text, header) {
  const start = text.indexOf(header);
  assert.ok(start >= 0, `bloco nao encontrado: ${header}`);
  let depth = 0;
  let i = text.indexOf("{", start);
  const open = i;
  for (; i < text.length; i += 1) {
    if (text[i] === "{") depth += 1;
    if (text[i] === "}") {
      depth -= 1;
      if (depth === 0) break;
    }
  }
  return text.slice(open, i + 1);
}

const allTf = fs
  .readdirSync(tfDir)
  .filter((name) => name.endsWith(".tf"))
  .map((name) => ({ name, text: code(name) }));

test("the Load Balancer is a public flexible layer-7 LB in its own subnet with a reserved IP", () => {
  const lb = code("load_balancer.tf");
  const main = block(lb, 'resource "oci_load_balancer_load_balancer" "main"');

  assert.match(main, /shape\s*=\s*"flexible"/);
  assert.match(main, /is_private\s*=\s*false/);
  assert.match(main, /subnet_ids\s*=\s*\[oci_core_subnet\.lb_public\.id\]/);
  assert.match(main, /network_security_group_ids\s*=\s*\[oci_core_network_security_group\.lb\.id\]/);
  assert.match(main, /reserved_ips\s*\{\s*id\s*=\s*oci_core_public_ip\.lb\.id/);
  const publicIp = block(lb, 'resource "oci_core_public_ip" "lb"');
  assert.match(publicIp, /lifetime\s*=\s*"RESERVED"/);
  // O LB e quem associa o IP (reserved_ips). Sem isto o plano seguinte ao apply propoe private_ip_id -> null
  // e a OCI recusa desassociar um IP gerenciado pelo LB.
  assert.match(publicIp, /ignore_changes\s*=\s*\[private_ip_id\]/);
  assert.doesNotMatch(publicIp, /\n\s*private_ip_id\s*=/, "o IP nao deve ser associado por aqui: o LB faz isso");

  const subnet = block(code("network.tf"), 'resource "oci_core_subnet" "lb_public"');
  assert.match(subnet, /route_table_id\s*=\s*oci_core_route_table\.public\.id/);
  assert.match(subnet, /cidr_block\s*=\s*var\.lb_public_subnet_cidr/);
});

test("the LB talks to the VM on the Nginx ports 80, 9080 and 9081, which Nginx really serves", () => {
  const lb = code("load_balancer.tf");
  const ports = block(lb, "locals");
  const nginxInternal = fs.readFileSync(
    path.join(repoRoot, "infra/nginx/app-vm/sites-tls/15-lb-interno.conf"),
    "utf8",
  );

  assert.match(ports, /http\s*=\s*80\b/);
  assert.match(ports, /app\s*=\s*9080\b/);
  assert.match(ports, /health\s*=\s*9081\b/);
  assert.match(nginxInternal, /listen\s+9080\s+default_server/);
  assert.match(nginxInternal, /listen\s+9081\s*;/);

  for (const set of ["http", "app"]) {
    const backendSet = block(lb, `resource "oci_load_balancer_backend_set" "${set}"`);
    assert.match(backendSet, /port\s*=\s*local\.lb_backend_ports\.health/, `${set}: health na 9081`);
    assert.match(backendSet, /url_path\s*=\s*"\/healthz"/);
    assert.match(backendSet, /return_code\s*=\s*200/);
  }
  assert.match(block(lb, 'resource "oci_load_balancer_backend" "http"'), /port\s*=\s*local\.lb_backend_ports\.http/);
  assert.match(block(lb, 'resource "oci_load_balancer_backend" "app"'), /port\s*=\s*local\.lb_backend_ports\.app/);
});

test("the HTTPS listener is gated, terminates TLS 1.2/1.3 and never reverts the rotated certificate", () => {
  const listener = block(code("load_balancer.tf"), 'resource "oci_load_balancer_listener" "https"');

  assert.match(listener, /count\s*=\s*var\.lb_https_enabled \? 1 : 0/);
  assert.match(listener, /port\s*=\s*443/);
  assert.match(listener, /protocols\s*=\s*\["TLSv1\.2",\s*"TLSv1\.3"\]/);
  assert.match(listener, /idle_timeout_in_seconds\s*=\s*var\.lb_idle_timeout_seconds/);
  assert.match(listener, /ignore_changes\s*=\s*\[ssl_configuration\[0\]\.certificate_name\]/);
  assert.match(listener, /precondition\s*\{[^}]*lb_bootstrap_certificate_name != ""/);
  assert.match(code("load_balancer.tf"), /oci_load_balancer_listener" "http"[\s\S]*?port\s*=\s*80/);
});

test("no Terraform file ever handles the certificate or its private key (it would land in the state)", () => {
  for (const { name, text } of allTf) {
    assert.doesNotMatch(text, /oci_load_balancer_certificate/, `${name}: certificado fora do Terraform`);
    assert.doesNotMatch(text, /tls_private_key|tls_self_signed_cert|tls_cert_request/, `${name}: sem recurso tls`);
    assert.doesNotMatch(text, /private_key\s*=|public_certificate\s*=|ca_certificate\s*=/, `${name}: sem PEM`);
    assert.doesNotMatch(text, /letsencrypt\/live|privkey\.pem/, `${name}: sem caminho do certificado`);
  }
});

test("the VM accepts the LB ports only from the LB NSG; the web ports from CIDRs stay 80 and 443", () => {
  const security = code("security.tf");
  const fromLb = block(security, 'resource "oci_core_network_security_group_security_rule" "app_from_lb"');

  assert.match(fromLb, /for_each\s*=\s*local\.lb_backend_ports/);
  assert.match(fromLb, /network_security_group_id\s*=\s*oci_core_network_security_group\.app\.id/);
  assert.match(fromLb, /source\s*=\s*oci_core_network_security_group\.lb\.id/);
  assert.match(fromLb, /source_type\s*=\s*"NETWORK_SECURITY_GROUP"/);

  const toApp = block(security, 'resource "oci_core_network_security_group_security_rule" "lb_to_app_egress"');
  assert.match(toApp, /destination\s*=\s*oci_core_network_security_group\.app\.id/);
  assert.match(toApp, /destination_type\s*=\s*"NETWORK_SECURITY_GROUP"/);

  const lbIngress = block(security, 'resource "oci_core_network_security_group_security_rule" "lb_web_ingress"');
  assert.match(lbIngress, /toset\(\["80",\s*"443"\]\)/);
  assert.match(lbIngress, /source\s*=\s*"0\.0\.0\.0\/0"/);

  // As portas internas nunca podem ser abertas para um CIDR: so web (80/443) vem de allowed_web_cidrs.
  const locals = code("locals.tf");
  assert.match(locals, /for port in \[80, 443\]/);
  assert.doesNotMatch(locals, /9080|9081/);
  assert.match(
    block(security, 'resource "oci_core_network_security_group_security_rule" "app_web_ingress"'),
    /local\.app_web_ingress_rules/,
  );
});

test("direct web access to the VM is optional: allowed_web_cidrs defaults to empty", () => {
  const variable = block(code("variables.tf"), 'variable "allowed_web_cidrs"');

  assert.match(variable, /default\s*=\s*\[\]/);
});

test("the LB idle timeout stays below the Nginx keepalive so the LB never reuses a closed connection", () => {
  const variable = block(code("variables.tf"), 'variable "lb_idle_timeout_seconds"');
  const idle = Number(variable.match(/default\s*=\s*(\d+)/)[1]);
  const keepalive = Number(
    fs
      .readFileSync(path.join(repoRoot, "infra/nginx/app-vm/nginx.conf"), "utf8")
      .match(/^\s*keepalive_timeout\s+(\d+)s;/m)[1],
  );

  assert.ok(idle >= 300, "o idle precisa cobrir o webhook do Portainer (ate 300 s)");
  assert.ok(idle < keepalive, `idle ${idle}s deve ser menor que o keepalive ${keepalive}s do Nginx`);
  assert.match(variable, /var\.lb_idle_timeout_seconds < 350/);
});

test("the WAF is attached to the LB and starts in detection (CHECK), switching to block only by variable", () => {
  const waf = code("waf.tf");
  const mode = block(code("variables.tf"), 'variable "waf_mode"');

  assert.match(mode, /default\s*=\s*"DETECTION"/);
  assert.match(mode, /contains\(\["DETECTION",\s*"BLOCK"\],\s*var\.waf_mode\)/);
  assert.match(waf, /waf_protect_action\s*=\s*var\.waf_mode == "BLOCK" \? "block" : "check"/);
  assert.match(waf, /waf_rate_action\s*=\s*var\.waf_mode == "BLOCK" \? "rate_limit" : "check"/);

  const firewall = block(waf, 'resource "oci_waf_web_app_firewall" "main"');
  assert.match(firewall, /backend_type\s*=\s*"LOAD_BALANCER"/);
  assert.match(firewall, /load_balancer_id\s*=\s*oci_load_balancer_load_balancer\.main\.id/);
  assert.match(firewall, /web_app_firewall_policy_id\s*=\s*oci_waf_web_app_firewall_policy\.main\.id/);
});

test("the WAF actions: CHECK only logs; the block and rate-limit responses carry the CORS origin", () => {
  const policy = block(code("waf.tf"), 'resource "oci_waf_web_app_firewall_policy" "main"');

  assert.match(policy, /name\s*=\s*"check"\s*\n\s*type\s*=\s*"CHECK"/);
  assert.match(policy, /name\s*=\s*"allow"\s*\n\s*type\s*=\s*"ALLOW"/);
  for (const [action, status] of [["block", 403], ["rate_limit", 429]]) {
    const re = new RegExp(`actions\\s*\\{\\s*name\\s*=\\s*"${action}"[\\s\\S]*?\\n  \\}`);
    const body = policy.match(re)?.[0];
    assert.ok(body, `acao ${action} ausente`);
    assert.match(body, /type\s*=\s*"RETURN_HTTP_RESPONSE"/);
    assert.match(body, new RegExp(`code\\s*=\\s*${status}`));
    assert.match(body, /Access-Control-Allow-Origin[\s\S]*?var\.waf_cors_origin/, `${action}: sem CORS`);
  }
  assert.match(policy, /Retry-After/);
});

test("WAF protection: collaborative SQLi and XSS groups are required, body inspection never blocks large uploads", () => {
  const waf = code("waf.tf");
  const prefixes = block(waf, "waf_collaborative_prefixes = ");

  assert.match(prefixes, /"sql-injection"\s*=\s*\["942"\]/);
  assert.match(prefixes, /"xss"\s*=\s*\["941"\]/);
  assert.match(prefixes, /"path-traversal-lfi"\s*=\s*\["930"\]/);
  assert.match(prefixes, /"rce"\s*=\s*\["932"\]/);
  assert.match(waf, /precondition\s*\{[\s\S]*?\["sql-injection", "xss"\][\s\S]*?\}/);

  // Limite da OCI (8192 por tenancy) e sem acao de excesso: o restante do corpo (CSV, Sonar) nao e barrado.
  assert.match(waf, /body_inspection_size_limit_in_bytes\s*=\s*8192/);
  assert.doesNotMatch(waf, /body_inspection_size_limit_exceeded_action_name/);

  // As regras individuais ruidosas (protocolo 920xxx e User-Agent generico) ficam de fora.
  const standalone = block(waf, "waf_standalone_keys = ");
  assert.doesNotMatch(standalone, /"920\d+"/, "protocolo 920xxx quebraria o smoke test e clientes simples");
  assert.doesNotMatch(standalone, /"91310[12]"/, "User-Agent generico pegaria o Node do CI");
  assert.match(standalone, /"911100"/);
  assert.match(standalone, /"944150"/);
});

test("WAF logs go to OCI Logging so false positives can be reviewed before blocking", () => {
  const log = block(code("waf.tf"), 'resource "oci_logging_log" "waf"');

  assert.match(log, /service\s*=\s*"waf"/);
  assert.match(log, /source_type\s*=\s*"OCISERVICE"/);
  assert.match(log, /resource\s*=\s*oci_waf_web_app_firewall\.main\.id/);
  assert.match(log, /retention_duration\s*=\s*var\.waf_log_retention_days/);
});

test("certificate rotation IAM is least privilege: 'use' on this LB only, opt-in, reusing the VM dynamic group", () => {
  const iam = code("edge_iam.tf");
  const policy = block(iam, 'resource "oci_identity_policy" "edge"');

  assert.match(policy, /count\s*=\s*var\.manage_edge_iam \? 1 : 0/);
  assert.match(policy, /to use load-balancers in compartment id/);
  assert.doesNotMatch(policy, /to manage /, "o hook nao precisa de manage");
  assert.match(policy, /where target\.loadbalancer\.id = '\$\{oci_load_balancer_load_balancer\.main\.id\}'/);
  assert.match(policy, /oci_identity_dynamic_group\.app\[0\]\.name/);
  assert.match(policy, /precondition\s*\{\s*condition\s*=\s*var\.manage_backup_iam/);
  assert.match(block(code("variables.tf"), 'variable "manage_edge_iam"'), /default\s*=\s*false/);
});

test("the observability ports map keeps the format that observability-compose.test.js parses", () => {
  assert.match(code("locals.tf"), /observability_stack_ports = \{[\s\S]*?\}/);
});
