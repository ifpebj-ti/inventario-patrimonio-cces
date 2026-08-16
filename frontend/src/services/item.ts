import { api } from '@/services/api'
import sendEmail from '@/validations/sendEmail'
import { z } from 'zod'
import { Item, Observation } from '@/commons/models/item'

// Define o tipo dos dados para a requisição de envio de e-mail, baseado no schema de validação Zod.
export type SendEmailSheetRequest = z.infer<typeof sendEmail>

// As funções abaixo usam 'responseType: blob' para tratar a resposta da API como um arquivo para download.
export const generateQRCodeLabelsPdf = async (
  itemsIds: number[],
): Promise<Blob> => {
  const response = await api.post('/item/pdf', itemsIds, {
    responseType: 'blob',
  })
  return response.data
}

export const generateQRCodeAllLabelsPdf = async (
  inventoryId: number,
): Promise<Blob> => {
  const response = await api.post('/item/all-items-pdf', inventoryId, {
    responseType: 'blob',
  })
  return response.data
}

export const generateItemsSheet = async (
  inventoryId: number,
): Promise<Blob> => {
  const response = await api.get('/item/export-sheet', {
    params: {
      inventoryId,
    },
    responseType: 'blob',
  })
  return response.data
}

// Envia a planilha por e-mail, passando os destinatários no corpo e o ID do inventário como query param.
export const sendSheetByEmail = async (
  inventoryId: number,
  payload: SendEmailSheetRequest,
): Promise<void> => {
  await api.post('/item/send-email-sheet', payload, {
    params: { inventoryId },
  })
}

// Requisição para pegar item por Id
export const getItemByIdRequest = async (itemId: number): Promise<Item> => {
  const response = await api.get<Item>(`/item/${itemId}`)
  console.log(response.data)
  return response.data
}

// Atualização de dados de itens como preço, descrição etc
export const updateItemRequest = async (updatedItem: Item): Promise<Item> => {
  const response = await api.put<Item>(`/item/${updatedItem.id}`, updatedItem)
  return response.data
}

// Atualiza apenas as observações de um item, usando o método PATCH para uma atualização parcial.
export const updateItemNotesRequest = async (
  itemId: number,
  notesArray: Observation[],
): Promise<Item> => {
  const response = await api.patch<Item>(`/item/${itemId}/notes`, notesArray)
  return response.data
}
