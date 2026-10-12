import axios from 'axios'
import { z } from 'zod'
import type { ImportReport, SpreadsheetError } from '../models/spreadsheet'

const errorSchema = z.object({
  code: z.string(),
  line: z.union([z.number().int().positive(), z.string()]),
  errors: z.array(z.string()).min(1),
})

export class SpreadsheetRequestError extends Error {
  constructor(
    message: string,
    readonly errors: SpreadsheetError[] = [],
    readonly report: ImportReport | null = null,
  ) {
    super(message)
    this.name = 'SpreadsheetRequestError'
  }
}

export const readValidationResponse = (status: number, data: unknown) => {
  const result = z
    .object({
      message: z.string().trim().min(1).optional(),
      hasErrors: z.boolean().optional(),
      errors: z.array(errorSchema).optional(),
    })
    .strict()
    .safeParse(data)

  if (!result.success) {
    throw new SpreadsheetRequestError(
      'Resposta de validação inválida. Tente novamente.',
    )
  }
  const { message, hasErrors, errors = [] } = result.data
  if (status !== 200 || hasErrors === true || errors.length > 0) {
    throw new SpreadsheetRequestError(
      errors.length > 0
        ? 'Erros encontrados na planilha. Consulte o relatório e corrija o arquivo.'
        : message || 'A planilha não foi aprovada para importação.',
      errors,
    )
  }
  if (!message && hasErrors !== false) {
    throw new SpreadsheetRequestError(
      'O servidor não confirmou a validação da planilha.',
    )
  }
}

export const readImportResponse = (data: unknown): ImportReport => {
  if (!data || typeof data !== 'object') {
    throw new SpreadsheetRequestError(
      'Resposta de importação inválida. Confira o inventário antes de tentar novamente.',
    )
  }
  const body = data as Record<string, unknown>
  const result = z
    .object({
      totalProcessed: z.number().int().nonnegative(),
      successes: z.number().int().nonnegative(),
      failures: z.number().int().nonnegative(),
      failedItems: z.array(
        z.object({
          code: z.string(),
          line: z.union([z.number().int().positive(), z.string()]),
          message: z.string(),
          success: z.literal(false),
        }),
      ),
    })
    .safeParse({
      totalProcessed: body.totalProcessed ?? body['totalProcessed\n'],
      successes: body.successes ?? body['successes\n'],
      failures: body.failures ?? body['failures\n'],
      failedItems: body.failedItems ?? body['failedItems\n'],
    })
  if (
    !result.success ||
    result.data.totalProcessed === 0 ||
    result.data.successes + result.data.failures !==
      result.data.totalProcessed ||
    result.data.failedItems.length !== result.data.failures
  ) {
    throw new SpreadsheetRequestError(
      'Resposta de importação inválida. Confira o inventário antes de tentar novamente.',
    )
  }
  return {
    totalProcessed: result.data.totalProcessed,
    successes: result.data.successes,
    failures: result.data.failures,
    errors: result.data.failedItems.map((item) => ({
      code: item.code,
      line: item.line,
      errors: [item.message],
    })),
  }
}

export const getSpreadsheetFailure = (
  error: unknown,
): SpreadsheetRequestError => {
  if (error instanceof SpreadsheetRequestError) return error
  if (axios.isAxiosError(error)) {
    const status = error.response?.status
    if (status === 401)
      return new SpreadsheetRequestError(
        'Sua sessão expirou. Entre novamente para continuar.',
      )
    if (status === 403)
      return new SpreadsheetRequestError(
        'Você não tem permissão para realizar esta operação.',
      )
    if (!error.response)
      return new SpreadsheetRequestError(
        'Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.',
      )
    if (status && status >= 500)
      return new SpreadsheetRequestError(
        'O serviço de planilhas está indisponível. Tente novamente mais tarde.',
      )
    const data: unknown = error.response.data
    try {
      readValidationResponse(status || 400, data)
    } catch (failure) {
      if (failure instanceof SpreadsheetRequestError) return failure
    }
  }
  return new SpreadsheetRequestError(
    'Não foi possível concluir a operação. Tente novamente.',
  )
}

const normalizeHeader = (value: string) =>
  value
    .trim()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()

