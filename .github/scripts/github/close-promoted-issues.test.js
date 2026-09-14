const test = require("node:test");
const assert = require("node:assert/strict");
const {
  closePromotedIssues,
  collectLinkedIssues,
  dedupeIssueNumbers,
  isMergedIntoDevelopment,
} = require("./close-promoted-issues");

test("identifies merged PRs targeting development", () => {
  assert.equal(
    isMergedIntoDevelopment({ base: { ref: "development" }, merged_at: "2026-09-12T12:00:00Z" }),
    true,
  );
  assert.equal(
    isMergedIntoDevelopment({ base: { ref: "main" }, merged_at: "2026-09-12T12:00:00Z" }),
    false,
  );
  assert.equal(
    isMergedIntoDevelopment({ base: { ref: "development" }, merged_at: null }),
    false,
  );
});

test("collects linked issues and reports human PRs without issue", () => {
  const result = collectLinkedIssues([
    pr({ number: 10, body: "Closes #66" }),
    pr({ number: 11, body: "" }),
    pr({ number: 12, body: "", author: "dependabot[bot]" }),
  ]);

  assert.deepEqual(result.issueNumbers, ["66"]);
  assert.equal(result.errors.length, 1);
  assert.match(result.errors[0], /PR #11/);
  assert.equal(result.skippedPullRequests.length, 1);
});

test("deduplicates and sorts issue numbers", () => {
  assert.deepEqual(dedupeIssueNumbers(["70", "66", "70"]), ["66", "70"]);
});

test("closes open issues discovered from promoted development PRs", async () => {
  const calls = [];
  const request = async (path, options = {}) => {
    calls.push({ path, options });

    if (path.startsWith("/pulls/50/commits")) {
      return [{ sha: "abc" }, { sha: "def" }];
    }

    if (path.startsWith("/commits/abc/pulls")) {
      return [
        pr({ number: 40, body: "Closes #66" }),
        pr({ number: 50, base: "main", body: "" }),
      ];
    }

    if (path.startsWith("/commits/def/pulls")) {
      return [
        pr({ number: 40, body: "Closes #66" }),
        pr({ number: 41, body: "Fixes #67" }),
      ];
    }

    if (path === "/issues/66" && options.method === "PATCH") {
      return { number: 66, state: "closed" };
    }

    if (path === "/issues/66") {
      return { number: 66, state: "open" };
    }

    if (path === "/issues/67") {
      return { number: 67, state: "closed" };
    }

    throw new Error(`Unexpected request: ${path}`);
  };

  const result = await closePromotedIssues({
    request,
    promotionPrNumber: 50,
  });

  assert.deepEqual(result.issueNumbers, ["66", "67"]);
  assert.deepEqual(result.closedIssues, ["66"]);
  assert.deepEqual(result.skippedIssues, ["#67 is already closed."]);
  assert.equal(
    calls.some(
      (call) => call.path === "/issues/66" && call.options.method === "PATCH",
    ),
    true,
  );
});

function pr({ number, body, author = "pedro", base = "development" }) {
  return {
    number,
    body,
    base: { ref: base },
    merged_at: "2026-09-12T12:00:00Z",
    user: { login: author },
  };
}
