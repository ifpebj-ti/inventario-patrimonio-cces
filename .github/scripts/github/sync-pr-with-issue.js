const { getLinkedIssueNumber } = require("./pr-body");

const START_MARKER = "<!-- issue-summary:start -->";
const END_MARKER = "<!-- issue-summary:end -->";

async function main() {
  const token = requireEnv("GITHUB_TOKEN");
  const repository = requireEnv("GITHUB_REPOSITORY");
  const prNumber = requireEnv("PR_NUMBER");
  const prBody = process.env.PR_BODY || "";
  const issueNumber = getLinkedIssueNumber(prBody);

  if (!issueNumber) {
    console.log("No linked issue found in PR body.");
    return;
  }

  const issue = await githubRequest(
    token,
    `/repos/${repository}/issues/${issueNumber}`,
  );

  if (issue.pull_request) {
    throw new Error(`#${issueNumber} is a pull request, not an issue.`);
  }

  const issueSummary = [
    `### #${issue.number} - ${issue.title}`,
    "",
    issue.body?.trim() || "_Issue sem descricao._",
  ].join("\n");

  const nextBody = replaceBetweenMarkers(prBody, issueSummary);

  if (nextBody === prBody) {
    console.log("PR body is already synchronized with the linked issue.");
    return;
  }

  await githubRequest(token, `/repos/${repository}/pulls/${prNumber}`, {
    method: "PATCH",
    body: JSON.stringify({ body: nextBody }),
  });

  console.log(`PR #${prNumber} synchronized with issue #${issueNumber}.`);
}

function replaceBetweenMarkers(body, content) {
  const start = body.indexOf(START_MARKER);
  const end = body.indexOf(END_MARKER);

  if (start === -1 || end === -1 || end <= start) {
    return `${body.trim()}\n\n## Descricao da issue\n\n${START_MARKER}\n${content}\n${END_MARKER}\n`;
  }

  return [
    body.slice(0, start + START_MARKER.length),
    "\n",
    content.trim(),
    "\n",
    body.slice(end),
  ].join("");
}

async function githubRequest(token, path, options = {}) {
  const response = await fetch(`https://api.github.com${path}`, {
    ...options,
    headers: {
      Accept: "application/vnd.github+json",
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
      "X-GitHub-Api-Version": "2022-11-28",
      ...options.headers,
    },
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(`GitHub API ${response.status}: ${body}`);
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
