import { useState } from 'react'
import { TableHeaderCellProps } from './types'
import { IoIosArrowDown, IoIosArrowUp } from 'react-icons/io'

// Componente para uma célula de cabeçalho de tabela (<th>).
// Ele pode exibir um ícone de ordenação que alterna entre para cima e para baixo.
export const TableHeaderCell = ({
  text,
  onClick,
  canSort = false, // Prop para controlar se a funcionalidade de ordenação está ativa.
}: TableHeaderCellProps) => {
  // Estado interno para controlar a direção da seta (para cima ou para baixo).
  const [isArrowUp, setIsArrowUp] = useState(false)

  // Função que inverte o estado da seta. Atribuída ao contêiner principal.
  const afterClicked = () => setIsArrowUp(!isArrowUp)

  // Função que inverte o estado da seta e também chama a função 'onClick' vinda do componente pai.
  const handleIconClick = () => {
    // Usa a forma funcional do 'setState' para garantir que o valor mais recente seja usado.
    setIsArrowUp((prevIsArrowUp) => !prevIsArrowUp)

    // Se uma função 'onClick' foi passada como prop, ela é executada.
    if (onClick) {
      onClick()
    }
  }

  return (
    // Elemento de cabeçalho da tabela.
    <th
      scope="col"
      className="px-6 py-4 text-center text-xl font-semibold text-slate-700 tracking-tight"
    >
      {/* Contêiner flexível para alinhar o texto e o ícone.
          Um clique nesta área inteira dispara a função 'afterClicked'. */}
      <div
        className="flex flex-row items-center justify-center gap-4"
        onClick={afterClicked}
      >
        <span>{text}</span>
        {/* Renderiza os ícones de ordenação apenas se 'canSort' for verdadeiro. */}
        {canSort &&
          // Operador ternário que escolhe qual ícone exibir com base no estado 'isArrowUp'.
          (isArrowUp ? (
            // Se 'isArrowUp' for true, exibe a seta para cima.
            <IoIosArrowUp
              onClick={handleIconClick}
              className="cursor-pointer"
            />
          ) : (
            // Se 'isArrowUp' for false, exibe a seta para baixo.
            <IoIosArrowDown
              onClick={handleIconClick}
              className="cursor-pointer"
            />
          ))}
      </div>
    </th>
  )
}
