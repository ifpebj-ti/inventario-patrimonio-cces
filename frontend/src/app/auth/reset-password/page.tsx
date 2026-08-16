'use client'
import email from '@/commons/validations/email'
import { Button } from '@/components/atoms/button'
import Input from '@/components/atoms/input'
import { askPasswordResetRequest } from '@/services/auth'
import { zodResolver } from '@hookform/resolvers/zod'
import { useRouter } from 'next/navigation'
import { useForm } from 'react-hook-form'
import toast from 'react-hot-toast'
import { IoIosArrowBack } from 'react-icons/io'
import { z } from 'zod'

// Define o schema de validação e o tipo de dados do formulário usando Zod.
const askResetPasswordFormSchema = z.object({ email })
type FormDataProps = z.infer<typeof askResetPasswordFormSchema>

// Componente da página para solicitar a redefinição de senha.
export default function AskResetPassword() {
  // Hook do Next.js para controlar a navegação entre as páginas.
  const router = useRouter()

  // Hook 'react-hook-form' para gerenciar o estado, validação e submissão do formulário.
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormDataProps>({
    mode: 'all',
    criteriaMode: 'all',
    resolver: zodResolver(askResetPasswordFormSchema),
  })

  // Função chamada quando o formulário é submetido com um e-mail válido.
  const handleAskResetPassword = async ({ email }: FormDataProps) => {
    // Chama a função de serviço que envia a requisição para o backend.
    const response = await askPasswordResetRequest(email)
    // Verifica o status da resposta da API para decidir qual notificação exibir.
    if (response.status !== 200) {
      toast.error('Verifique se digitou o email corretamente.', {
        id: 'errorResetPassword',
      })
      return
    }
    toast.success('Email enviado com sucesso, verifique sua caixa de email.', {
      id: 'successResetPassword',
    })
  }

  return (
    // Container principal que centraliza o formulário na tela.
    <div className=" bg-white flex justify-center items-center flex-col w-screen h-screen">
      {/* O handleSubmit do react-hook-form envolve a função 'handleAskResetPassword',
          garantindo que ela só seja chamada se a validação do Zod passar. */}
      <form
        className="w-full max-w-md md:h-1/2 flex flex-col justify-center items-center gap-8 rounded-3xl shadow-2xl h-full"
        onSubmit={handleSubmit(handleAskResetPassword)}
      >
        {/* Cabeçalho do formulário com um botão para voltar. */}
        <span className="flex flex-row justify-center items-center gap-4">
          <IoIosArrowBack
            className="text-4xl text-blue-400 cursor-pointer"
            onClick={() => router.push('/')}
          />
          <h2 className="text-3xl text-blue-400">Busque por seu email</h2>
        </span>
        {/* Componente de Input para o campo de e-mail. */}
        <Input
          {...register('email')}
          placeholder="Digite seu email..."
          type="email"
          name="email"
          autoComplete="email"
          required
          id="email-address"
        />
        {/* Exibe a mensagem de erro para o campo de e-mail, se houver. */}
        {errors.email && (
          <p className=" text-red-500 text-[14px]">{errors.email.message}</p>
        )}
        <Button text="Buscar" type="submit" />
      </form>
    </div>
  )
}
