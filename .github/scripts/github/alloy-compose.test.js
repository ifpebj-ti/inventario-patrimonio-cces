const test = require("node:test");
const assert = require("node:assert/strict");
const { spawnSync } = require("node:child_process");
const fs = require("node:fs");
const path = require("node:path");

const repoRoot = path.resolve(__dirname, "../../..");
const alloyDir = path.join(repoRoot, "infra/alloy");
const composeFile = path.join(alloyDir, "docker-compose.alloy.yml");
const dashboardFile = path.join(
  repoRoot,
  "infra/observability/grafana/provisioning/dashboards/infraestrutura.json",
);
const datasourcesFile = path.join(
  repoRoot,
  "infra/observability/grafana/provisioning/datasources/datasources.yml",
);

const baseEnv = { OBS_HOST: "10.42.0.74", VM_NAME: "app" };

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
  return JSON.parse(result.stdout).services.alloy;
}

test("alloy image is pinned and the container has a memory limit", { skip }, () => {
  const alloy = resolved();
  const tag = alloy.image.split(":")[1];
  assert.ok(tag && tag !== "latest", `imagem sem versão fixa: ${alloy.image}`);
  assert.ok(Number(alloy.mem_limit) > 0, "alloy sem mem_limit");
});

test("alloy fails without the destination and the VM name", { skip }, () => {
  const result = render({});
  assert.notEqual(result.status, 0);
  assert.match(result.stderr, /OBS_HOST|VM_NAME/);
});

test("alloy reads the host but exposes no external port", { skip }, () => {
  const alloy = resolved();
  assert.equal(alloy.network_mode, "host");
  assert.notEqual(alloy.privileged, true);
  assert.equal(alloy.ports, undefined);
  assert.ok(
    alloy.command.includes("--server.http.listen-addr=127.0.0.1:12345"),
    "a interface do Alloy deve escutar só em loopback",
  );
});

test("host and docker mounts are read-only", { skip }, () => {
  const mounts = resolved().volumes.filter((volume) =>
    ["/", "/proc", "/sys", "/var/run/docker.sock", "/var/lib/docker"].includes(
      volume.source,
    ),
  );
  assert.equal(mounts.length, 5);
  for (const mount of mounts) {
    assert.equal(mount.read_only, true, `${mount.source} deve ser somente leitura`);
  }
});

test("config.alloy is valid for the Alloy version in the compose", { skip }, () => {
  const image = resolved().image;
  const result = spawnSync(
    "docker",
    [
      "run",
      "--rm",
      "-e",
      "OBS_HOST=10.42.0.74",
      "-e",
      "VM_NAME=app",
      "-v",
      `${alloyDir}:/cfg:ro`,
      image,
      "validate",
      "/cfg/config.alloy",
    ],
    { encoding: "utf8" },
  );
  assert.equal(result.status, 0, result.stdout + result.stderr);
});

test("config.alloy sends to the ports the observability stack publishes", () => {
  const config = fs.readFileSync(path.join(alloyDir, "config.alloy"), "utf8");
  assert.match(config, /":9090\/api\/v1\/write"/);
  assert.match(config, /":3100\/loki\/api\/v1\/push"/);
});

test("dashboard uses only provisioned datasources and filters by VM", () => {
  const dashboard = JSON.parse(fs.readFileSync(dashboardFile, "utf8"));
  const datasources = fs.readFileSync(datasourcesFile, "utf8");
  const provisioned = [...datasources.matchAll(/uid:\s*(\w+)/g)].map(
    (match) => match[1],
  );

  assert.ok(dashboard.panels.length > 0);
  for (const panel of dashboard.panels) {
    assert.ok(
      provisioned.includes(panel.datasource.uid),
      `${panel.title}: datasource ${panel.datasource.uid} não provisionado`,
    );
    for (const target of panel.targets) {
      assert.ok(
        target.expr.includes("$vm"),
        `${panel.title}: consulta sem filtro por VM`,
      );
    }
  }
});
