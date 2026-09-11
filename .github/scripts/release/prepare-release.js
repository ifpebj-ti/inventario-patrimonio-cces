const fs = require("fs");
const path = require("path");
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
  const releaseOption = getReleaseOption(prBody);

  if (!releaseOption) {
    throw new Error("PR body does not contain exactly one release option.");
  }

  if (releaseOption.key === "none") {
    writeOutput("skip", "true");
    console.log("Release skipped because PR is marked as sem release.");
    return;
  }

  const existingRelease = getExistingReleaseForPr(prNumber);
  const summary = getReleaseSummary(prBody, prTitle, prNumber);

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

  const packageJsonPath = path.join(ROOT, "package.json");
  const packageJson = readJson(packageJsonPath);
  const currentVersion = packageJson.version;
  const nextVersion = incrementVersion(currentVersion, releaseOption.key);
  const tag = `v${nextVersion}`;
  const today = new Date().toISOString().slice(0, 10);

  packageJson.version = nextVersion;
  writeJson(packageJsonPath, packageJson);
  updatePackageLock(nextVersion);
  updateChangelog(nextVersion, today, releaseOption.heading, summary, prNumber);

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

function getReleaseSummary(prBody, prTitle, prNumber) {
  const doneSection = stripHtmlComments(getSection(prBody, "O que foi feito"));
  const issueSection = stripHtmlComments(getSection(prBody, "Descricao da issue"));
  const source = doneSection && doneSection !== "-" ? doneSection : issueSection;
  const cleaned = normalizeMarkdownList(source);

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

function updatePackageLock(nextVersion) {
  const lockPath = path.join(ROOT, "package-lock.json");

  if (!fs.existsSync(lockPath)) {
    return;
  }

  const lock = readJson(lockPath);
  lock.version = nextVersion;

  if (lock.packages?.[""]) {
    lock.packages[""].version = nextVersion;
  }

  writeJson(lockPath, lock);
}

function getExistingReleaseForPr(prNumber) {
  if (!prNumber) {
    return null;
  }

  const changelogPath = path.join(ROOT, "CHANGELOG.md");

  if (!fs.existsSync(changelogPath)) {
    return null;
  }

  const changelog = fs.readFileSync(changelogPath, "utf8");
  const marker = `<!-- release-pr:${prNumber} -->`;
  const markerIndex = changelog.indexOf(marker);

  if (markerIndex === -1) {
    return null;
  }

  const afterMarker = changelog.slice(markerIndex + marker.length);
  const versionMatch = afterMarker.match(/## \[(\d+\.\d+\.\d+)\]/);

  return versionMatch ? { version: versionMatch[1] } : null;
}

function updateChangelog(version, date, heading, summary, prNumber) {
  const changelogPath = path.join(ROOT, "CHANGELOG.md");
  const current = fs.existsSync(changelogPath)
    ? fs.readFileSync(changelogPath, "utf8")
    : "# Changelog\n\nTodas as mudancas relevantes do Inventarium serao documentadas neste arquivo.\n";
  const marker = prNumber ? `<!-- release-pr:${prNumber} -->\n` : "";
  const entry = [
    `${marker}## [${version}] - ${date}`,
    "",
    `### ${heading}`,
    "",
    summary,
    "",
  ].join("\n");
  const lines = current.split(/\r?\n/);
  const firstReleaseIndex = lines.findIndex((line) => /^## \[/.test(line));

  if (firstReleaseIndex === -1) {
    fs.writeFileSync(changelogPath, `${current.trim()}\n\n${entry}\n`);
    return;
  }

  const before = lines.slice(0, firstReleaseIndex).join("\n").trimEnd();
  const after = lines.slice(firstReleaseIndex).join("\n").trimStart();

  fs.writeFileSync(changelogPath, `${before}\n\n${entry}\n${after}\n`);
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

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, "utf8"));
}

function writeJson(filePath, value) {
  fs.writeFileSync(filePath, `${JSON.stringify(value, null, 2)}\n`);
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
