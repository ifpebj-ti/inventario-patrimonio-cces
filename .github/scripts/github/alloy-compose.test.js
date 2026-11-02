const test = require("node:test");
const assert = require("node:assert/strict");
const { spawnSync } = require("node:child_process");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

const repoRoot = path.resolve(__dirname, "../../..");
const alloyDir = path.join(repoRoot, "infra/alloy");
const composeFile = path.join(alloyDir, "docker-compose.alloy.yml");
const dashboardsDir = path.join(
  repoRoot,
  "infra/observability/grafana/provisioning/dashboards",
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

function validate(dir, env) {
  const args = ["run", "--rm"];
  for (const [key, value] of Object.entries(env)) {
    args.push("-e", `${key}=${value}`);
  }
  args.push("-v", `${dir}:/cfg:ro`, resolved().image, "validate", "/cfg");
  return spawnSync("docker", args, { encoding: "utf8" });
}

test("the Alloy configuration of the app VM (with backend metrics) is valid", { skip }, () => {
  const result = validate(alloyDir, {
    OBS_HOST: "10.42.0.74",
    VM_NAME: "app",
    BACKEND_METRICS_ADDR: "127.0.0.1:8081",
  });
  assert.equal(result.status, 0, result.stdout + result.stderr);
});

test("the Alloy configuration of the observability VM (empty backend.alloy) is valid", { skip }, () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "alloy-obs-"));
  try {
    fs.copyFileSync(path.join(alloyDir, "config.alloy"), path.join(dir, "config.alloy"));
    fs.writeFileSync(path.join(dir, "backend.alloy"), "");
    const result = validate(dir, { OBS_HOST: "127.0.0.1", VM_NAME: "observability" });
    assert.equal(result.status, 0, result.stdout + result.stderr);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test("backend metrics are scraped only from the loopback management port", () => {
  const backend = fs.readFileSync(path.join(alloyDir, "backend.alloy"), "utf8");
  assert.match(backend, /metrics_path\s*=\s*"\/actuator\/prometheus"/);
  assert.match(backend, /BACKEND_METRICS_ADDR/);
  assert.equal(resolved().environment.BACKEND_METRICS_ADDR, "127.0.0.1:8081");
});

test("only the app VM gets the backend scrape file", () => {
  const script = fs.readFileSync(
    path.join(repoRoot, "infra/scripts/alloy-vm/bootstrap-alloy.sh"),
    "utf8",
  );
  assert.match(script, /if \[ "\$vm_name" = "app" \]; then\s+install[^\n]*backend\.alloy/);
  assert.match(script, /else\s+: > "\$APP_DIR\/backend\.alloy"/);
});

test("config.alloy sends to the ports the observability stack publishes", () => {
  const config = fs.readFileSync(path.join(alloyDir, "config.alloy"), "utf8");
  assert.match(config, /":9090\/api\/v1\/write"/);
  assert.match(config, /":3100\/loki\/api\/v1\/push"/);
});

test("dashboards use only provisioned datasources and filter by VM", () => {
  const datasources = fs.readFileSync(datasourcesFile, "utf8");
  const provisioned = [...datasources.matchAll(/uid:\s*(\w+)/g)].map(
    (match) => match[1],
  );

  const files = fs.readdirSync(dashboardsDir).filter((name) => name.endsWith(".json"));
  assert.ok(files.length >= 2, "esperados os dashboards de infraestrutura e de backend");
  for (const file of files) {
    const dashboard = JSON.parse(fs.readFileSync(path.join(dashboardsDir, file), "utf8"));
    assert.ok(dashboard.panels.length > 0, file);
    for (const panel of dashboard.panels) {
      assert.ok(
        provisioned.includes(panel.datasource.uid),
        `${file} - ${panel.title}: datasource ${panel.datasource.uid} não provisionado`,
      );
      for (const target of panel.targets) {
        assert.ok(
          target.expr.includes("$vm"),
          `${file} - ${panel.title}: consulta sem filtro por VM`,
        );
      }
    }
  }
});

test("the Loki derived field extracts the traceId from a Spring Boot log line", () => {
  const datasources = fs.readFileSync(datasourcesFile, "utf8");
  const match = datasources.match(/matcherRegex:\s*'([^']+)'/);
  assert.ok(match, "matcherRegex não encontrado");

  const traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
  const line = `2026-10-25T23:33:26.403-03:00  INFO 1 --- [inventory] [nio-8080-exec-1] [${traceId}-00f067aa0ba902b7] c.i.Foo : ok`;
  assert.equal(new RegExp(match[1]).exec(line)[1], traceId);
});
