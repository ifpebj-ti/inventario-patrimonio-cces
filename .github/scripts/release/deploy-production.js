// Aciona o webhook da stack no Portainer depois de uma release e confere se a aplicação respondeu.
// A URL do webhook é um segredo: nunca é impressa nem entra em mensagens de erro.

const DEFAULT_ATTEMPTS = 30;
const DEFAULT_INTERVAL_MS = 10_000;
const REQUEST_TIMEOUT_MS = 15_000;

async function triggerWebhook(webhookUrl, fetchImpl = fetch) {
  let response;
  try {
    response = await fetchImpl(webhookUrl, {
      method: "POST",
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
  } catch (error) {
    throw new Error(`Falha ao chamar o webhook do Portainer: ${error.name}`);
  }

  if (!response.ok) {
    throw new Error(`O webhook do Portainer respondeu HTTP ${response.status}.`);
  }
}

async function waitForHealthy(url, options = {}, fetchImpl = fetch) {
  const attempts = options.attempts ?? DEFAULT_ATTEMPTS;
  const intervalMs = options.intervalMs ?? DEFAULT_INTERVAL_MS;
  const sleep = options.sleep ?? ((ms) => new Promise((resolve) => setTimeout(resolve, ms)));

  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      const response = await fetchImpl(url, {
        signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
      });
      if (response.ok) {
        console.log(`${url} respondeu HTTP ${response.status} (tentativa ${attempt}).`);
        return;
      }
      console.log(`${url} respondeu HTTP ${response.status} (tentativa ${attempt}/${attempts}).`);
    } catch (error) {
      console.log(`${url} indisponível (tentativa ${attempt}/${attempts}): ${error.name}`);
    }

    if (attempt < attempts) {
      await sleep(intervalMs);
    }
  }

  throw new Error(`${url} não ficou saudável após ${attempts} tentativas.`);
}

async function deploy(env, fetchImpl = fetch, options = {}) {
  const webhookUrl = (env.PORTAINER_STACK_WEBHOOK_URL || "").trim();
  if (!webhookUrl) {
    console.log("PORTAINER_STACK_WEBHOOK_URL não configurado: deploy automático ignorado.");
    return { deployed: false };
  }

  const healthUrls = [requireValue(env, "API_HEALTH_URL"), requireValue(env, "FRONTEND_URL")];

  await triggerWebhook(webhookUrl, fetchImpl);
  console.log(`Webhook acionado (release ${env.TAG || "desconhecida"}).`);

  for (const url of healthUrls) {
    await waitForHealthy(url, options, fetchImpl);
  }

  return { deployed: true };
}

function requireValue(env, name) {
  const value = env[name];
  if (!value) {
    throw new Error(`${name} é obrigatório.`);
  }
  return value;
}

if (require.main === module) {
  deploy(process.env).catch((error) => {
    console.error(error.message);
    process.exit(1);
  });
}

module.exports = { triggerWebhook, waitForHealthy, deploy };
