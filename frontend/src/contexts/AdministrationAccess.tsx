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
import { capabilitiesOf, roleOf, type AccessRole } from '@/lib/access'

type Capabilities = ReturnType<typeof capabilitiesOf>

type Access = Capabilities & {
  loading: boolean
  role: AccessRole
  // Setores a que o usuário está alocado (o gestor só gerencia estes).
  sectorIds: number[]
  error: string | null
  refresh: () => Promise<void>
}

const AccessContext = createContext<Access | null>(null)
const blocked = {
  loading: true,
  role: 'consult' as AccessRole,
  sectorIds: [] as number[],
  ...capabilitiesOf('consult'),
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
    const role = roleOf(sessionUser.permissions)
    setState({
      loading: false,
      role,
      sectorIds: sessionUser.sectorIds ?? [],
      ...capabilitiesOf(role),
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
