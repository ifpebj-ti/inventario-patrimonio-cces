'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { GoogleOAuthProvider, GoogleLogin } from '@react-oauth/google'
import toast from 'react-hot-toast'

import { useAuth } from '@/hooks/useAuth'
import { AuthError } from '@/commons/exceptions/AuthError'
import { GOOGLE_CLIENT_ID, INSTITUTIONAL_DOMAIN_LABEL } from '@/commons/env'

const MESSAGES = {
  // O 401 do backend cobre token inválido E domínio não permitido, com a mesma
  // mensagem, de propósito. Como um ID token inválido é praticamente impossível
  // de um humano produzir — ele vem do Google segundos antes —, na prática este
  // caso significa "entrou com a conta Google errada", e o texto diz isso.
  unauthorized: `Não foi possível entrar. Use sua conta institucional ${INSTITUTIONAL_DOMAIN_LABEL}. Contas pessoais do Google não têm acesso ao Inventarium.`,
  unavailable:
    'O serviço de autenticação está indisponível no momento. Tente novamente em alguns minutos.',
  unexpected: 'Falha inesperada ao entrar. Tente novamente.',
  google:
    'Não foi possível concluir o login com o Google. Verifique sua conexão e tente de novo.',
  misconfigured:
    'Login indisponível: a aplicação foi publicada sem o client id do Google.',
}

export const LoginForm = () => {
  const { signInWithGoogle, isAuthenticated, loading } = useAuth()
  const router = useRouter()

  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Quem já tem sessão válida não precisa ver o botão de login.
  useEffect(() => {
    if (!loading && isAuthenticated) router.replace('/dashboard')
  }, [loading, isAuthenticated, router])

  const handleCredential = async (credential?: string) => {
    if (!credential) {
      setError(MESSAGES.google)
      return
    }

    setError(null)
    setSubmitting(true)

    try {
      await signInWithGoogle(credential)
      toast.success('Bem-vindo ao Inventarium!')
    } catch (err) {
      const message =
        err instanceof AuthError ? MESSAGES[err.kind] : MESSAGES.unexpected

      // O erro fica inline além do toast: quem foi barrado por domínio precisa
      // poder reler o motivo depois que o toast some sozinho.
      setError(message)
      toast.error(message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="w-full h-full xl:w-2/3 bg-white flex justify-center items-center flex-col gap-6 xl:h-3/4 rounded-3xl shadow-2xl">
      <h2 className="text-3xl text-blue-400">Seja Bem-vindo</h2>

      <p className="text-center text-gray-600 max-w-xs px-4">
        Entre com sua conta institucional do Google para acessar o Inventarium.
      </p>

      {!GOOGLE_CLIENT_ID ? (
        <p className="text-red-500 text-[14px] text-center max-w-xs px-4">
          {MESSAGES.misconfigured}
        </p>
      ) : (
        <GoogleOAuthProvider
          clientId={GOOGLE_CLIENT_ID}
          locale="pt-BR"
          onScriptLoadError={() => setError(MESSAGES.google)}
        >
          {/* min-h evita o card pular quando o botão vira o texto de progresso */}
          <div className="flex flex-col items-center gap-4 min-h-[44px]">
            {submitting ? (
              <p className="text-gray-500">Entrando...</p>
            ) : (
              <GoogleLogin
                onSuccess={(response) => handleCredential(response.credential)}
                onError={() => setError(MESSAGES.google)}
                text="signin_with"
                shape="pill"
                size="large"
                width={280}
              />
            )}
          </div>
        </GoogleOAuthProvider>
      )}

      {error && (
        <p
          role="alert"
          className="text-red-500 text-[14px] text-center max-w-xs px-4"
        >
          {error}
        </p>
      )}

      {/* Avisar antes da tentativa evita a maior parte da confusão. */}
      <p className="text-center text-xs text-gray-400 max-w-xs px-4">
        Apenas contas {INSTITUTIONAL_DOMAIN_LABEL} podem acessar o sistema.
      </p>
    </div>
  )
}
