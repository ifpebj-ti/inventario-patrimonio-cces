import { TableRowProps } from './types'

// Cria somente uma linha na tabela com evento de doubleClick e personalização no tailwind
export const TableRow = ({
  children,
  onDoubleClick,
  className,
}: TableRowProps) => {
  return (
    <tr className={className} onDoubleClick={onDoubleClick}>
      {children}
    </tr>
  )
}
