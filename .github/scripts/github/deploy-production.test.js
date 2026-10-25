const test = require("node:test");
const assert = require("node:assert/strict");

const {
  triggerWebhook,
  waitForHealthy,
  deploy,
} = require("../release/deploy-production.js");

const webhook = "https://portainer.example.test/api/stacks/webhooks/00000000-0000-0000-0000-000000000000";
const noSleep = { attempts: 3, intervalMs: 0, sleep: async () => {} };

function response(status) {
  return { ok: status >= 200 && status < 300, status };
}

test("triggerWebhook faz POST e falha em resposta não-2xx sem vazar a URL", async () => {
  const calls = [];
  await triggerWebhook(webhook, async (url, init) => {
    calls.push({ url: String(url), method: init.method });
    return response(204);
  });
  assert.equal(calls[0].method, "POST");
  assert.equal(calls[0].url, webhook, "o webhook é chamado sem parâmetros: a stack usa IMAGE_TAG=latest");

  await assert.rejects(
    triggerWebhook(webhook, async () => response(404)),
    (error) => error.message.includes("404") && !error.message.includes("0000-0000"),
  );
});

test("triggerWebhook não repassa a URL quando a rede falha", async () => {
  await assert.rejects(
    triggerWebhook(webhook, async () => {
      throw new TypeError(`fetch failed ${webhook}`);
    }),
    (error) => !error.message.includes("0000-0000"),
  );
});

test("waitForHealthy tenta de novo até responder 2xx", async () => {
  const statuses = [503, 502, 200];
  let calls = 0;
  await waitForHealthy("https://api.example.test/health", noSleep, async () => response(statuses[calls++]));
  assert.equal(calls, 3);
});

test("waitForHealthy falha ao esgotar as tentativas", async () => {
  await assert.rejects(
    waitForHealthy("https://api.example.test/health", noSleep, async () => response(503)),
    /não ficou saudável/,
  );
});

test("deploy termina sem falhar quando o secret do webhook está ausente", async () => {
  let called = false;
  const result = await deploy({ PORTAINER_STACK_WEBHOOK_URL: "  " }, async () => {
    called = true;
    return response(200);
  });
  assert.equal(result.deployed, false);
  assert.equal(called, false);
});

test("deploy aciona o webhook e depois confere API e frontend", async () => {
  const urls = [];
  const result = await deploy(
    {
      PORTAINER_STACK_WEBHOOK_URL: webhook,
      TAG: "v7.2.0",
      API_HEALTH_URL: "https://api.example.test/actuator/health",
      FRONTEND_URL: "https://app.example.test/",
    },
    async (url) => {
      urls.push(String(url));
      return response(200);
    },
    noSleep,
  );
  assert.equal(result.deployed, true);
  assert.equal(urls[0], webhook);
  assert.deepEqual(urls.slice(1), [
    "https://api.example.test/actuator/health",
    "https://app.example.test/",
  ]);
});
