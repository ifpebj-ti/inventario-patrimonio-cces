'use client'

import { useEffect, useState } from 'react'
import { AuthContext } from './context'
import {
  recoverUserInformation,
  resetPasswordRequest,
  signInRequest,
  verifyUserRequest,
} from '@/services/auth'
import { ProviderProps, SignInData, User, ResetPasswordData } from './types'
import { setCookie, parseCookies, destroyCookie } from 'nookies'
import { useRouter } from 'next/navigation'
import { signUpRequest, SignUpRequestData } from '@/services/user'
import toast from 'react-hot-toast'
import { VerifyEmailError } from '@/commons/exceptions/VerifyEmailError'

// Este componente gerencia todo o estado e lógica de autenticação da aplicação,
// disponibilizando-os para todos os componentes filhos através de um Contexto.
export const AuthProvider = ({ children }: ProviderProps) => {
  // Estado para armazenar os dados do usuário logado.
  const [user, setUser] = useState<User | null>(null)
  // Estado para controlar o carregamento inicial e evitar redirecionamentos indevidos.
  const [loading, setLoading] = useState(true)
  const router = useRouter()
  // Booleano derivado do estado 'user' para facilitar as verificações.
  const isAuthenticated = !!user

  // Efeito que roda uma única vez para verificar se existe um token nos cookies
  // e tentar restaurar a sessão do usuário ao carregar a aplicação.
  useEffect(() => {
    const { 'inventarium.token': token } = parseCookies()

    if (token) {
      recoverUserInformation()
        .then((response) => {
          setUser(response.data)
        })
        .catch(() => {
          // Se o token for inválido, limpa o cookie e o estado local.
          destroyCookie(undefined, 'inventarium.token')
          setUser(null)
        })
        .finally(() => {
          // Garante que o estado de 'loading' termine, independentemente do resultado.
          setLoading(false)
        })
    } else {
      setLoading(false)
    }
  }, [])

  // Função que lida com o fluxo de login do usuário.
  const signIn = async ({
    email,
    password,
    recaptchaToken,
  }: SignInData): Promise<void> => {
    try {
      const { token, user } = await signInRequest({
        email,
        password,
        recaptchaToken,
      })

      console.log(token, user)

      // Salva o token JWT nos cookies para manter a sessão.
      setCookie(undefined, 'inventarium.token', token, {
        maxAge: 24 * 60 * 60 * 1, // Expira em 1 dia
      })

      // Atualiza o estado global e redireciona para o dashboard.
      setUser(user)
      router.push('/dashboard')
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } catch (error: any) {
      // Diferencia o erro de "email não verificado" de outros erros (ex: senha errada).
      if (error instanceof VerifyEmailError) {
        throw new VerifyEmailError('Email não verificado')
      }
      // Relança o erro para que o formulário de login possa tratá-lo.
      throw new Error('Erro ao logar no sistema do Coletare', error)
    }
  }

  // Função para deslogar o usuário.
  const signOut = async () => {
    router.push('/')
    toast.success('Você saiu com sucesso do Inventarium.')
    // Limpa o token dos cookies e o estado do usuário.
    destroyCookie(undefined, 'inventarium.token')
    setUser(null)
  }

  // Função para registrar um novo usuário.
  const signUp = async (data: SignUpRequestData) => {
    // Chama a API de cadastro e, em seguida, direciona o usuário para o fluxo de verificação de e-mail.
    await signUpRequest(data)
    toast.error('Verifique seu email.')
    router.push('/')
  }

  // Chama a API para validar o token de verificação de e-mail.
  const verifyEmail = async (token: string) => {
    await verifyUserRequest(token)
  }

  // Chama a API para efetivar a redefinição de senha.
  const resetPassword = async ({ token, password }: ResetPasswordData) => {
    return await resetPasswordRequest({ token, password })
  }

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated,
        loading,
        signIn,
        signOut,
        signUp,
        verifyEmail,
        resetPassword,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}
