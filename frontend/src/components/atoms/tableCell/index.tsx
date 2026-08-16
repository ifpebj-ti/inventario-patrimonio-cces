import { TableCellProps } from './types'

// Cria apenas uma célula personalizada pra tabela
export const TableCell = ({ text }: TableCellProps) => {
  return (
    <td className="px-6 py-4 text-left text-xl text-slate-700 whitespace-normal">
      <div className="line-clamp-2">{text}</div>
    </td>
  )
}
