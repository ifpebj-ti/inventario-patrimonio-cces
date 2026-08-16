'use client'
import { Input } from '@/components/atoms/input'
import { Button } from '@/components/atoms/button'
import { useForm } from 'react-hook-form'
import { useAuth } from '@/hooks/useAuth'
import { zodResolver } from '@hookform/resolvers/zod'
import signIn from '@/validations/signIn'
import { z } from 'zod'
import Link from 'next/link'
import toast from 'react-hot-toast'
import { VerifyEmailError } from '@/commons/exceptions/VerifyEmailError'
import { useRef, useState } from 'react'
import ReCAPTCHA from 'react-google-recaptcha'

// Associa o schema de validação Zod e infere o tipo de dados do formulário.
const signInFormSchema = signIn
type FormDataProps = z.infer<typeof signInFormSchema>

// Componente que renderiza o formulário de login.
export const LoginForm = () => {
  const [failedAttempts, setFailedAttempts] = useState(0)
  const [recaptchaToken, setRecaptchaToken] = useState<string | null>(null)
  const recaptchaRef = useRef<ReCAPTCHA>(null)

  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors },
  } = useForm<FormDataProps>({
    mode: 'all',
    criteriaMode: 'all',
    resolver: zodResolver(signInFormSchema),
  })

  const { signIn } = useAuth()

  const handleSignIn = async (data: FormDataProps) => {
    try {
      let token = recaptchaToken // Começa com o token capturado no modo visível (se existir)

      // 1. Lógica do reCAPTCHA
      if (failedAttempts < 3) {
        // MODO INVISÍVEL: Executa o reCAPTCHA via código
        token = await recaptchaRef.current!.executeAsync()
        recaptchaRef.current!.reset()
      }
      // Se failedAttempts >= 3 (MODO VISÍVEL), confiamos no 'recaptchaToken'
      // que foi setado pelo 'handleRecaptchaChange' (interação do usuário).
      // Não chamamos executeAsync() aqui.

      if (!token) {
        toast.error('Por favor, confirme que você não é um robô.')
        return
      }

      // 2. Continuação do Login
      data.recaptchaToken = token
      setValue('recaptchaToken', token)

      await signIn(data)

      toast.success('Você foi logado com sucesso no Inventarium!')
      setFailedAttempts(0)
      setRecaptchaToken(null)
    } catch (error) {
      // 3. Em caso de falha, incrementa a contagem e reseta o reCAPTCHA
      setFailedAttempts((prev) => prev + 1)

      // IMPORTANTE: Zera o token no estado e no formulário para forçar uma nova captura
      setRecaptchaToken(null)
      setValue('recaptchaToken', '')
      recaptchaRef.current!.reset() // Limpa o widget para a próxima tentativa

      if (error instanceof VerifyEmailError) {
        toast.error('Verifique seu email.')
        return
      }
      toast.error('Verifique suas credenciais.')
    }
  }

  const recaptchaSize = failedAttempts >= 3 ? 'normal' : 'invisible'

  // 🔹 Captura o token quando o usuário resolve manualmente o reCAPTCHA visível
  const handleRecaptchaChange = (value: string | null) => {
    setRecaptchaToken(value)
    setValue('recaptchaToken', value || '')
  }

  return (
    <div className="w-full h-full xl:w-2/3 bg-white flex justify-center items-center flex-col gap-6 xl:h-3/4 rounded-3xl shadow-2xl">
      <h2 className="text-3xl text-blue-400">Seja Bem-vindo</h2>
      <form
        className="w-full flex flex-col justify-center items-center gap-4"
        onSubmit={handleSubmit(handleSignIn)}
      >
        <Input
          {...register('email')}
          placeholder="Digite seu email..."
          type="email"
          name="email"
          autoComplete="email"
          required
          id="email-address"
        />
        {errors.email && (
          <p className="text-red-500 text-[14px]">{errors.email.message}</p>
        )}

        <Input
          {...register('password')}
          placeholder="Digite sua senha..."
          type="password"
          id="password"
          name="password"
          required
          autoComplete="current-password"
        />
        {errors.password && (
          <p className="text-red-500 text-[14px]">{errors.password.message}</p>
        )}

        {/* Componente do Google reCAPTCHA */}
        <ReCAPTCHA
          key={recaptchaSize} // força remontagem quando muda de modo
          ref={recaptchaRef}
          sitekey="6Lff_AAsAAAAANm-DdmxG9Y6tf_Rp7MiHPq6HZVD"
          size={recaptchaSize}
          onChange={handleRecaptchaChange}
        />
        {errors.recaptchaToken && (
          <p className="text-red-500 text-[14px]">
            {errors.recaptchaToken.message}
          </p>
        )}

        <p className="text-center">
          Ainda não tem uma conta? <br />
          <Link
            href="/sign-up"
            className="text-blue-500 hover:text-blue-700 transition-colors"
          >
            Cadastre-se
          </Link>
        </p>

        <Button text="Realizar Login" type="submit" />
        <p>
          <Link
            href="auth/reset-password"
            className="text-blue-500 hover:text-blue-700 transition-colors"
          >
            Esqueceu a senha?
          </Link>
        </p>
      </form>
    </div>
  )
}
