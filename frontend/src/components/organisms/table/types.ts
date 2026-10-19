import { InventoryResponse } from '@/services/inventory'
import { Item } from '@/commons/models/item'
import type { ReactNode } from 'react'

// Cria interface que aqui serve basicamente como tipagem permitindo usar métodos e atributos
export interface TableProps {
  header: { key: string; headerText: string }[]
  content?: (Item | InventoryResponse)[]
  filterToolbar?: ReactNode
  appearance?: 'default' | 'inventory'

  selectable?: boolean
  selectedRows?: number[]
  showExportButtons?: boolean
  onClearInventory?: () => void
  disableClear?: boolean
  // Esconde o botão de limpar todos os itens (perfis que não gerenciam o inventário).
  hideClear?: boolean
  onDeleteSelected?: () => void
  onExportSelected?: () => void
  onExportSheet?: () => void
  onSendEmailSheet?: () => void
  onRowSelect?: (selectedIds: number[]) => void
  onRowDoubleClick?: (item: Item | InventoryResponse) => void
  onDeleteItem?: (item: Item | InventoryResponse) => void
  onEditItem?: (item: InventoryResponse) => void
  // Quando informados, escondem Editar/Excluir nas linhas em que a função devolve falso (o cargo do
  // usuário vale por setor, então a mesma tabela pode ter linhas editáveis e linhas só de leitura).
  canEditItem?: (item: InventoryResponse) => boolean
  canDeleteItem?: (item: InventoryResponse) => boolean
}
