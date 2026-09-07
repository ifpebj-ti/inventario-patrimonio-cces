'use client'

import { useAuth } from '@/hooks/useAuth'
import { useRouter } from 'next/navigation'
import { JSX, useEffect } from 'react'

type Props = {
  children: JSX.Element
}

// Este componente é um "Guarda de Rota" (Route Guard).
// Sua responsabilidade é verificar se o usuário pode ou não acessar o conteúdo protegido (`children`).
function AuthenticatedOnlyFeature({ children }: Props): JSX.Element {
  const { isAuthenticated, loading } = useAuth()
  const router = useRouter()

  useEffect(() => {
    // Enquanto carrega ainda estamos recuperando a sessão pelo cookie:
    // redirecionar aqui derrubaria quem tem sessão válida.
    if (loading) return

    if (!isAuthenticated) {
      // replace e não push: o botão Voltar não deve reentrar na rota protegida.
      router.replace('/')
    }
  }, [isAuthenticated, loading, router])

  // Bloqueia a renderização em vez de apenas agendar o redirecionamento. Sem
  // isto os filhos montam e disparam suas requisições autenticadas sem token,
  // mostrando um flash da tela real para quem não está logado.
  if (loading || !isAuthenticated) {
    return (
      <div
        className="flex h-screen w-screen items-center justify-center"
        role="status"
        aria-live="polite"
      >
        <p className="text-blue-400 text-2xl">Carregando...</p>
      </div>
    )
  }

  return children
}

// Componente Wrapper para exportação, mantendo a estrutura do projeto limpa.
export default function AuthenticatedOnlyFeatureWrapper({
  children,
}: Props): JSX.Element {
  return <AuthenticatedOnlyFeature>{children}</AuthenticatedOnlyFeature>
}
