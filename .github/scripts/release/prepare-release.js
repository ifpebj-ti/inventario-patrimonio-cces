const fs = require("fs");
const path = require("path");
const { execFileSync } = require("child_process");
const {
  getReleaseOption,
  getSection,
  stripHtmlComments,
} = require("../github/pr-body");

const ROOT = process.cwd();

function main() {
  const prBody = process.env.PR_BODY || "";
  const prNumber = process.env.PR_NUMBER || "";
  const prTitle = process.env.PR_TITLE || "Mudancas do PR";
  const headRef = process.env.HEAD_REF || "";
  const baseRef = process.env.BASE_REF || "";
  const releaseOption = getReleaseOption(prBody);

  if (!shouldPrepareRelease({ headRef, baseRef })) {
    writeOutput("skip", "true");
    console.log(`Release skipped for unsupported flow ${headRef} -> ${baseRef}.`);
    return;
  }

  if (!releaseOption) {
    throw new Error("PR body does not contain exactly one release option.");
  }

  if (baseRef === "main" && headRef.startsWith("hotfix/") && releaseOption.key !== "patch") {
    throw new Error("Hotfix PRs to main must be marked as patch releases.");
  }

  if (releaseOption.key === "none") {
    writeOutput("skip", "true");
    console.log("Release skipped because PR is marked as sem release.");
    return;
  }

  const summary = getReleaseSummary(prBody, prTitle, prNumber);
  const existingRelease = getExistingReleaseForPr(prNumber);

  if (existingRelease) {
    const tag = `v${existingRelease.version}`;
    const releaseNotesPath = writeReleaseNotes(tag, releaseOption.heading, summary);

    writeOutput("skip", "false");
    writeOutput("version", existingRelease.version);
    writeOutput("tag", tag);
    writeOutput("release_notes_path", releaseNotesPath);

    console.log(`Release ${tag} was already prepared for PR #${prNumber}.`);
    return;
  }

  const currentVersion = getLatestVersionFromTags();
  const nextVersion = incrementVersion(currentVersion, releaseOption.key);
  const tag = `v${nextVersion}`;

  const releaseNotesPath = writeReleaseNotes(tag, releaseOption.heading, summary);

  writeOutput("skip", "false");
  writeOutput("version", nextVersion);
  writeOutput("tag", tag);
  writeOutput("release_notes_path", releaseNotesPath);

  console.log(`Prepared release ${tag}.`);
}

function incrementVersion(version, type) {
  const match = String(version).match(/^(\d+)\.(\d+)\.(\d+)(?:-.+)?$/);

  if (!match) {
    throw new Error(`Invalid package version: ${version}`);
  }

  let major = Number(match[1]);
  let minor = Number(match[2]);
  let patch = Number(match[3]);

  if (type === "major") {
    major += 1;
    minor = 0;
    patch = 0;
  } else if (type === "minor") {
    minor += 1;
    patch = 0;
  } else if (type === "patch") {
    patch += 1;
  } else {
    throw new Error(`Unknown release type: ${type}`);
  }

  return `${major}.${minor}.${patch}`;
}

function shouldPrepareRelease({ headRef, baseRef }) {
  return (
    baseRef === "main" &&
    (headRef === "development" || String(headRef || "").startsWith("hotfix/"))
  );
}

function getReleaseSummary(prBody, prTitle, prNumber) {
  const doneSection = stripHtmlComments(getSection(prBody, "O que foi feito"));
  const cleaned = normalizeMarkdownList(doneSection && doneSection !== "-" ? doneSection : "");

  if (cleaned) {
    return cleaned;
  }

  return `- ${prTitle}${prNumber ? ` (#${prNumber})` : ""}`;
}

function normalizeMarkdownList(text) {
  const lines = String(text || "")
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .filter((line) => !/^esta secao sera preenchida/i.test(line));

  if (lines.length === 0) {
    return "";
  }

  if (lines.some((line) => /^[-*]\s+/.test(line))) {
    return lines.join("\n");
  }

  return `- ${lines.join(" ")}`;
}

function getExistingReleaseForPr(prNumber) {
  if (!prNumber) {
    return null;
  }

  const marker = `release-pr:${prNumber}`;

  for (const tag of getSemverTags()) {
    const contents = git(["for-each-ref", "--format=%(contents)", `refs/tags/${tag}`]);

    if (contents.includes(marker)) {
      return { version: tag.slice(1) };
    }
  }

  return null;
}

function getLatestVersionFromTags() {
  const [latestTag] = getSemverTags();

  if (!latestTag) {
    return "0.0.0";
  }

  return latestTag.slice(1);
}

function getSemverTags() {
  return git(["tag", "--list", "v[0-9]*.[0-9]*.[0-9]*", "--sort=-v:refname"])
    .split(/\r?\n/)
    .map((tag) => tag.trim())
    .filter((tag) => /^v\d+\.\d+\.\d+$/.test(tag));
}

function writeReleaseNotes(tag, heading, summary) {
  const releaseNotesPath = path.join(
    process.env.RUNNER_TEMP || ROOT,
    `release-notes-${tag}.md`,
  );

  fs.writeFileSync(
    releaseNotesPath,
    [`## ${tag}`, "", `### ${heading}`, "", summary, ""].join("\n"),
  );

  return releaseNotesPath;
}

function git(args) {
  return execFileSync("git", args, { encoding: "utf8" });
}

function writeOutput(name, value) {
  const outputPath = process.env.GITHUB_OUTPUT;

  if (!outputPath) {
    console.log(`${name}=${value}`);
    return;
  }

  fs.appendFileSync(outputPath, `${name}=${value}\n`);
}

main();
