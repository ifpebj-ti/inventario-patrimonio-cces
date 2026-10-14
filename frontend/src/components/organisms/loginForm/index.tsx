'use client'
import { homeRoute } from '@/lib/home-route'

import { Card as UiCard } from '@/components/ui/card'

import { Button as UiButton } from '@/components/ui/button'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { GoogleOAuthProvider, GoogleLogin } from '@react-oauth/google'
import { toast } from 'sonner'

import { useAuth } from '@/hooks/useAuth'
import { AuthError } from '@/commons/exceptions/AuthError'
import { GOOGLE_CLIENT_ID } from '@/commons/env'
import { PasswordLoginForm } from '@/components/organisms/passwordLoginForm'

const MESSAGES = {
  // O 401 do backend cobre token inválido E domínio não permitido, com a mesma
  // mensagem, de propósito. Como um ID token inválido é praticamente impossível
  // de um humano produzir — ele vem do Google segundos antes —, na prática este
  // caso significa "entrou com a conta Google errada", e o texto diz isso.
  unauthorized: 'Não foi possível entrar. Use sua conta institucional.',
  unavailable:
    'O serviço de autenticação está indisponível no momento. Tente novamente em alguns minutos.',
  rateLimited: 'Muitas tentativas. Tente novamente em alguns minutos.',
  unexpected: 'Falha inesperada ao entrar. Tente novamente.',
  google:
    'Não foi possível concluir o login com o Google. Verifique sua conexão e tente de novo.',
  misconfigured:
    'Login indisponível: a aplicação foi publicada sem o client id do Google.',
}

export const LoginForm = () => {
  const { signInWithGoogle, isAuthenticated, loading, user } = useAuth()
  const router = useRouter()

  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  // O login por usuário e senha é da conta de sistema; o Google segue como caminho
  // principal e o formulário só aparece quando o link é acionado.
  const [showPasswordForm, setShowPasswordForm] = useState(false)

  // Quem já tem sessão válida não precisa ver o botão de login.
  useEffect(() => {
    if (!loading && isAuthenticated)
      router.replace(homeRoute(user?.permissions))
  }, [loading, isAuthenticated, router, user])

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

  const googleLogin = !GOOGLE_CLIENT_ID ? (
    <p className="text-destructive text-[14px] text-center max-w-xs px-4">
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
          <p className="text-muted-foreground">Entrando...</p>
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
  )

  const togglePasswordForm = () => {
    setError(null)
    setShowPasswordForm((visible) => !visible)
  }

  return (
    <UiCard className="w-full h-full xl:w-2/3 justify-center items-center gap-6 xl:h-3/4 shadow-2xl">
      <h2 className="text-3xl text-primary">Boas-vindas</h2>

      <p className="text-center text-muted-foreground max-w-xs px-4">
        Entre com sua conta institucional para acessar o Inventarium.
      </p>

      {showPasswordForm ? <PasswordLoginForm /> : googleLogin}

      {error && (
        <p
          role="alert"
          className="text-destructive text-[14px] text-center max-w-xs px-4"
        >
          {error}
        </p>
      )}

      {/* Fora do ternário do Google de propósito: sem client id o aviso aparece
          e este link continua sendo a única forma de entrar. */}
      <UiButton
        variant="link"
        type="button"
        onClick={togglePasswordForm}
        aria-expanded={showPasswordForm}
        className="min-h-11 text-muted-foreground underline hover:text-foreground"
      >
        {showPasswordForm ? 'Voltar' : 'Entrar com usuário e senha'}
      </UiButton>
    </UiCard>
  )
}
