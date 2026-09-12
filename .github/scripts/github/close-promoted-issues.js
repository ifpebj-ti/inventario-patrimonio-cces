const { getLinkedIssueNumber } = require("./pr-body");

async function main() {
  const token = requireEnv("GITHUB_TOKEN");
  const repository = requireEnv("GITHUB_REPOSITORY");
  const promotionPrNumber = Number(requireEnv("PR_NUMBER"));

  const result = await closePromotedIssues({
    request: (path, options) => githubRequest(token, repository, path, options),
    promotionPrNumber,
  });

  console.log(
    `Promotion PR #${promotionPrNumber}: ${result.pullRequests.length} PR(s), ${result.issueNumbers.length} issue(s), ${result.closedIssues.length} closed, ${result.skippedIssues.length} already closed/skipped.`,
  );
  logList(
    "Included pull requests",
    result.pullRequests.map((pullRequest) => `#${pullRequest.number}`),
  );
  logList(
    "Linked issues",
    result.issueNumbers.map((issueNumber) => `#${issueNumber}`),
  );
  logList(
    "Closed issues",
    result.closedIssues.map((issueNumber) => `#${issueNumber}`),
  );
  logList("Skipped", [...result.skippedIssues, ...result.skippedPullRequests]);

  if (result.errors.length > 0) {
    console.error("Errors:");
    for (const error of result.errors) {
      console.error(error);
    }
    throw new Error("Some promoted PRs are missing linked issues.");
  }
}

async function closePromotedIssues({ request, promotionPrNumber }) {
  const commits = await listPaginated(
    request,
    `/pulls/${promotionPrNumber}/commits`,
  );
  const pullRequestsByNumber = new Map();

  for (const commit of commits) {
    const pullRequests = await listPaginated(
      request,
      `/commits/${commit.sha}/pulls`,
    );

    for (const pullRequest of pullRequests) {
      if (
        isMergedIntoDevelopment(pullRequest) &&
        pullRequest.number !== promotionPrNumber
      ) {
        pullRequestsByNumber.set(pullRequest.number, pullRequest);
      }
    }
  }

  const pullRequests = [...pullRequestsByNumber.values()].sort(
    (left, right) => left.number - right.number,
  );
  const collected = collectLinkedIssues(pullRequests);
  const issueNumbers = dedupeIssueNumbers(collected.issueNumbers);
  const closedIssues = [];
  const skippedIssues = [];

  for (const issueNumber of issueNumbers) {
    const issue = await request(`/issues/${issueNumber}`);

    if (issue.pull_request) {
      skippedIssues.push(`#${issueNumber} is a pull request.`);
      continue;
    }

    if (issue.state === "closed") {
      skippedIssues.push(`#${issueNumber} is already closed.`);
      continue;
    }

    await request(`/issues/${issueNumber}`, {
      method: "PATCH",
      body: JSON.stringify({
        state: "closed",
        state_reason: "completed",
      }),
    });

    closedIssues.push(issueNumber);
  }

  return {
    pullRequests,
    issueNumbers,
    closedIssues,
    skippedIssues,
    skippedPullRequests: collected.skippedPullRequests,
    errors: collected.errors,
  };
}

function collectLinkedIssues(pullRequests) {
  const issueNumbers = [];
  const errors = [];
  const skippedPullRequests = [];

  for (const pullRequest of pullRequests) {
    const issueNumber = getLinkedIssueNumber(pullRequest.body || "");

    if (issueNumber) {
      issueNumbers.push(issueNumber);
      continue;
    }

    if (pullRequest.user?.login === "dependabot[bot]") {
      skippedPullRequests.push(
        `Dependabot PR #${pullRequest.number} skipped because it has no linked issue.`,
      );
      continue;
    }

    errors.push(
      `PR #${pullRequest.number} was promoted but does not reference an issue with Closes/Fixes/Resolves.`,
    );
  }

  return {
    issueNumbers,
    errors,
    skippedPullRequests,
  };
}

function isMergedIntoDevelopment(pullRequest) {
  return (
    pullRequest?.base?.ref === "development" &&
    Boolean(pullRequest.merged_at)
  );
}

function dedupeIssueNumbers(issueNumbers) {
  return [...new Set(issueNumbers.map(String))].sort(
    (left, right) => Number(left) - Number(right),
  );
}

function logList(title, items) {
  console.log(`${title}:`);

  if (items.length === 0) {
    console.log("- none");
    return;
  }

  for (const item of items) {
    console.log(`- ${item}`);
  }
}

async function listPaginated(request, path) {
  const items = [];
  let page = 1;

  while (true) {
    const separator = path.includes("?") ? "&" : "?";
    const pageItems = await request(`${path}${separator}per_page=100&page=${page}`);

    items.push(...pageItems);

    if (pageItems.length < 100) {
      return items;
    }

    page += 1;
  }
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

if (require.main === module) {
  main().catch((error) => {
    console.error(error.message);
    process.exit(1);
  });
}

module.exports = {
  closePromotedIssues,
  collectLinkedIssues,
  dedupeIssueNumbers,
  isMergedIntoDevelopment,
  listPaginated,
};
