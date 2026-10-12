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
}

export type SpreadsheetState = {
  file: File | null
  status:
    'idle' | 'validating' | 'valid' | 'invalid' | 'importing' | 'completed'
  message: string
  errors: SpreadsheetError[]
  report: ImportReport | null
}
