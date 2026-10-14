// Tipo do inventário
export interface Inventory {
  id: number
  name: string
  description: string
  createdAt?: string
  plannedEndDate?: string | null
}
