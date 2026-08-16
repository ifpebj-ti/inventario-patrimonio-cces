import { api } from './api'
import { z } from 'zod'
import inventory from '@/validations/inventory'

// TIPOS PARA REQUEST
export type CreateInventoryRequest = z.infer<typeof inventory>

// TIPOS PARA RESPONSE
export type InventoryResponse = {
  id: number
  name: string
  description: string
  createdAt: string
}

// tipo para atualizar inventario
export type UpdateInventory = {
  name: string
  description: string
}

// FUNÇÕES DO SERVICE
export const getUserInventoriesRequest = async () => {
  const response = await api.get('/inventory/user-inventories')
  return response.data
}

export const createInventoryRequest = async (
  data: CreateInventoryRequest,
): Promise<InventoryResponse> => {
  const response = await api.post('/inventory/new-inventory', data)
  return response.data
}

export const deleteInventoryRequest = async (inventoryId: number) => {
  const response = await api.delete(`inventory/${inventoryId}`)
  return response
}

export const updateInventoryRequest = async (
  data: UpdateInventory,
  inventoryId: number,
): Promise<InventoryResponse> => {
  const response = await api.put(`inventory/${inventoryId}`, data)
  return response.data
}

// Busca os itens de um inventário com paginação, passando os dados via query params.
export const getInventoryItemsRequest = async (
  inventoryId: number,
  page: number,
  pageSize: number,
) => {
  const response = await api.get('/inventory/inventory-items', {
    params: {
      inventoryId,
      page,
      pageSize,
    },
  })
  return response.data
}

// Envia um arquivo de planilha usando FormData, definindo o header para multipart.
export const addItemsBySheet = async (data: File, inventoryId: number) => {
  const formData = new FormData()
  formData.append('file', data)
  formData.append('inventoryId', String(inventoryId))

  const response = await api.post('/inventory/add-items', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })

  return response.data
}

// Deleta um item específico enviando os IDs como query params.
export const deleteItemFromInventory = async (
  itemId: number,
  inventoryId: number,
) => {
  const response = await api.delete('/inventory/delete-item', {
    params: {
      itemId,
      inventoryId,
    },
  })

  return response.data
}

// Valida a planilha. 'validateStatus' faz o Axios tratar respostas de erro (ex: 400) como sucesso.
export const validateSpreadsheetData = async (data: File) => {
  const formData = new FormData()
  formData.append('file', data)

  const response = await api.post('/inventory/validate-sheet', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
    validateStatus: () => true,
  })

  return response.data
}
