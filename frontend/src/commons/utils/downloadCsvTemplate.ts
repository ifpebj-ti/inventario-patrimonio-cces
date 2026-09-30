/**
 * Função utilitária para gerar e disparar o download imediato do arquivo CSV modelo de importação.
 * Utiliza separador ponto e vírgula (;) e BOM UTF-8 (\uFEFF) para garantir abertura automática perfeita
 * em programas como Microsoft Excel (padrão Brasil), LibreOffice Calc e Google Planilhas.
 */
export const downloadCsvTemplate = () => {
  const headers = [
    'Código',
    'Descrição',
    'Responsável',
    'Valor',
    'Sala',
    'Observações',
  ]

  const rows = [
    [
      '102345',
      'Notebook Dell Latitude 3420',
      'Coordenação de TI',
      '3500,00',
      'Laboratório 02',
      'Bem em perfeito estado',
    ],
    [
      '102346',
      'Monitor LG 24 Polegadas IPS',
      'Secretaria Acadêmica',
      '750,00',
      'Sala 105',
      'Etiqueta original legível',
    ],
    [
      '102347',
      'Cadeira Ergonômica Presidente',
      'Diretoria Geral',
      '620,00',
      'Gabinete',
      '',
    ],
  ]

  const csvRows = [
    headers.join(';'),
    ...rows.map((row) =>
      row
        .map((cell) => {
          // Escapa aspas duplas caso existam
          const escaped = String(cell).replace(/"/g, '""')
          return `"${escaped}"`
        })
        .join(';'),
    ),
  ]

  const csvString = '\uFEFF' + csvRows.join('\r\n')
  const blob = new Blob([csvString], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.setAttribute('download', 'modelo_importacao_patrimonio.csv')
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
