import axios from 'axios'
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
  try {
    await api.post('/item/send-email-sheet', payload, {
      params: { inventoryId },
    })
  } catch (error) {
    throw new Error(sendSheetErrorMessage(error))
  }
}

// O backend não devolve texto de erro no corpo, então a mensagem vem do status:
// 400 (destinatário de domínio não permitido ou dados inválidos), 403 (só
// administrador e gestor do setor), 429 (limite de envios) e 502 (o servidor de
// e-mail não aceitou a mensagem).
const sendSheetErrorMessage = (error: unknown): string => {
  if (axios.isAxiosError(error)) {
    switch (error.response?.status) {
      case 400:
        return 'Destinatário inválido ou de domínio não permitido. Use um e-mail institucional.'
      case 403:
        return 'Seu cargo neste setor não permite enviar a planilha.'
      case 429:
        return 'Muitos envios em pouco tempo. Tente novamente mais tarde.'
      case 502:
        return 'O servidor de e-mail não aceitou a mensagem. Tente novamente.'
    }
  }
  return 'Não foi possível enviar.'
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

// Valida o item informando a sala em que foi encontrado (obrigatória em setor com salas).
export const validateItemRequest = async (
  itemId: number,
  roomId: number | null,
): Promise<Item> => {
  const response = await api.patch<Item>(
    `/item/${itemId}/validate`,
    roomId === null ? undefined : { roomId },
  )
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
