'use client'

import { useCallback, useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { setCookie, parseCookies, destroyCookie } from 'nookies'
import toast from 'react-hot-toast'

import { AuthContext } from './context'
import { ProviderProps, User } from './types'
import { googleSignInRequest, recoverUserInformation } from '@/services/auth'

export const TOKEN_COOKIE = 'inventarium.token'

// O path explícito é obrigatório nos dois lados. Sem ele o cookie é gravado com
// o path da página do login ('/') e o destroyCookie é emitido com o path da
// página onde o logout aconteceu ('/dashboard'), que não casa — e o cookie
// sobrevive ao logout.
const COOKIE_OPTIONS = {
  maxAge: 24 * 60 * 60, // o JWT do backend também expira em 24h
  path: '/',
  sameSite: 'lax' as const,
}

// Este componente gerencia todo o estado e lógica de autenticação da aplicação,
// disponibilizando-os para todos os componentes filhos através de um Contexto.
export const AuthProvider = ({ children }: ProviderProps) => {
  const [user, setUser] = useState<User | null>(null)
  // Controla o carregamento inicial e evita redirecionamentos indevidos.
  const [loading, setLoading] = useState(true)
  const router = useRouter()

  const isAuthenticated = !!user

  // Roda uma única vez para restaurar a sessão a partir do cookie.
  useEffect(() => {
    const { [TOKEN_COOKIE]: token } = parseCookies()

    if (!token) {
      setLoading(false)
      return
    }

    recoverUserInformation()
      .then((response) => setUser(response.data))
      .catch(() => {
        destroyCookie(undefined, TOKEN_COOKIE, { path: '/' })
        setUser(null)
      })
      .finally(() => setLoading(false))
  }, [])

  const signInWithGoogle = useCallback(
    async (credential: string) => {
      // Deixa o AuthError subir: quem chama decide a mensagem exibida.
      const { token, user } = await googleSignInRequest(credential)

      setCookie(undefined, TOKEN_COOKIE, token, COOKIE_OPTIONS)
      setUser(user)
      router.replace('/dashboard')
    },
    [router],
  )

  const signOut = useCallback(() => {
    // Limpa a sessão antes de navegar: o guard de rota reage a isAuthenticated
    // e tira o usuário das páginas protegidas por conta própria.
    destroyCookie(undefined, TOKEN_COOKIE, { path: '/' })
    setUser(null)
    router.replace('/')
    toast.success('Você saiu com sucesso do Inventarium.')
  }, [router])

  return (
    <AuthContext.Provider
      value={{ user, loading, isAuthenticated, signInWithGoogle, signOut }}
    >
      {children}
    </AuthContext.Provider>
  )
}
