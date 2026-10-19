import { ReactNode } from 'react'

// Alocação do usuário em um setor, com o cargo dele nesse setor e as permissões do cargo.
export type Allocation = {
  sectorId: number
  roleId: number
  roleName: string
  permissions: string[]
}

// Dados de sessão; não confundir com UserDto das listagens administrativas.
export type User = {
  id: number
  name: string
  email: string
  administrator: boolean
  sectorIds: number[]
  allocations: Allocation[]
}

// Tipo para o contexto de autenticação, importante para usar atributos e métodos
export type AuthContextType = {
  user: User | null
  // Verdadeiro apenas enquanto a sessão inicial é recuperada a partir do cookie.
  loading: boolean
  isAuthenticated: boolean
  // Recebe o ID token do Google devolvido pelo <GoogleLogin>.
  signInWithGoogle: (credential: string) => Promise<void>
  // Login da conta de sistema (usuário e senha, sem Google).
  signInWithPassword: (username: string, password: string) => Promise<void>
  refreshSession: () => Promise<User>
  signOut: () => void
}

// Importante criar o tipo das propriedades do provider
// É um children de um nó do react, ou seja tag html com js
export type ProviderProps = {
  children: ReactNode
}
