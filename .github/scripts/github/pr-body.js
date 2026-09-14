const RELEASE_OPTIONS = [
  { key: "patch", label: "patch", heading: "Fixed" },
  { key: "minor", label: "minor", heading: "Added" },
  { key: "major", label: "major", heading: "Breaking Changes" },
  { key: "none", label: "sem release", heading: "" },
];

function getLinkedIssueNumber(body) {
  const [first] = getLinkedIssueNumbers(body);

  return first || null;
}

function getLinkedIssueNumbers(body) {
  const matches = String(body || "").matchAll(
    /\b(?:close[sd]?|fix(?:e[sd])?|resolve[sd]?)\s+#(\d+)\b/gi,
  );

  return [...new Set([...matches].map((match) => match[1]))];
}

function getSection(body, heading) {
  const lines = String(body || "").split(/\r?\n/);
  const headingPattern = new RegExp(`^##\\s+${escapeRegExp(heading)}\\s*$`, "i");
  const start = lines.findIndex((line) => headingPattern.test(line.trim()));

  if (start === -1) {
    return "";
  }

  const rest = lines.slice(start + 1);
  const end = rest.findIndex((line) => /^##\s+/.test(line.trim()));
  return (end === -1 ? rest : rest.slice(0, end)).join("\n").trim();
}

function getSelectedReleaseOptions(body) {
  const releaseSection = getSection(body, "Tipo de release");
  const selected = [];

  for (const line of releaseSection.split(/\r?\n/)) {
    const checkbox = line.match(/^\s*-\s*\[([ xX])\]\s*(.+?)\s*$/);

    if (!checkbox || checkbox[1].toLowerCase() !== "x") {
      continue;
    }

    const text = checkbox[2].toLowerCase();
    const option = RELEASE_OPTIONS.find((item) => text.startsWith(item.label));

    if (option) {
      selected.push(option);
    }
  }

  return selected;
}

function getReleaseOption(body) {
  const selected = getSelectedReleaseOptions(body);
  return selected.length === 1 ? selected[0] : null;
}

function stripHtmlComments(text) {
  return String(text || "").replace(/<!--[\s\S]*?-->/g, "").trim();
}

function escapeRegExp(text) {
  return text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

module.exports = {
  RELEASE_OPTIONS,
  getLinkedIssueNumber,
  getLinkedIssueNumbers,
  getReleaseOption,
  getSection,
  getSelectedReleaseOptions,
  stripHtmlComments,
};
