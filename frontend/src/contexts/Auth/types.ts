import { ReactNode } from 'react'

// Exatamente o que o backend devolve em /auth/me e /auth/google.
export type User = {
  id: number
  name: string
  email: string
}

// Tipo para o contexto de autenticação, importante para usar atributos e métodos
export type AuthContextType = {
  user: User | null
  // Verdadeiro apenas enquanto a sessão inicial é recuperada a partir do cookie.
  loading: boolean
  isAuthenticated: boolean
  // Recebe o ID token do Google devolvido pelo <GoogleLogin>.
  signInWithGoogle: (credential: string) => Promise<void>
  signOut: () => void
}

// Importante criar o tipo das propriedades do provider
// É um children de um nó do react, ou seja tag html com js
export type ProviderProps = {
  children: ReactNode
}
