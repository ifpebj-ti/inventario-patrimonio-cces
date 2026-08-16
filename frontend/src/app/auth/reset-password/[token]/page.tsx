'use client'
import password from '@/commons/validations/password'
import { Button } from '@/components/atoms/button'
import Input from '@/components/atoms/input'
import { useAuth } from '@/hooks/useAuth'
import { zodResolver } from '@hookform/resolvers/zod'
import { useParams, useRouter } from 'next/navigation'
import { useForm } from 'react-hook-form'
import toast from 'react-hot-toast'
import { z } from 'zod'

// Schema de validação com Zod para o formulário de redefinição de senha.
const resetPasswordFormSchema = z
  .object({
    // Utiliza uma validação de senha importada.
    password,
    // Define o campo de confirmação de senha.
    passwordConfirmation: z.string({
      required_error: 'A confirmação de senha é obrigatória',
    }),
  })
  // O '.refine()' é uma validação customizada que verifica se os dois campos de senha são iguais.
  .refine((data) => data.password === data.passwordConfirmation, {
    message: 'As senhas não conferem',
    // 'path' define em qual campo a mensagem de erro deve ser exibida.
    path: ['passwordConfirmation'],
  })

// Extrai o tipo TypeScript a partir do schema Zod para garantir a segurança de tipos.
type resetPasswordFormProps = z.infer<typeof resetPasswordFormSchema>

// Componente da página onde o usuário define sua nova senha.
export default function ChangePassword() {
  // Hook do Next.js para extrair parâmetros dinâmicos da URL (neste caso, o token).
  const { token } = useParams()
  // Hook do Next.js para controlar a navegação.
  const router = useRouter()
  // Hook 'react-hook-form' para gerenciar o estado e a validação do formulário.
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<resetPasswordFormProps>({
    mode: 'all',
    criteriaMode: 'all',
    resolver: zodResolver(resetPasswordFormSchema),
  })
  // Obtém a função de redefinir senha do contexto de autenticação global.
  const { resetPassword } = useAuth()

  // Função chamada quando o formulário é submetido com dados válidos.
  const handleResetPassword = async ({
    password,
    // A confirmação é usada apenas para validação e pode ser ignorada aqui.
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    passwordConfirmation,
  }: resetPasswordFormProps) => {
    // Converte o token da URL (que pode ser um array de strings) para uma string simples.
    const stringToken = token!.toString()
    // Chama a função do contexto de autenticação, passando o token e a nova senha.
    const response = await resetPassword({ token: stringToken, password })
    // Verifica o status da resposta da API para dar feedback ao usuário.
    if (response.status !== 200) {
      toast.error('Você já trocou sua senha.', {
        id: 'errorResetPassword',
      })
      return
    }
    toast.success('Sua senha foi trocada com sucesso.', {
      id: 'successResetPassword',
    })
    // Redireciona o usuário para a página inicial após o sucesso.
    router.push('/')
  }

  return (
    // Container principal que centraliza o formulário na tela.
    <div className=" bg-white flex justify-center items-center flex-col w-screen h-screen">
      {/* O handleSubmit do react-hook-form envolve 'handleResetPassword',
          garantindo que ela só seja chamada se a validação do Zod passar. */}
      <form
        className="w-full max-w-md md:h-1/2 flex flex-col justify-center items-center gap-4 rounded-3xl shadow-2xl h-full"
        onSubmit={handleSubmit(handleResetPassword)}
      >
        <h2 className="text-3xl text-blue-400">Mude sua senha</h2>
        {/* Input para a nova senha, conectado ao formulário via 'register'. */}
        <Input
          {...register('password')}
          placeholder="Digite sua nova senha..."
          type="password"
          name="password"
          required
          id="password"
        />
        {/* Exibe a mensagem de erro para o campo 'password', se houver. */}
        {errors.password && (
          <p className=" text-red-500 text-[14px]">{errors.password.message}</p>
        )}
        {/* Input para a confirmação da nova senha. */}
        <Input
          {...register('passwordConfirmation')}
          placeholder="Confirme sua nova sua senha..."
          type="password"
          id="passwordConfirmation"
          name="passwordConfirmation"
          required
        />
        {/* Exibe a mensagem de erro para o campo de confirmação, se houver. */}
        {errors.passwordConfirmation && (
          <p className="text-red-500 text-sm mt-1">
            {errors.passwordConfirmation.message}
          </p>
        )}
        <Button text="Mudar senha" type="submit" />
      </form>
    </div>
  )
}
