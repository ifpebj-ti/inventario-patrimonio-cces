const {
  getLinkedIssueNumbers,
  getSelectedReleaseOptions,
} = require("./pr-body");

function validatePullRequest({ body, headRef, baseRef, author }) {
  const normalizedHeadRef = String(headRef || "");
  const normalizedBaseRef = String(baseRef || "");
  const linkedIssues = getLinkedIssueNumbers(body);
  const selectedReleaseOptions = getSelectedReleaseOptions(body);
  const selectedReleaseOption = selectedReleaseOptions[0] || null;
  const errors = [];
  const flow = getPullRequestFlow({
    headRef: normalizedHeadRef,
    baseRef: normalizedBaseRef,
    author,
  });

  if (flow === "unsupported") {
    errors.push(
      `Fluxo de PR nao suportado: ${normalizedHeadRef} -> ${normalizedBaseRef}.`,
    );
  }

  if (flow === "dependabot-development") {
    return {
      valid: true,
      flow,
      errors: [],
      selectedReleaseOption,
      linkedIssues,
    };
  }

  if (["work-development", "hotfix-main"].includes(flow)) {
    requireExactlyOneLinkedIssue(linkedIssues, errors);
  }

  if (["work-development", "main-development"].includes(flow)) {
    requireExactlyOneReleaseOption(selectedReleaseOptions, errors);
    requireReleaseType(selectedReleaseOption, "none", errors);
  }

  if (flow === "development-main") {
    requireExactlyOneReleaseOption(selectedReleaseOptions, errors);
  }

  if (flow === "hotfix-main") {
    requireExactlyOneReleaseOption(selectedReleaseOptions, errors);
    requireReleaseType(selectedReleaseOption, "patch", errors);
  }

  return {
    valid: errors.length === 0,
    flow,
    errors,
    selectedReleaseOption,
    linkedIssues,
  };
}

function getPullRequestFlow({ headRef, baseRef, author }) {
  if (author === "dependabot[bot]" && baseRef === "development") {
    return "dependabot-development";
  }

  if (baseRef === "development" && headRef === "main") {
    return "main-development";
  }

  if (baseRef === "development") {
    return "work-development";
  }

  if (baseRef === "main" && headRef === "development") {
    return "development-main";
  }

  if (baseRef === "main" && headRef.startsWith("hotfix/")) {
    return "hotfix-main";
  }

  return "unsupported";
}

function requireExactlyOneLinkedIssue(linkedIssues, errors) {
  if (linkedIssues.length !== 1) {
    errors.push(
      "Informe exatamente uma issue vinculada usando `Closes #numero`, `Fixes #numero` ou `Resolves #numero`.",
    );
  }
}

function requireExactlyOneReleaseOption(selectedReleaseOptions, errors) {
  if (selectedReleaseOptions.length !== 1) {
    errors.push("Marque exatamente uma opcao em `Tipo de release`.");
  }
}

function requireReleaseType(selectedReleaseOption, expectedKey, errors) {
  if (!selectedReleaseOption || selectedReleaseOption.key !== expectedKey) {
    const expectedLabel =
      expectedKey === "none" ? "sem release" : selectedReleaseOptionLabel(expectedKey);

    errors.push(`Este fluxo exige que o tipo de release seja \`${expectedLabel}\`.`);
  }
}

function selectedReleaseOptionLabel(key) {
  return (
    {
      patch: "patch",
      minor: "minor",
      major: "major",
      none: "sem release",
    }[key] || key
  );
}

function main() {
  const result = validatePullRequest({
    body: process.env.PR_BODY || "",
    headRef: process.env.HEAD_REF || "",
    baseRef: process.env.BASE_REF || "",
    author: process.env.PR_AUTHOR || "",
  });

  if (!result.valid) {
    console.error("A descricao do PR precisa de ajustes:");
    for (const error of result.errors) {
      console.error(`- ${error}`);
    }
    process.exit(1);
  }

  if (result.flow === "dependabot-development") {
    console.log("PR do Dependabot para development dispensado da validacao.");
    return;
  }

  console.log(
    `PR valido para o fluxo ${result.flow}. Tipo de release: ${result.selectedReleaseOption.label}.`,
  );
}

if (require.main === module) {
  main();
}

module.exports = {
  getPullRequestFlow,
  validatePullRequest,
};
