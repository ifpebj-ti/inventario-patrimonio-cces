// Tipo da observação
export interface Observation {
  id: number | null
  content: string
}

// Situação do item no inventário: pendente (ainda não conferido), validado (encontrado) ou não
// localizado (conferido e não encontrado). O inventário só finaliza sem itens pendentes.
export type ValidationStatus = 'PENDENTE' | 'VALIDADO' | 'NAO_LOCALIZADO'

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
  // Sala cadastrada do item (do setor) e sala em que foi encontrado na validação.
  roomId?: number | null
  roomName?: string | null
  foundRoomId?: number | null
  foundRoomName?: string | null
  // Validado numa sala diferente da cadastrada.
  divergent?: boolean
  isValid?: boolean
  // Ausente em servidores antigos: nesse caso vale isValid (validado ou pendente).
  validationStatus?: ValidationStatus
  observations?: Observation[]
  validatedAt?: string
}
