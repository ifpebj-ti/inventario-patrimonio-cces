import { api } from './api'
import { z } from 'zod'
import inventory from '@/validations/inventory'
import {
  readImportResponse,
  readValidationResponse,
  SpreadsheetRequestError,
} from '@/commons/utils/spreadsheet'
import axios from 'axios'

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

  try {
    const response = await api.post('/inventory/add-items', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    if (response.status !== 201 && response.status !== 206) {
      throw new SpreadsheetRequestError(
        'O servidor não confirmou a importação. Confira o inventário.',
      )
    }
    const report = readImportResponse(response.data)
    if (report.successes === 0) {
      throw new SpreadsheetRequestError(
        'Nenhum item foi importado. Consulte o relatório.',
        report.errors,
        report,
      )
    }
    if (
      (response.status === 201 && report.failures > 0) ||
      (response.status === 206 && report.failures === 0)
    ) {
      throw new SpreadsheetRequestError(
        'Resposta de importação inconsistente. Confira o inventário.',
        report.errors,
        report,
      )
    }
    return report
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.status === 400) {
      const data = error.response.data
      if (
        data &&
        typeof data === 'object' &&
        ('failedItems' in data || 'failedItems\n' in data)
      ) {
        const report = readImportResponse(data)
        throw new SpreadsheetRequestError(
          'Nenhum item foi importado. Consulte o relatório.',
          report.errors,
          report,
        )
      }
    }
    throw error
  }
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

// HTTP de erro continua sendo rejeitado pelo Axios; o corpo nunca substitui o status.
export const validateSpreadsheetData = async (
  data: File,
  signal?: AbortSignal,
) => {
  const formData = new FormData()
  formData.append('file', data)

  const response = await api.post('/inventory/validate-sheet', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
    signal,
  })

  readValidationResponse(response.status, response.data)
}

// Limpa todos os itens do inventário de uma vez só
export const clearInventoryItemsRequest = async (inventoryId: number) => {
  const response = await api.delete('/inventory/clear-items', {
    params: {
      inventoryId,
    },
  })
  return response.data
}
