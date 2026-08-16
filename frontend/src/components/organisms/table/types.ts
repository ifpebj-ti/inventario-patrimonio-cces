import { InventoryResponse } from '@/services/inventory'
import { Item } from '@/commons/models/item'

// Cria interface que aqui serve basicamente como tipagem permitindo usar métodos e atributos
export interface TableProps {
  header: { key: string; headerText: string }[]
  content?: (Item | InventoryResponse)[]

  selectable?: boolean
  selectedRows?: number[]
  showExportButtons?: boolean
  onExportSelected?: () => void
  onExportAll?: () => void
  onExportSheet?: () => void
  onSendEmailSheet?: () => void
  onRowSelect?: (selectedIds: number[]) => void
  onRowDoubleClick?: (item: Item | InventoryResponse) => void
  onDeleteItem?: (item: Item | InventoryResponse) => void
  onEditItem?: (item: InventoryResponse) => void
}
