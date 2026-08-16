'use client'

import { useParams } from 'next/navigation'
import { useEffect, useState, useCallback } from 'react'
import { Button } from '@/components/atoms/button'
import { Item, Observation } from '@/commons/models/item'
import {
  getItemByIdRequest,
  updateItemRequest,
  updateItemNotesRequest,
} from '@/services/item'
import { RiPencilFill } from 'react-icons/ri'
import { ObservationModal } from '@/components/organisms/modalObservation'

type EditableItemType = Omit<
  Item,
  'price' | 'locale' | 'notes' | 'description'
> & {
  price: number | string | null | undefined
  locale: string | null | undefined
  notes: string | null | undefined
  description: string | undefined | null
}

export default function ItemDetailPage() {
  const { itemId } = useParams()
  const [item, setItem] = useState<Item | null>(null)
  const [editableItem, setEditableItem] = useState<EditableItemType | null>(
    null,
  )
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [isEditing, setIsEditing] = useState(false)
  const [isObservationModalOpen, setIsObservationModalOpen] = useState(false)

  const fetchItem = useCallback(async () => {
    if (!itemId) {
      setError('ID do item não fornecido. Não é possível carregar detalhes.')
      setIsLoading(false)
      return
    }
    try {
      setIsLoading(true)
      setError(null)
      const response = await getItemByIdRequest(Number(itemId))
      setItem(response)
      setEditableItem({
        ...response,
        price: response.price?.toString().replace('.', ',') || '',
        locale: response.locale || '',
        notes: response.observations?.[0]?.content || '',
        description: response.description || '',
      })
    } catch (err) {
      console.error('Erro ao carregar o item:', err)
      setError('Erro ao carregar o item. Por favor, tente novamente.')
    } finally {
      console.log('Item carregado:', editableItem?.notes)
      setIsLoading(false)
    }
  }, [itemId])

  useEffect(() => {
    fetchItem()
  }, [fetchItem])

  const handleChange = (
    e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>,
  ) => {
    const { name, value } = e.target
    setEditableItem((prev) => (prev ? { ...prev, [name]: value } : null))
  }

  const handleEditClick = () => setIsEditing(true)

  const handleCancelClick = () => {
    setIsEditing(false)
    setEditableItem(
      item
        ? {
            ...item,
            price: item.price?.toString().replace('.', ',') || '',
            locale: item.locale || '',
            notes: item.observations?.[0]?.content || '',
            description: item.description || '',
          }
        : null,
    )
  }

  const handleSaveClick = async () => {
    if (!editableItem || !editableItem.id) {
      setError('Dados do item inválidos para salvar.')
      return
    }
    // const cleanPriceString = String(editableItem.price).replace(',', '.')
    const cleanPriceString = String(editableItem.price)
      .replace(/[^\d,.-]/g, '') // remove tudo que não é dígito, vírgula, ponto ou sinal
      .replace(',', '.')
    const priceToSend = Number(cleanPriceString)
    if (isNaN(priceToSend) || !isFinite(priceToSend)) {
      setError('Valor do preço inválido.')
      return
    }

    try {
      setIsLoading(true)
      const savedItem = await updateItemRequest({
        ...editableItem,
        price: priceToSend,
      } as Item)
      setItem(savedItem)
      setEditableItem({
        ...savedItem,
        price: savedItem.price?.toString().replace('.', ',') || '',
        locale: savedItem.locale || '',
        notes: savedItem.observations?.[0]?.content || '',
        description: savedItem.description || '',
      })
      setIsEditing(false)
    } catch (err) {
      console.error('Erro ao salvar item principal:', err)
      setError('Falha ao salvar o item principal. Tente novamente.')
    } finally {
      setError(null)
      setIsLoading(false)
    }
  }

  const openObservationModal = () => setIsObservationModalOpen(true)
  const closeObservationModal = () => setIsObservationModalOpen(false)

  const handleSaveNotes = async (updatedNotesArray: Observation[]) => {
    if (!item || !item.id) {
      setError('ID do item necessário para salvar observações.')
      return
    }
    try {
      setIsLoading(true)
      const savedItem = await updateItemNotesRequest(item.id, updatedNotesArray)
      setItem(savedItem)
      setEditableItem({
        ...savedItem,
        price: savedItem.price?.toString().replace('.', ',') || '',
        locale: savedItem.locale || '',
        notes: savedItem.observations?.[0]?.content || '',
        description: savedItem.description || '',
      })
      closeObservationModal()
    } catch (err) {
      console.error('Erro ao salvar observações:', err)
      setError('Falha ao salvar observações. Tente novamente.')
    } finally {
      setError(null)
      setIsLoading(false)
    }
  }

  return (
    <div className="overflow-x-hidden flex flex-col justify-start items-center min-h-screen">
      {/* Container principal da página de detalhes */}
      <div className="flex flex-col items-center align-items-center w-full max-w-4xl p-4">
        {isLoading && (
          <p className="text-gray-500 text-lg mt-8">
            Carregando detalhes do item...
          </p>
        )}
        {error && <p className="text-red-500 text-lg mt-8">{error}</p>}
        {item && editableItem && (
          <div className="w-full bg-white shadow-md rounded-lg p-6 flex flex-col sm:flex-row justify-center items-start gap-12">
            <div className="flex-1 flex flex-col items-start">
              <div className="flex items-center gap-3">
                <h2 className="text-4xl font-bold text-green-600 mr-2">
                  {isEditing ? (
                    <input
                      type="text"
                      name="code"
                      value={editableItem.code}
                      onChange={handleChange}
                      className="border-b border-gray-400 text-green-600 text-4xl font-bold"
                    />
                  ) : (
                    item.code
                  )}
                </h2>
                {!isEditing && (
                  <button
                    onClick={handleEditClick}
                    aria-label="Editar item"
                    title="Editar item"
                    className="inline-flex items-center"
                  >
                    <RiPencilFill
                      className="text-gray-500 cursor-pointer"
                      size={20}
                    />
                  </button>
                )}
              </div>
              {isEditing ? (
                <textarea
                  name="description"
                  value={editableItem.description || ''}
                  onChange={handleChange}
                  className="border-b border-gray-400 text-xl text-gray-800 w-full mt-4"
                  rows={3}
                />
              ) : (
                <p className="text-xl text-gray-800 mt-4">{item.description}</p>
              )}
              <p className="text-lg text-gray-700 mt-3">
                Carga:{' '}
                {isEditing ? (
                  <input
                    type="text"
                    name="responsible"
                    value={editableItem.responsible}
                    onChange={handleChange}
                    className="border-b border-gray-400 text-lg text-gray-700"
                  />
                ) : (
                  item.responsible
                )}
              </p>
              <p className="text-lg text-gray-700 mt-3">
                Valor:{' '}
                {isEditing ? (
                  <input
                    type="text"
                    name="price"
                    value={editableItem.price || ''}
                    onChange={handleChange}
                    className="border-b border-gray-400 text-lg text-gray-700"
                  />
                ) : (
                  item.price?.toLocaleString('pt-BR', {
                    style: 'currency',
                    currency: 'BRL',
                  })
                )}
              </p>
              {item.locale && (
                <p className="text-lg text-gray-700 mt-3">
                  Sala:{' '}
                  {isEditing ? (
                    <input
                      type="text"
                      name="locale"
                      value={editableItem.locale || ''}
                      onChange={handleChange}
                      className="border-b border-gray-400 text-lg text-gray-700"
                    />
                  ) : (
                    item.locale
                  )}
                </p>
              )}
            </div>
            <div className="flex-1 p-6 rounded-lg shadow-md">
              <h3 className="text-md font-semibold text-gray-600 mb-2">
                Observações
              </h3>
              <div className="min-h-[80px] border border-gray-200 rounded p-3 text-gray-700 text-sm mt-2 w-full">
                {item.observations && item.observations.length > 0
                  ? item.observations.map((obs, index) => (
                      <p key={index}>{obs.content}</p>
                    ))
                  : 'Nenhuma observação.'}
              </div>
              <div className="flex gap-4 mt-4">
                {isEditing ? (
                  <>
                    <Button
                      text="Salvar"
                      type="button"
                      onClick={handleSaveClick}
                      width="w-full"
                      variant={3}
                      accessKey="s"
                    />
                    <Button
                      text="Cancelar"
                      type="button"
                      onClick={handleCancelClick}
                      width="w-full"
                      variant={2}
                      accessKey="x"
                    />
                  </>
                ) : (
                  <Button
                    text="Editar Observações"
                    type="button"
                    onClick={openObservationModal}
                    width="w-full"
                  />
                )}
              </div>
            </div>
          </div>
        )}
        {!isLoading && !error && !item && (
          <p className="text-gray-600 text-lg mt-8">Item não encontrado.</p>
        )}
      </div>
      <ObservationModal
        isOpen={isObservationModalOpen}
        onClose={closeObservationModal}
        currentNotes={item?.observations || []}
        onSave={handleSaveNotes}
      />
    </div>
  )
}
