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
  price: number
  // qr_code: string
  responsible: string
  locale?: string // A interrogação '?' indica que 'locale' é opcional (pode existir ou não)
  isValid?: boolean
  observations?: Observation[]
  // validated_at: Date
  // observation_id: number
  // inventory_id: number
}
