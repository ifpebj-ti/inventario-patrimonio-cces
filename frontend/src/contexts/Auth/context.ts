'use client'
import { createContext } from 'react'
import { AuthContextType } from './types'

// Cria o contexto de autenticação.
// O valor inicial é null de propósito: com `{} as AuthContextType` a checagem
// no useAuth nunca dispara, e quem monta fora do provider recebe um
// "signInWithGoogle is not a function" em vez da mensagem de erro real.
export const AuthContext = createContext<AuthContextType | null>(null)
