/**
 * Trunca nomes de arquivos muito longos preservando a extensão quando disponível,
 * evitando quebra de layouts em cards, modais e alertas.
 *
 * Exemplo:
 * "relatorio_bens_patrimoniais_2026_campus_belo_jardim.xlsx" -> "relatorio_bens_pa...xlsx"
 */
export const truncateFileName = (fileName: string, maxLength = 24): string => {
  if (!fileName || fileName.length <= maxLength) return fileName

  const lastDotIndex = fileName.lastIndexOf('.')
  // Verifica se possui extensão de até 7 caracteres (ex: .xlsx, .csv, .json, .pdf)
  if (lastDotIndex > 0 && fileName.length - lastDotIndex <= 7) {
    const ext = fileName.slice(lastDotIndex)
    const nameWithoutExt = fileName.slice(0, lastDotIndex)
    const availableChars = maxLength - ext.length - 3 // 3 reservado para as reticências "..."
    if (availableChars > 3) {
      return `${nameWithoutExt.slice(0, availableChars)}...${ext}`
    }
  }

  // Caso não possua extensão válida ou a extensão ocupe quase todo o espaço
  return `${fileName.slice(0, Math.max(3, maxLength - 3))}...`
}
