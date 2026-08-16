'use client'

import { useAuth } from '@/hooks/useAuth'
import { useRouter } from 'next/navigation'
import { JSX, useEffect } from 'react'
import toast from 'react-hot-toast'

type Props = {
  children: JSX.Element
}

// Este componente é um "Guarda de Rota" (Route Guard).
// Sua responsabilidade é verificar se o usuário pode ou não acessar o conteúdo protegido (`children`).
function AuthenticatedOnlyFeature({ children }: Props): JSX.Element {
  // Consome os dados do contexto de autenticação global.
  const { isAuthenticated, user, loading } = useAuth()
  const router = useRouter()

  // Este 'useEffect' reage a mudanças no estado de autenticação para proteger a rota.
  useEffect(() => {
    // Se o estado de autenticação ainda está sendo verificado, não faz nada e aguarda.
    // Isso é crucial para evitar redirecionamentos incorretos no carregamento inicial da página.
    if (loading) return

    // Se o carregamento terminou e o usuário NÃO está autenticado, redireciona para a home.
    if (!isAuthenticated) {
      // toast.error('Por favor, faça login para entrar no Inventarium.')
      router.push('/')
      return
    }

    // Se o usuário está autenticado, verifica se o e-mail dele foi confirmado.
    if (user) {
      if (!user.verified) {
        // Se não foi verificado, exibe um aviso e redireciona para a home.
        toast.error('Por favor, verifique seu email.', {
          id: 'userNotVerified',
        })

        router.push('/')
      }
    }
  }, [isAuthenticated, user, router, loading])

  // Se o usuário passou por todas as verificações, o conteúdo protegido (`children`) é renderizado.
  return children
}

// Componente Wrapper para exportação, mantendo a estrutura do projeto limpa.
export default function AuthenticatedOnlyFeatureWrapper({
  children,
}: Props): JSX.Element {
  return <AuthenticatedOnlyFeature>{children}</AuthenticatedOnlyFeature>
}
