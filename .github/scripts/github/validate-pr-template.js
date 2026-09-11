const {
  getLinkedIssueNumber,
  getSelectedReleaseOptions,
} = require("./pr-body");

const body = process.env.PR_BODY || "";
const errors = [];
const selectedReleaseOptions = getSelectedReleaseOptions(body);

if (!getLinkedIssueNumber(body)) {
  errors.push(
    "Informe uma issue vinculada usando `Closes #numero`, `Fixes #numero` ou `Resolves #numero`.",
  );
}

if (selectedReleaseOptions.length !== 1) {
  errors.push("Marque exatamente uma opcao em `Tipo de release`.");
}

if (errors.length > 0) {
  console.error("A descricao do PR precisa de ajustes:");
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  process.exit(1);
}

console.log(
  `PR valido. Tipo de release: ${selectedReleaseOptions[0].label}.`,
);
