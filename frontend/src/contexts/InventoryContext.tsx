'use client'

import React, {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
  ReactNode,
} from 'react'
import {
  getUserInventoriesRequest,
  InventoryResponse,
} from '@/services/inventory'
import { useAuth } from '@/hooks/useAuth'

interface InventoryContextData {
  inventories: InventoryResponse[]
  isLoading: boolean
  refreshInventories: () => Promise<InventoryResponse[]>
}

const InventoryContext = createContext<InventoryContextData>(
  {} as InventoryContextData,
)

function parseInventoriesList(raw: unknown): InventoryResponse[] {
  if (Array.isArray(raw)) {
    return raw as InventoryResponse[]
  }
  if (raw && typeof raw === 'object') {
    const obj = raw as Record<string, unknown>
    if (Array.isArray(obj.data)) {
      return obj.data as InventoryResponse[]
    }
    if (Array.isArray(obj.inventories)) {
      return obj.inventories as InventoryResponse[]
    }
  }
  return []
}

export function InventoryProvider({ children }: { children: ReactNode }) {
  const { isAuthenticated, user } = useAuth()
  const [inventories, setInventories] = useState<InventoryResponse[]>([])
  const [isLoading, setIsLoading] = useState(false)

  const refreshInventories = useCallback(async () => {
    try {
      setIsLoading(true)
      const data = await getUserInventoriesRequest()
      const list = parseInventoriesList(data)
      setInventories(list)
      return list
    } catch (error) {
      console.error('Erro ao buscar inventários:', error)
      return []
    } finally {
      setIsLoading(false)
    }
  }, [])

  useEffect(() => {
    if (isAuthenticated) {
      refreshInventories()
    } else {
      setInventories([])
    }
  }, [isAuthenticated, user, refreshInventories])

  // Escuta eventos customizados e evento de foco da janela para sincronização em tempo real
  useEffect(() => {
    const handleUpdate = () => {
      if (isAuthenticated) {
        refreshInventories()
      }
    }

    window.addEventListener('inventarium-inventories-updated', handleUpdate)
    window.addEventListener('focus', handleUpdate)

    return () => {
      window.removeEventListener(
        'inventarium-inventories-updated',
        handleUpdate,
      )
      window.removeEventListener('focus', handleUpdate)
    }
  }, [isAuthenticated, refreshInventories])

  return (
    <InventoryContext.Provider
      value={{ inventories, isLoading, refreshInventories }}
    >
      {children}
    </InventoryContext.Provider>
  )
}

export function useInventory(): InventoryContextData {
  const context = useContext(InventoryContext)
  if (!context) {
    throw new Error(
      'useInventory deve ser utilizado dentro de um InventoryProvider',
    )
  }
  return context
}
