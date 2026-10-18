const test = require("node:test");
const assert = require("node:assert/strict");
const { spawnSync } = require("node:child_process");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

const composeFile = path.resolve(
  __dirname,
  "../../../docker-compose.production.yml",
);

// Valores fictícios: existem só para o `docker compose config` resolver a stack.
const baseEnv = {
  POSTGRES_USER: "user",
  POSTGRES_PASSWORD: "password-for-test",
  POSTGRES_DB: "inventory_management",
  SPRING_MAIL_USERNAME: "mail@example.com",
  SPRING_MAIL_PASSWORD: "mail-password-for-test",
  SECURITY_TOKEN_SECRET: "token-secret-for-test",
  GOOGLE_OAUTH_CLIENT_ID: "client-id.apps.googleusercontent.com",
};

const compose = spawnSync("docker", ["compose", "version"], {
  encoding: "utf8",
});
const skip = compose.status === 0 ? false : "docker compose indisponível";

// O Docker precisa do ambiente do usuário para achar o plugin do Compose, mas variáveis do host com
// o mesmo nome das da stack mascarariam um valor ausente no teste.
const stackVariables = [
  ...Object.keys(baseEnv),
  "IMAGE_TAG",
  "SYSTEM_ADMIN_USERNAME",
  "SYSTEM_ADMIN_PASSWORD",
  "FORWARD_HEADERS_STRATEGY",
  "CORS_ALLOWED_ORIGINS",
  "BACKEND_PORT",
  "FRONTEND_PORT",
  "SPRING_JPA_HIBERNATE_DDL_AUTO",
  "GOOGLE_ALLOWED_DOMAINS",
  "AUTH_LOGIN_USER_MAX_FAILURES",
  "AUTH_LOGIN_WINDOW",
  "AUTH_LOGIN_BLOCK_DURATION",
];

function cleanProcessEnv() {
  const env = { ...process.env };
  for (const name of stackVariables) {
    delete env[name];
  }
  return env;
}

function renderConfig(env) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "inventarium-compose-"));
  const envFile = path.join(dir, "test.env");
  fs.writeFileSync(
    envFile,
    Object.entries(env)
      .map(([key, value]) => `${key}=${value}`)
      .join("\n"),
  );

  try {
    return spawnSync(
      "docker",
      [
        "compose",
        "--env-file",
        envFile,
        "-f",
        composeFile,
        "config",
        "--format",
        "json",
      ],
      { encoding: "utf8", env: cleanProcessEnv() },
    );
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
}

function resolved(env = baseEnv) {
  const result = renderConfig(env);
  assert.equal(result.status, 0, result.stderr);
  return JSON.parse(result.stdout);
}

test("fixes project and volume names", { skip }, () => {
  const config = resolved();

  assert.equal(config.name, "inventarium");
  assert.equal(
    config.volumes["postgres-data"].name,
    "inventarium_postgres-data",
  );
});

test("publishes application ports only on loopback", { skip }, () => {
  const { services } = resolved();

  assert.equal(services.postgres.ports, undefined);
  for (const name of ["inventarium-back", "inventarium-front"]) {
    assert.ok(services[name].ports.length > 0);
    for (const port of services[name].ports) {
      assert.equal(port.host_ip, "127.0.0.1", `${name} deve usar loopback`);
    }
  }
});

test("pulls release images from GHCR by IMAGE_TAG", { skip }, () => {
  const latest = resolved().services;
  assert.equal(
    latest["inventarium-back"].image,
    "ghcr.io/ifpebj-ti/inventarium-back:latest",
  );
  assert.equal(
    latest["inventarium-front"].image,
    "ghcr.io/ifpebj-ti/inventarium-front:latest",
  );

  const pinned = resolved({ ...baseEnv, IMAGE_TAG: "v1.2.3" }).services;
  assert.equal(
    pinned["inventarium-back"].image,
    "ghcr.io/ifpebj-ti/inventarium-back:v1.2.3",
  );
  assert.equal(
    pinned["inventarium-front"].image,
    "ghcr.io/ifpebj-ti/inventarium-front:v1.2.3",
  );
});

test("passes admin, proxy and login limiter settings to the backend", { skip }, () => {
  const environment = resolved().services["inventarium-back"].environment;

  assert.equal(environment.SYSTEM_ADMIN_USERNAME, "admin");
  assert.equal(environment.SYSTEM_ADMIN_PASSWORD, "");
  assert.equal(environment.FORWARD_HEADERS_STRATEGY, "native");
  assert.equal(environment.CORS_ALLOWED_ORIGINS, "https://inventariumifpe.me");
  assert.equal(environment.AUTH_LOGIN_USER_MAX_FAILURES, "5");
  assert.equal(environment.AUTH_LOGIN_WINDOW, "15m");
  assert.equal(environment.AUTH_LOGIN_BLOCK_DURATION, "15m");
});

test("forwards overridden production settings", { skip }, () => {
  const environment = resolved({
    ...baseEnv,
    SYSTEM_ADMIN_PASSWORD: "a-long-enough-password",
    FORWARD_HEADERS_STRATEGY: "none",
    CORS_ALLOWED_ORIGINS: "https://example.test",
  }).services["inventarium-back"].environment;

  assert.equal(environment.SYSTEM_ADMIN_PASSWORD, "a-long-enough-password");
  assert.equal(environment.FORWARD_HEADERS_STRATEGY, "none");
  assert.equal(environment.CORS_ALLOWED_ORIGINS, "https://example.test");
});

test("keeps a healthcheck on the frontend", { skip }, () => {
  assert.ok(resolved().services["inventarium-front"].healthcheck);
});

for (const variable of Object.keys(baseEnv)) {
  test(`fails without required ${variable}`, { skip }, () => {
    const env = { ...baseEnv };
    delete env[variable];

    const result = renderConfig(env);

    assert.notEqual(result.status, 0);
    assert.match(result.stderr, new RegExp(variable));
  });
}
