'use client'

import React from 'react'

// Define as propriedades (props) que o componente ConfirmationModal pode receber.
// Isso cria um "contrato" claro de como usar este componente.
interface ConfirmationModalProps {
  isOpen: boolean // Controla se o modal está visível ou não.
  onClose: () => void // Função a ser chamada para fechar o modal.
  onConfirm: () => void // Função a ser chamada quando a ação principal é confirmada.
  title: string // O título exibido no modal.
  message: string // A mensagem ou pergunta de confirmação.
  confirmButtonText?: string // Texto opcional para o botão de confirmação.
  cancelButtonText?: string // Texto opcional para o botão de cancelar.
}

// Componente de modal genérico e reutilizável para ações de confirmação (ex: deletar).
export const ConfirmationModal: React.FC<ConfirmationModalProps> = ({
  isOpen,
  onClose,
  onConfirm,
  title,
  message,
  confirmButtonText = 'Confirmar', // Define 'Confirmar' como texto padrão.
  cancelButtonText = 'Cancelar', // Define 'Cancelar' como texto padrão.
}) => {
  // Se a prop 'isOpen' for falsa, o componente não renderiza nada no DOM.
  if (!isOpen) return null

  // Função para fechar o modal se o usuário clicar no fundo escuro (backdrop).
  const handleBackdropClick = (e: React.MouseEvent) => {
    // Verifica se o clique foi no próprio elemento de backdrop, e não em um de seus filhos (o painel do modal).
    if (e.target === e.currentTarget) {
      onClose()
    }
  }

  return (
    <>
      {/* Backdrop: o fundo escuro e com blur que cobre a página. */}
      <div
        className="fixed inset-0 bg-opacity-30 backdrop-blur-sm z-50"
        onClick={handleBackdropClick}
      />
      {/* Contêiner que usa Flexbox para centralizar o modal na tela. */}
      <div className="fixed inset-0 flex justify-center items-center z-50 p-4">
        {/* Painel branco principal do modal. */}
        <div className="bg-white rounded-2xl shadow-xl p-8 relative w-full max-w-lg">
          {/* Botão de fechar (X) no canto superior direito. */}
          <button
            className="absolute top-3 right-4 text-gray-400 hover:text-gray-600 text-3xl font-bold transition-colors cursor-pointer"
            onClick={onClose}
          >
            &times;
          </button>
          {/* Título e mensagem do modal, preenchidos dinamicamente via props. */}
          <h2 className="text-2xl font-bold text-slate-800 mb-4">{title}</h2>
          <p className="text-slate-600">{message}</p>
          {/* Contêiner para os botões de ação, alinhados à direita. */}
          <div className="mt-8 flex justify-end gap-4">
            {/* Botão de Cancelar, que chama a função onClose. */}
            <button
              onClick={onClose}
              className="px-6 py-2 rounded-lg bg-slate-200 hover:bg-slate-300 font-semibold text-slate-800 transition-colors cursor-pointer"
            >
              {cancelButtonText}
            </button>
            {/* Botão de Confirmar, que chama a função onConfirm. */}
            <button
              onClick={onConfirm}
              className="px-6 py-2 rounded-lg bg-red-500 text-white font-semibold hover:bg-red-600 transition-colors cursor-pointer"
            >
              {confirmButtonText}
            </button>
          </div>
        </div>
      </div>
    </>
  )
}
