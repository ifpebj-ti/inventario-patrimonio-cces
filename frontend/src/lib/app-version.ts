// Rótulo exibido para a versão do app: "v7.3.0" na release, "dev" quando o build não veio do CI.
export function formatAppVersion(raw?: string): string {
  const version = raw?.trim()
  if (!version) return 'dev'
  return /^v/i.test(version) ? version : `v${version}`
}