const parseLine = (line: string, delimiter: string) => {
  const cells: string[] = []
  let cell = ''
  let quoted = false
  let closed = false
  for (let index = 0; index < line.length; index++) {
    const character = line[index]
    if (character === '"') {
      if (quoted && line[index + 1] === '"') {
        cell += '"'
        index++
      } else if (quoted) {
        quoted = false
        closed = true
      } else if (!cell.trim() && !closed) {
        quoted = true
      } else {
        throw new Error('Aspas em posição inválida.')
      }
    } else if (character === delimiter && !quoted) {
      cells.push(cell.trim())
      cell = ''
      closed = false
    } else {
      if (closed && character.trim())
        throw new Error('Texto após o fechamento de aspas.')
      cell += character
    }
  }
  if (quoted)
    throw new Error(
      'Aspas não fechadas. Campos com quebra de linha não são suportados.',
    )
  cells.push(cell.trim())
  return cells
}

export const inspectCsv = async (file: File): Promise<SpreadsheetError[]> => {
  if (!file.name.toLowerCase().endsWith('.csv')) {
    throw new SpreadsheetRequestError(
      'Formato não permitido. Envie apenas arquivos .csv.',
    )
  }
  let text: string
  try {
    text = await file.text()
  } catch {
    throw new SpreadsheetRequestError(
      'Não foi possível ler o arquivo CSV. Selecione o arquivo novamente.',
    )
  }
  const lines = text.replace(/^\uFEFF/, '').split(/\r\n|\n|\r/)
  const header = lines[0]
  if (!header?.trim())
    throw new SpreadsheetRequestError(
      'O arquivo CSV está vazio. Baixe o modelo oficial.',
    )
  // Mesma prioridade de separadores usada pelo backend.
  const delimiter = header.includes(';')
    ? ';'
    : header.includes(',')
      ? ','
      : '\t'
  let headers: string[]
  try {
    headers = parseLine(header, delimiter).map(normalizeHeader)
  } catch {
    throw new SpreadsheetRequestError(
      'Cabeçalho CSV inválido. Verifique as aspas e baixe o modelo oficial.',
    )
  }
  const codeColumn = headers.findIndex(
    (value) =>
      value === 'cod' ||
      ['codigo', 'patrimonio', 'etiqueta', 'tombamento'].some((alias) =>
        value.includes(alias),
      ),
  )
  if (codeColumn === -1)
    throw new SpreadsheetRequestError(
      'Coluna "Código" não encontrada. Baixe o modelo oficial.',
    )
  const priceColumn = headers.findIndex((value) =>
    ['val', 'prec', 'price', 'custo'].some((alias) => value.includes(alias)),
  )
  const errors: SpreadsheetError[] = []
  const codes = new Map<string, number>()
  let rows = 0
  lines.slice(1).forEach((line, index) => {
    if (!line.trim()) return
    rows++
    const lineNumber = index + 2
    try {
      const cells = parseLine(line, delimiter)
      const code = cells[codeColumn] || ''
      const messages: string[] = []
      if (cells.length !== headers.length)
        messages.push('Quantidade de colunas diferente do cabeçalho.')
      if (!code) messages.push('Código não pode ser vazio.')
      if (code.length > 30)
        messages.push('Código não pode ter mais de 30 caracteres.')
      if (code && codes.has(code))
        messages.push(
          `Código repetido no arquivo; primeira ocorrência na linha ${codes.get(code)}.`,
        )
      else if (code) codes.set(code, lineNumber)
      const price = priceColumn === -1 ? '' : cells[priceColumn]?.trim() || ''
      if (
        price &&
        !/^(?:R\$\s*)?(?:\d+(?:,\d{1,2})?|\d{1,3}(?:\.\d{3})+,\d{1,2})$/.test(
          price,
        ) &&
        !/^\d+(?:\.\d{1,2})?$/.test(price)
      )
        messages.push(
          'Valor inválido. Informe um preço não negativo, com até duas casas decimais.',
        )
      if (messages.length > 0)
        errors.push({ code, line: lineNumber, errors: messages })
    } catch (error) {
      errors.push({
        code: '',
        line: lineNumber,
        errors: [
          error instanceof Error ? error.message : 'Linha CSV inválida.',
        ],
      })
    }
  })
  if (rows === 0)
    throw new SpreadsheetRequestError(
      'A planilha não contém itens após o cabeçalho.',
    )
  return errors
}
