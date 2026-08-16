import { ReactNode } from 'react'

// Propriedades do cabeçalho da tabela
export type TableHeaderCellProps = {
  text: string | ReactNode
  onClick?: () => void
  canSort?: boolean
}
