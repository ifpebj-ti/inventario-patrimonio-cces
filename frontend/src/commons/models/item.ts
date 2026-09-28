// Tipo da observação
export interface Observation {
  id: number | null
  content: string
}

// Tipo do item
export interface Item {
  id: number
  code: string
  description: string
  price: number | string
  qrCode?: string
  qr_code?: string
  responsible: string
  locale?: string // A interrogação '?' indica que 'locale' é opcional (pode existir ou não)
  isValid?: boolean
  observations?: Observation[]
  validatedAt?: string
}
