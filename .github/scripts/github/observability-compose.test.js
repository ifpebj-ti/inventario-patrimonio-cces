const test = require("node:test");
const assert = require("node:assert/strict");
const { spawnSync } = require("node:child_process");
const fs = require("node:fs");
const path = require("node:path");

const repoRoot = path.resolve(__dirname, "../../..");
const composeFile = path.join(
  repoRoot,
  "infra/observability/docker-compose.observability.yml",
);
const localsFile = path.join(repoRoot, "infra/terraform/oci/locals.tf");

// Valores fictícios: existem só para o `docker compose config` resolver a stack.
const baseEnv = {
  GRAFANA_ADMIN_PASSWORD: "grafana-password-for-test",
  SONAR_DB_PASSWORD: "sonar-password-for-test",
};

const compose = spawnSync("docker", ["compose", "version"], {
  encoding: "utf8",
});
const skip = compose.status === 0 ? false : "docker compose indisponível";

function render(env) {
  const processEnv = { ...process.env };
  for (const name of Object.keys(baseEnv)) {
    delete processEnv[name];
  }
  return spawnSync(
    "docker",
    ["compose", "-f", composeFile, "config", "--format", "json"],
    { encoding: "utf8", env: { ...processEnv, ...env } },
  );
}

function resolved() {
  const result = render(baseEnv);
  assert.equal(result.status, 0, result.stderr);
  return JSON.parse(result.stdout);
}

function publishedPorts(services) {
  return Object.values(services)
    .flatMap((service) => service.ports ?? [])
    .map((port) => Number(port.published))
    .sort((a, b) => a - b);
}

test("every service has a memory limit", { skip }, () => {
  for (const [name, service] of Object.entries(resolved().services)) {
    assert.ok(Number(service.mem_limit) > 0, `${name} sem mem_limit`);
  }
});

test("the memory limits fit in the 8 GB VM with room for the OS", { skip }, () => {
  const total = Object.values(resolved().services).reduce(
    (sum, service) => sum + Number(service.mem_limit),
    0,
  );
  assert.ok(total <= 7 * 1024 ** 3, `limites somam ${total / 1024 ** 3} GB`);
});

test("images are pinned to an explicit version", { skip }, () => {
  for (const [name, service] of Object.entries(resolved().services)) {
    const tag = service.image.split(":")[1];
    assert.ok(tag, `${name} sem tag`);
    assert.notEqual(tag, "latest", `${name} usa latest`);
  }
});

test("fails without the generated secrets", { skip }, () => {
  const result = render({});
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /GRAFANA_ADMIN_PASSWORD|SONAR_DB_PASSWORD/);
});

test("prometheus receives remote write and limits retention", { skip }, () => {
  const command = resolved().services.prometheus.command;
  assert.ok(command.includes("--web.enable-remote-write-receiver"));
  assert.ok(command.some((arg) => arg.startsWith("--storage.tsdb.retention.time=")));
  assert.ok(command.some((arg) => arg.startsWith("--storage.tsdb.retention.size=")));
});

test("only the expected ports are published", { skip }, () => {
  assert.deepEqual(publishedPorts(resolved().services), [
    3000, 3100, 4317, 4318, 9000, 9090,
  ]);
});

test("persistent data lives in named volumes", { skip }, () => {
  const config = resolved();
  for (const volume of [
    "prometheus-data",
    "loki-data",
    "tempo-data",
    "grafana-data",
    "sonarqube-db-data",
    "sonarqube-data",
  ]) {
    assert.ok(config.volumes[volume], `volume ${volume} ausente`);
  }
  for (const [name, service] of Object.entries(config.services)) {
    assert.notEqual(service.privileged, true, `${name} é privileged`);
  }
});

test("the Terraform NSG opens exactly the ports the stack publishes", { skip }, () => {
  const locals = fs.readFileSync(localsFile, "utf8");
  const block = locals.match(/observability_stack_ports = \{([\s\S]*?)\}/);
  assert.ok(block, "observability_stack_ports não encontrado em locals.tf");
  const nsgPorts = [...block[1].matchAll(/=\s*(\d+)/g)]
    .map((match) => Number(match[1]))
    .sort((a, b) => a - b);

  assert.deepEqual(nsgPorts, publishedPorts(resolved().services));
});

test("service config files referenced by the compose exist", () => {
  const dir = path.dirname(composeFile);
  for (const file of [
    "prometheus/prometheus.yml",
    "loki/loki.yml",
    "tempo/tempo.yml",
    "grafana/provisioning/datasources/datasources.yml",
  ]) {
    assert.ok(fs.existsSync(path.join(dir, file)), `${file} ausente`);
  }
});
