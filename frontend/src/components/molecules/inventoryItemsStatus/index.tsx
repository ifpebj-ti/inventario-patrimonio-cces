import { ItemsValid } from '@/components/molecules/inventoryItemsStatus/types'

// Componente de "card de status", projetado para exibir um resumo visual
// da quantidade de itens verificados e não verificados em um inventário.
export const InventoryItemsStatus = ({ content = [] }: ItemsValid) => {
  // Filtra o array 'content' para contar quantos itens têm a propriedade 'isValid' como true.
  const verifiedCount = content.filter((item) => item.isValid).length
  // Filtra o array 'content' para contar quantos itens têm a propriedade 'isValid' como false.
  const unverifiedCount = content.filter((item) => !item.isValid).length

  return (
    // Container principal do card, com estilização de fundo, bordas e sombra.
    <div className="bg-white rounded-2xl shadow-md p-6 sm:p-8 mx-auto w-full max-w-xl h-auto">
      {/* Container para os contadores numéricos, usando Flexbox para alinhá-los. */}
      <div className="flex justify-evenly items-start gap-4 sm:gap-8 my-4 sm:my-6">
        {/* Seção para exibir a contagem de itens verificados. */}
        <div className="text-center">
          <div className="text-5xl sm:text-7xl font-light text-green-500 mb-2">
            {verifiedCount}
          </div>
          <div className="text-gray-600 text-xs sm:text-sm font-medium">Itens verificados</div>
        </div>

        {/* Seção para exibir a contagem de itens não verificados. */}
        <div className="text-center">
          <div className="text-5xl sm:text-7xl font-light text-red-500 mb-2">
            {unverifiedCount}
          </div>
          <div className="text-gray-600 text-xs sm:text-sm font-medium">Itens não verificados</div>
        </div>
      </div>

      {/* Texto instrutivo na parte inferior do card. */}
      <div className="text-center">
        <p className="text-gray-600 text-sm">
          Verifique os itens através do app em seu celular.
        </p>
      </div>
    </div>
  )
}
