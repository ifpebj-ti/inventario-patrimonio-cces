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

// O backend não devolve texto de erro no corpo, então a mensagem vem do status ao salvar um item:
// 403 (desvalidar ou editar dados é do gestor do setor) e 409 (item validado ou inventário
// finalizado: só observações podem mudar).
export const updateItemErrorMessage = (error: unknown): string => {
  if (axios.isAxiosError(error)) {
    switch (error.response?.status) {
      case 403:
        return 'Seu cargo neste setor não permite esta alteração. Desvalidar um item e editar os dados é do gestor do setor.'
      case 409:
        return 'Item validado ou não localizado, ou inventário finalizado: só é possível registrar observações. O gestor pode desfazer a conferência do item ou reabrir o inventário para corrigir.'
    }
  }
  return 'Não foi possível salvar o item. Tente novamente.'
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

// Marca o item como não localizado. O motivo é obrigatório: fica na auditoria e vira uma
// observação do item. Quem opera o inventário marca; só o gestor desfaz.
export const markItemNotFoundRequest = async (
  itemId: number,
  reason: string,
): Promise<Item> => {
  const response = await api.patch<Item>(`/item/${itemId}/not-found`, {
    reason,
  })
  return response.data
}

// A mensagem vem do status (o backend não devolve texto de erro): 400 (motivo ausente ou longo
// demais), 403 (cargo sem permissão para operar o inventário) e 409 (inventário finalizado ou
// item que já foi conferido).
export const markNotFoundErrorMessage = (error: unknown): string => {
  if (axios.isAxiosError(error)) {
    switch (error.response?.status) {
      case 400:
        return 'Informe o motivo, com até 255 caracteres.'
      case 403:
        return 'Seu cargo neste setor não permite marcar itens como não localizados.'
      case 409:
        return 'O inventário está finalizado ou o item já foi conferido. Atualize a página.'
    }
  }
  return 'Não foi possível marcar o item. Tente novamente.'
}

// Atualiza apenas as observações de um item, usando o método PATCH para uma atualização parcial.
export const updateItemNotesRequest = async (
  itemId: number,
  notesArray: Observation[],
): Promise<Item> => {
  const response = await api.patch<Item>(`/item/${itemId}/notes`, notesArray)
  return response.data
}
