import { ReactNode } from 'react'

// Tipos da linha da tabela, com métodos e atributos
export type TableRowProps = {
  children: ReactNode
  onDoubleClick?: () => void
  className?: string
}
