'use client'

import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import toast from 'react-hot-toast'

import { Input } from '@/components/atoms/input'
import { Button } from '@/components/atoms/button'
import { useAuth } from '@/hooks/useAuth'
import { AuthError, AuthErrorKind } from '@/commons/exceptions/AuthError'
import loginSchema from '@/validations/login'

type LoginFormData = z.infer<typeof loginSchema>

const MESSAGES: Record<AuthErrorKind, string> = {
  // O 401 do backend cobre usuário inexistente, senha errada e login por senha
  // desligado, com a mesma mensagem, de propósito: o front não distingue.
  unauthorized: 'Usuário ou senha incorretos.',
  unavailable:
    'O serviço de autenticação está indisponível no momento. Tente novamente em alguns minutos.',
  unexpected: 'Falha inesperada ao entrar. Tente novamente.',
}

// Login da conta de sistema (usuário e senha, sem Google). A sessão e o
// redirecionamento para o dashboard ficam por conta do AuthProvider.
export const PasswordLoginForm = () => {
  const { signInWithPassword } = useAuth()
  const [error, setError] = useState<string | null>(null)

  const {
    register,
    handleSubmit,
    resetField,
    formState: { errors, isSubmitting },
  } = useForm<LoginFormData>({
    resolver: zodResolver(loginSchema),
  })

  const onSubmit = async ({ username, password }: LoginFormData) => {
    setError(null)

    try {
      await signInWithPassword(username, password)
      toast.success('Bem-vindo ao Inventarium!')
    } catch (err) {
      const message =
        err instanceof AuthError ? MESSAGES[err.kind] : MESSAGES.unexpected

      // O erro fica inline além do toast, para poder ser relido depois que o
      // toast some sozinho.
      setError(message)
      toast.error(message)
      resetField('password')
    }
  }

  return (
    <form
      onSubmit={handleSubmit(onSubmit)}
      className="flex flex-col gap-4 w-full max-w-xs px-4"
      noValidate
    >
      <div>
        <label
          htmlFor="username"
          className="block text-sm font-medium text-slate-700 mb-1"
        >
          Usuário
        </label>
        <Input
          id="username"
          type="text"
          autoComplete="username"
          placeholder="Usuário"
          {...register('username')}
          width="w-full"
        />
        {errors.username && (
          <p className="text-red-500 text-sm mt-1">{errors.username.message}</p>
        )}
      </div>

      <div>
        <label
          htmlFor="password"
          className="block text-sm font-medium text-slate-700 mb-1"
        >
          Senha
        </label>
        <Input
          id="password"
          type="password"
          autoComplete="current-password"
          placeholder="Senha"
          {...register('password')}
          width="w-full"
        />
        {errors.password && (
          <p className="text-red-500 text-sm mt-1">{errors.password.message}</p>
        )}
      </div>

      {error && (
        <p role="alert" className="text-red-500 text-[14px] text-center">
          {error}
        </p>
      )}

      <Button
        type="submit"
        text={isSubmitting ? 'Entrando...' : 'Entrar'}
        disabled={isSubmitting}
        width="w-full"
      />
    </form>
  )
}
