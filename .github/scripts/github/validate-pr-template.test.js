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

test("requires linked issue and sem release for work PRs to development", () => {
  const result = validatePullRequest({
    body: prBody({ issue: 66, release: "none" }),
    headRef: "feat/containerize-app",
    baseRef: "development",
    author: "pedro",
  });

  assert.equal(result.valid, true);
  assert.equal(result.flow, "work-development");
});

test("rejects work PRs to development with release option different from sem release", () => {
  const result = validatePullRequest({
    body: prBody({ issue: 66, release: "minor" }),
    headRef: "feat/containerize-app",
    baseRef: "development",
    author: "pedro",
  });

  assert.equal(result.valid, false);
  assert.match(result.errors.join("\n"), /sem release/);
});

test("rejects work PRs to development without linked issue", () => {
  const result = validatePullRequest({
    body: prBody({ release: "none" }),
    headRef: "feat/containerize-app",
    baseRef: "development",
    author: "pedro",
  });

  assert.equal(result.valid, false);
  assert.match(result.errors.join("\n"), /exatamente uma issue/);
});

test("accepts promotion PRs from development to main without linked issue", () => {
  const result = validatePullRequest({
    body: prBody({ release: "minor" }),
    headRef: "development",
    baseRef: "main",
    author: "pedro",
  });

  assert.equal(result.valid, true);
  assert.equal(result.flow, "development-main");
});

test("accepts promotion PRs from development to main with sem release", () => {
  const result = validatePullRequest({
    body: prBody({ release: "none" }),
    headRef: "development",
    baseRef: "main",
    author: "pedro",
  });

  assert.equal(result.valid, true);
  assert.equal(result.selectedReleaseOption.key, "none");
});

test("rejects promotion PRs from development to main without release option", () => {
  const result = validatePullRequest({
    body: prBody({}),
    headRef: "development",
    baseRef: "main",
    author: "pedro",
  });

  assert.equal(result.valid, false);
  assert.match(result.errors.join("\n"), /Tipo de release/);
});

test("accepts hotfix PRs to main with linked issue and patch", () => {
  const result = validatePullRequest({
    body: prBody({ issue: 90, release: "patch" }),
    headRef: "hotfix/login",
    baseRef: "main",
    author: "pedro",
  });

  assert.equal(result.valid, true);
  assert.equal(result.flow, "hotfix-main");
});

test("requires patch for hotfix PRs to main", () => {
  const result = validatePullRequest({
    body: prBody({ issue: 70, release: "minor" }),
    headRef: "hotfix/login",
    baseRef: "main",
    author: "pedro",
  });

  assert.equal(result.valid, false);
  assert.match(result.errors.join("\n"), /`patch`/);
});

test("accepts main to development sync only with sem release", () => {
  const result = validatePullRequest({
    body: prBody({ release: "none" }),
    headRef: "main",
    baseRef: "development",
    author: "pedro",
  });

  assert.equal(result.valid, true);
  assert.equal(result.flow, "main-development");
});

test("exempts dependabot PRs to development", () => {
  const result = validatePullRequest({
    body: "",
    headRef: "dependabot/npm_and_yarn/frontend/eslint-10.8.1",
    baseRef: "development",
    author: "dependabot[bot]",
  });

  assert.equal(result.valid, true);
  assert.equal(result.flow, "dependabot-development");
});

test("classifies unsupported flows", () => {
  assert.equal(
    getPullRequestFlow({
      headRef: "feat/direct-main",
      baseRef: "main",
      author: "pedro",
    }),
    "unsupported",
  );
});

test("syncs issue summaries only for work and hotfix flows", () => {
  assert.equal(
    shouldSyncIssueSummary({
      headRef: "feat/api",
      baseRef: "development",
      author: "pedro",
    }),
    true,
  );
  assert.equal(
    shouldSyncIssueSummary({
      headRef: "hotfix/api",
      baseRef: "main",
      author: "pedro",
    }),
    true,
  );
  assert.equal(
    shouldSyncIssueSummary({
      headRef: "development",
      baseRef: "main",
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
