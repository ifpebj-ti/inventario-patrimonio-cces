import { AuthContext } from '@/contexts/Auth/context'
import { useContext } from 'react'

// Hook para chamar o contexto de autenticação
// Serve basicamente para simplificar a chamada do contexto e capturar o erro da page não estar englobada no Provider
export const useAuth = () => {
  const context = useContext(AuthContext)

  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }

  return context
}
