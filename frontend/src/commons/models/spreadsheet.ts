export type SpreadsheetError = {
  code: string
  line: number | string
  errors: string[]
}

export type ImportReport = {
  totalProcessed: number
  successes: number
  failures: number
  errors: SpreadsheetError[]
  // Quantas salas a importação criou no setor (ausente em servidores antigos).
  createdRooms?: number
}

// Salas citadas na planilha: as novas serão criadas ao importar; as outras já existem no setor.
export type RoomsPreview = {
  newRooms: string[]
  existingRooms: string[]
}

export type SpreadsheetState = {
  file: File | null
  status:
    'idle' | 'validating' | 'valid' | 'invalid' | 'importing' | 'completed'
  message: string
  errors: SpreadsheetError[]
  report: ImportReport | null
  rooms: RoomsPreview | null
}
