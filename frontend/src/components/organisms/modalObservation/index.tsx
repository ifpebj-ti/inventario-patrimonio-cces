'use client'

import React from 'react'
import { Button } from '@/components/atoms/button'
import { FaPlus, FaPencilAlt, FaTrashAlt } from 'react-icons/fa'
import { Observation } from '@/commons/models/item'

// Define as propriedades que o modal de observações espera receber.
interface ObservationModalProps {
  isOpen: boolean
  onClose: () => void
  onSave: (updatedNotes: Observation[]) => void
  currentNotes: Observation[]
}

// Componente de modal para visualizar, adicionar, editar e deletar observações de um item.
export const ObservationModal: React.FC<ObservationModalProps> = ({
  isOpen,
  onClose,
  currentNotes,
  onSave,
}) => {
  // Estado interno para manipular as observações sem afetar o estado pai até que 'Concluir' seja clicado.
  const [observations, setObservations] = React.useState<Observation[]>([])
  // Estado para o campo de texto de uma nova observação.
  const [newObservationText, setNewObservationText] = React.useState<string>('')
  // Estado para controlar qual observação está sendo editada no momento (pelo seu índice no array).
  const [editingIndex, setEditingIndex] = React.useState<number | null>(null)
  // Estado para o texto da observação que está sendo editada.
  const [editingText, setEditingText] = React.useState<string>('')

  // Efeito que inicializa ou reseta o estado do modal sempre que ele é aberto.
  React.useEffect(() => {
    // Só executa a lógica se o modal estiver sendo aberto.
    if (isOpen) {
      // Carrega as observações atuais (vindas do componente pai) no estado local.
      setObservations(currentNotes ?? [])
      // Reseta os campos de texto e o modo de edição.
      setNewObservationText('')
      setEditingIndex(null)
    }
  }, [isOpen, currentNotes])

  // Se o modal não estiver aberto, não renderiza nada.
  if (!isOpen) return null

  // Adiciona uma nova observação à lista local.
  const handleAddObservation = () => {
    const trimmedText = newObservationText.trim()
    // Só adiciona se o texto não estiver vazio.
    if (trimmedText) {
      const newObservation: Observation = {
        id: null, // O ID será gerado pelo backend ao salvar.
        content: trimmedText,
      }
      setObservations((prev) => [...prev, newObservation])
      setNewObservationText('')
      setEditingIndex(null)
    }
  }

  // Deleta uma observação da lista local com base no seu índice.
  const handleDeleteObservation = (indexToDelete: number) => {
    setObservations((prev) =>
      prev.filter((_, index) => index !== indexToDelete),
    )
    // Lógica para ajustar o índice de edição caso um item seja removido.
    if (editingIndex === indexToDelete) {
      setEditingIndex(null)
      setEditingText('')
    } else if (editingIndex !== null && editingIndex > indexToDelete) {
      setEditingIndex((prev) => (prev !== null ? prev - 1 : null))
    }
  }

  // Prepara o estado para entrar no modo de edição de uma observação específica.
  const handleStartEdit = (index: number) => {
    setEditingIndex(index)
    setEditingText(observations[index].content)
  }

  // Salva a alteração de uma observação que estava sendo editada.
  const handleSaveIndividualEdit = (indexToSave: number) => {
    // Se o texto editado ficar vazio, deleta a observação.
    if (editingText.trim() === '') {
      handleDeleteObservation(indexToSave)
      setEditingIndex(null)
      setEditingText('')
      return
    }
    // Atualiza o conteúdo da observação específica no array.
    setObservations((prev) =>
      prev.map((obs, index) =>
        index === indexToSave ? { ...obs, content: editingText.trim() } : obs,
      ),
    )
    setEditingIndex(null)
    setEditingText('')
  }

  // Cancela o modo de edição de uma observação.
  const handleCancelIndividualEdit = () => {
    setEditingIndex(null)
    setEditingText('')
  }

  // Função final chamada pelo botão "Concluir".
  const handleConclude = () => {
    // Garante que observações vazias não sejam salvas.
    const finalObservations = observations.filter(
      (obs) => obs.content.trim() !== '',
    )
    // Envia a lista final de observações de volta para o componente pai.
    onSave(finalObservations)
    // Fecha o modal.
    onClose()
  }

  return (
    // Container do modal com fundo escuro (backdrop).
    <div className="fixed inset-0 bg-opacity-50 backdrop-blur-sm flex items-center justify-center z-50 p-4">
      {/* Painel branco principal do modal. */}
      <div className="bg-white relative rounded-lg shadow-xl p-6 w-full max-w-md">
        {/* Botão para fechar o modal no canto superior direito. */}
        <button
          className="absolute top-0 right-4 text-gray-500 hover:text-gray-700 text-2xl font-bold transition-colors cursor-pointer"
          onClick={onClose}
        >
          &times;
        </button>

        <h2 className="text-xl font-bold text-blue-600 mb-4 text-center">
          Editar Observações
        </h2>

        {/* Área de listagem das observações com rolagem interna. */}
        <div className="max-h-60 overflow-y-auto mb-4 border border-gray-200 rounded p-2">
          {observations.length === 0 && editingIndex === null ? (
            <p className="text-gray-500 text-sm italic text-center">
              Nenhuma observação ainda.
            </p>
          ) : (
            // Mapeia o array de observações para renderizar cada uma.
            observations.map((obs, index) => (
              <div
                key={index}
                className="flex items-center justify-between bg-gray-50 p-2 rounded mb-2"
              >
                {/* Renderização condicional: mostra um input se estiver em modo de edição, senão mostra o texto. */}
                {editingIndex === index ? (
                  <input
                    type="text"
                    value={editingText}
                    onChange={(e) => setEditingText(e.target.value)}
                    onBlur={() => handleSaveIndividualEdit(index)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') handleSaveIndividualEdit(index)
                      if (e.key === 'Escape') handleCancelIndividualEdit()
                    }}
                    className="flex-grow border-b border-gray-400 p-1 mr-2"
                    autoFocus
                  />
                ) : (
                  <span className="text-gray-800">{obs.content}</span>
                )}

                {/* Ícones de ação para editar e deletar cada observação. */}
                <div className="flex gap-2">
                  {editingIndex !== index && (
                    <FaPencilAlt
                      className="text-gray-500 cursor-pointer"
                      size={16}
                      onClick={() => handleStartEdit(index)}
                    />
                  )}
                  <FaTrashAlt
                    className="text-red-500 cursor-pointer"
                    size={16}
                    onClick={() => handleDeleteObservation(index)}
                  />
                </div>
              </div>
            ))
          )}
        </div>

        {/* Seção para adicionar uma nova observação. */}
        <div className="flex items-center gap-2 mb-4">
          <input
            type="text"
            value={newObservationText}
            onChange={(e) => setNewObservationText(e.target.value)}
            placeholder="Nova observação"
            className="flex-grow border border-gray-300 rounded p-2"
            onKeyDown={(e) => {
              if (e.key === 'Enter') handleAddObservation()
            }}
          />
          <Button
            text=""
            type="button"
            onClick={handleAddObservation}
            icon={<FaPlus size={20} />}
            className="min-w-0 px-3 py-2"
          />
        </div>

        {/* Botão final para salvar todas as alterações e fechar o modal. */}
        <div className="flex justify-end mt-6">
          <Button
            text="Concluir"
            type="button"
            onClick={handleConclude}
            width="w-full"
          />
        </div>
      </div>
    </div>
  )
}
