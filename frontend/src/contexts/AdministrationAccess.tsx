'use client'

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from 'react'
import { useAuth } from '@/hooks/useAuth'
import { capabilitiesOf } from '@/lib/access'
import type { Allocation } from '@/contexts/Auth/types'

type Capabilities = ReturnType<typeof capabilitiesOf>

type Access = Capabilities & {
  loading: boolean
  // Setores a que o usuário está alocado (o gestor só gerencia os que têm MANAGE_SECTOR).
  sectorIds: number[]
  // Cargo e permissões do usuário em cada setor.
  allocations: Allocation[]
  error: string | null
  refresh: () => Promise<void>
}

const AccessContext = createContext<Access | null>(null)
const blocked = {
  loading: true,
  sectorIds: [] as number[],
  allocations: [] as Allocation[],
  ...capabilitiesOf({ administrator: false, allocations: [] }),
  error: null as string | null,
}

export const AdministrationAccessProvider = ({
  children,
}: {
  children: ReactNode
}) => {
  const { user, refreshSession } = useAuth()
  const [state, setState] = useState(blocked)
  const applyAccess = useCallback((sessionUser: typeof user) => {
    if (!sessionUser) {
      setState({ ...blocked, loading: false })
      return
    }
    const allocations = sessionUser.allocations ?? []
    setState({
      loading: false,
      sectorIds: sessionUser.sectorIds ?? [],
      allocations,
      ...capabilitiesOf({
        administrator: !!sessionUser.administrator,
        allocations,
      }),
      error: null,
    })
  }, [])

  const refresh = useCallback(async () => {
    if (!user) {
      applyAccess(null)
      return
    }
    applyAccess(await refreshSession())
  }, [applyAccess, refreshSession, user])

  useEffect(() => {
    applyAccess(user)
  }, [applyAccess, user])

  return (
    <AccessContext.Provider value={{ ...state, refresh }}>
      {children}
    </AccessContext.Provider>
  )
}

export const useAdministrationAccess = () => {
  const context = useContext(AccessContext)
  if (!context) throw new Error('AdministrationAccessProvider não encontrado')
  return context
}
