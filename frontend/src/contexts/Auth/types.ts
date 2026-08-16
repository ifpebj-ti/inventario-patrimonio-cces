/* eslint-disable @typescript-eslint/no-explicit-any */
import { SignUpRequestData } from '@/services/user'
import { AxiosResponse } from 'axios'
import { ReactNode } from 'react'

// Tipo para login
export type SignInData = {
  email: string
  password: string
  recaptchaToken?: string
}

// Tipo para trocar senha
export type ResetPasswordData = {
  token: string
  password: string
}

// Tipo de usuário
export type User = {
  name: string
  email: string
  id: number
  verified: boolean
}

// Tipo para o contexto de autenticação, importante para usar atributos e métodos
export type AuthContextType = {
  user: User | null
  isAuthenticated: boolean
  loading: boolean
  signIn: (data: SignInData) => Promise<void>
  signOut: () => Promise<void>
  signUp: (data: SignUpRequestData) => Promise<void>
  verifyEmail: (token: string) => Promise<void>
  resetPassword: (data: ResetPasswordData) => Promise<AxiosResponse<any, any>>
}

// Importante criar o tipo das propriedades do provider
// É um children de um nó do react, ou seja tag html com js
export type ProviderProps = {
  children: ReactNode
}
