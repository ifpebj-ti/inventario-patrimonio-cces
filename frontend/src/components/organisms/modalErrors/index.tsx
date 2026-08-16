import { errorSheet } from '@/app/(authenticated)/inventory/[id]/page'
import React from 'react'

// Define as propriedades que o modal de erros espera receber.
interface ModalErrorsProps {
  onClose: () => void // Função para fechar o modal.
  title?: string // Título opcional para o modal.
  errors: errorSheet[] | null // Array de erros a serem exibidos.
}

// Componente de modal projetado especificamente para exibir uma lista de erros
// de validação de uma planilha.
export const ModalErrorsSheet = ({
  onClose,
  title,
  errors,
}: ModalErrorsProps) => {
  // Função que permite fechar o modal ao clicar no fundo escuro (backdrop).
  const handleBackdropClick = (e: React.MouseEvent) => {
    if (e.target === e.currentTarget) {
      onClose()
    }
  }

  return (
    <>
      {/* Backdrop - fundo escuro e com blur. */}
      <div
        className="fixed inset-0 bg-opacity-20 backdrop-blur-sm z-60"
        onClick={handleBackdropClick}
      />

      {/* Contêiner que centraliza o conteúdo do modal na tela. */}
      <div className="fixed inset-0 flex justify-center items-center z-60 p-4">
        {/* O painel branco principal do modal, com estilos de sombra, borda e rolagem. */}
        <div className="bg-white rounded-3xl shadow-2xl p-8 relative w-full max-w-2xl max-h-screen overflow-y-auto">
          {/* Botão para fechar o modal no canto superior direito. */}
          <button
            className="absolute top-4 right-4 text-gray-500 hover:text-gray-700 text-2xl font-bold transition-colors cursor-pointer"
            onClick={onClose}
          >
            &times;
          </button>

          <h2 className="text-2xl text-red-500 font-semibold mb-6">{title}</h2>

          {/* Renderiza a lista de erros apenas se o array 'errors' existir e não estiver vazio. */}
          {errors && errors.length > 0 && (
            <div className="space-y-6">
              {/* Mapeia cada item de erro para criar um bloco de visualização. */}
              {errors.map((item, index) => (
                <div
                  key={index}
                  className="border border-red-200 bg-red-50 rounded-lg p-4"
                >
                  <p className="font-semibold text-red-700">
                    Código: <span className="font-normal">{item.code}</span>
                  </p>
                  <p className="text-sm text-gray-600 mb-2">
                    Linha da planilha: {item.line}
                  </p>
                  {/* Mapeia a lista de mensagens de erro para o item atual. */}
                  <ul className="list-disc pl-5 text-sm text-red-600 space-y-1">
                    {item.errors.map((err, idx) => (
                      <li key={idx}>{err}</li>
                    ))}
                  </ul>
                </div>
              ))}
            </div>
          )}

          {/* Seção com o botão para fechar o modal. */}
          <div className="flex justify-center mt-8">
            <button
              onClick={onClose}
              className="px-6 py-2 bg-gray-200 hover:bg-gray-300 text-gray-800 rounded-lg transition-colors cursor-pointer"
            >
              Fechar
            </button>
          </div>
        </div>
      </div>
    </>
  )
}
