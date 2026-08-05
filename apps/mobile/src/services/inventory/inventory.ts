import { api } from "../api"

export const getUserInventoriesRequest = async () => {
    const response = await api.get('/inventory/user-inventories')
    return response.data
}

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