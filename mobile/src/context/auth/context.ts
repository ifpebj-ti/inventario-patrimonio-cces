import { createContext } from 'react'
import { AuthContextType } from './types'

// Cria o contexto de autenticação
export const AuthContext = createContext({} as AuthContextType)