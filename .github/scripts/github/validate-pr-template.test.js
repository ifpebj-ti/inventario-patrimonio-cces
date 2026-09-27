const test = require("node:test");
const assert = require("node:assert/strict");
const { getLinkedIssueNumbers } = require("./pr-body");
const {
  getPullRequestFlow,
  validatePullRequest,
} = require("./validate-pr-template");
const { shouldSyncIssueSummary } = require("./sync-pr-with-issue");

test("extracts unique linked issue numbers", () => {
  assert.deepEqual(getLinkedIssueNumbers("Closes #10\nFixes #10\nResolves #11"), [
    "10",
    "11",
  ]);
});

test("accepts work PRs to main with one issue and one release type", () => {
  const result = validatePullRequest({
    body: prBody({ issue: 66, release: "minor" }),
    headRef: "feat/containerize-app",
    baseRef: "main",
    author: "pedro",
  });

  assert.equal(result.valid, true);
  assert.equal(result.flow, "work-main");
});

test("rejects work PRs to main without a linked issue", () => {
  const result = validatePullRequest({
    body: prBody({ release: "patch" }),
    headRef: "fix/containerize-app",
    baseRef: "main",
    author: "pedro",
  });

  assert.equal(result.valid, false);
  assert.match(result.errors.join("\n"), /exatamente uma issue/);
});

test("rejects work PRs to main without exactly one release type", () => {
  const result = validatePullRequest({
    body: prBody({ issue: 66 }),
    headRef: "docs/branching-strategy",
    baseRef: "main",
    author: "pedro",
  });

  assert.equal(result.valid, false);
  assert.match(result.errors.join("\n"), /Tipo de release/);
});

test("accepts hotfix PRs to main only with patch", () => {
  const accepted = validatePullRequest({
    body: prBody({ issue: 90, release: "patch" }),
    headRef: "hotfix/login",
    baseRef: "main",
    author: "pedro",
  });
  const rejected = validatePullRequest({
    body: prBody({ issue: 90, release: "minor" }),
    headRef: "hotfix/login",
    baseRef: "main",
    author: "pedro",
  });

  assert.equal(accepted.valid, true);
  assert.equal(accepted.flow, "hotfix-main");
  assert.equal(rejected.valid, false);
  assert.match(rejected.errors.join("\n"), /`patch`/);
});

test("exempts Dependabot PRs to main", () => {
  const result = validatePullRequest({
    body: "",
    headRef: "dependabot/npm_and_yarn/frontend/eslint-10.8.1",
    baseRef: "main",
    author: "dependabot[bot]",
  });

  assert.equal(result.valid, true);
  assert.equal(result.flow, "dependabot-main");
});

test("classifies non-main targets as unsupported", () => {
  assert.equal(
    getPullRequestFlow({
      headRef: "feat/direct-legacy",
      baseRef: "legacy",
      author: "pedro",
    }),
    "unsupported",
  );
});

test("syncs issue summaries for human PRs to main only", () => {
  assert.equal(
    shouldSyncIssueSummary({
      headRef: "feat/api",
      baseRef: "main",
      author: "pedro",
    }),
    true,
  );
  assert.equal(
    shouldSyncIssueSummary({
      headRef: "dependabot/npm_and_yarn/frontend/eslint-10.8.1",
      baseRef: "main",
      author: "dependabot[bot]",
    }),
    false,
  );
  assert.equal(
    shouldSyncIssueSummary({
      headRef: "feat/api",
      baseRef: "legacy",
      author: "pedro",
    }),
    false,
  );
});

function prBody({ issue, release }) {
  const options = ["patch", "minor", "major", "sem release"];
  const labels = {
    patch: "patch",
    minor: "minor",
    major: "major",
    none: "sem release",
  };

  return [
    "## Issue vinculada",
    "",
    issue ? `Closes #${issue}` : "",
    "",
    "## Tipo de release",
    "",
    ...options.map((option) => {
      const selected = option === labels[release] ? "x" : " ";
      return `- [${selected}] ${option}`;
    }),
  ].join("\n");
}
