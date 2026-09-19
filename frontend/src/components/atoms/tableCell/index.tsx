import { TableCellProps } from './types'

// Cria apenas uma célula personalizada pra tabela
export const TableCell = ({ text }: TableCellProps) => {
  return (
    <td className="px-2 sm:px-3 py-3 text-left text-xs sm:text-sm text-slate-700">
      {text}
    </td>
  )
}
