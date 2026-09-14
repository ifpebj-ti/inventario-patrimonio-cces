const fs = require("fs");

async function main() {
  const token = requireEnv("GITHUB_TOKEN");
  const repository = requireEnv("GITHUB_REPOSITORY");
  const tag = requireEnv("TAG");
  const releaseNotesPath = requireEnv("RELEASE_NOTES_PATH");
  const body = fs.readFileSync(releaseNotesPath, "utf8");
  const existingRelease = await githubRequest(
    token,
    repository,
    `/releases/tags/${encodeURIComponent(tag)}`,
    { allowNotFound: true },
  );

  if (existingRelease) {
    console.log(`GitHub Release ${tag} already exists.`);
    return;
  }

  await githubRequest(token, repository, "/releases", {
    method: "POST",
    body: JSON.stringify({
      tag_name: tag,
      name: tag,
      body,
      draft: false,
      prerelease: false,
    }),
  });

  console.log(`GitHub Release ${tag} published.`);
}

async function githubRequest(token, repository, path, options = {}) {
  const response = await fetch(`https://api.github.com/repos/${repository}${path}`, {
    method: options.method || "GET",
    body: options.body,
    headers: {
      Accept: "application/vnd.github+json",
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
      "X-GitHub-Api-Version": "2022-11-28",
    },
  });

  if (response.status === 404 && options.allowNotFound) {
    return null;
  }

  if (!response.ok) {
    const responseBody = await response.text();
    throw new Error(`GitHub API ${response.status}: ${responseBody}`);
  }

  return response.status === 204 ? null : response.json();
}

function requireEnv(name) {
  const value = process.env[name];

  if (!value) {
    throw new Error(`Missing required environment variable: ${name}`);
  }

  return value;
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
