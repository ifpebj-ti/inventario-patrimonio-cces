'use client'
import { Input } from '@/components/atoms/input'
import { Button } from '@/components/atoms/button'
import { useForm } from 'react-hook-form'
import { useAuth } from '@/hooks/useAuth'
import { zodResolver } from '@hookform/resolvers/zod'
import signUp from '@/validations/signUp'
import { z } from 'zod'
import Link from 'next/link'
import toast from 'react-hot-toast'

// Define o schema de validação e o tipo de dados do formulário a partir do Zod.
const signUpFormSchema = signUp
type FormDataProps = z.infer<typeof signUpFormSchema>

export const SignUpForm = () => {
  // Hook 'react-hook-form' para gerenciar o estado e a validação do formulário.
  const {
    register,
    setValue,
    handleSubmit,
    formState: { errors },
  } = useForm<FormDataProps>({
    mode: 'all',
    criteriaMode: 'all',
    resolver: zodResolver(signUpFormSchema),
  })

  // Obtém a função de cadastro do contexto de autenticação global.
  const { signUp } = useAuth()

  // Função chamada após a validação bem-sucedida do formulário.
  const handleSignUp = async (data: FormDataProps) => {
    try {
      console.log('Dados do formulário:', data)
      await signUp(data)
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
    } catch (error: any) {
      // Exibe um erro se o cadastro falhar (ex: e-mail já cadastrado).
      toast.error('Email já cadastrado')
      throw new Error('Erro ao cadastrar usuário', error)
    }
  }

  // Manipulador para o campo de telefone, que limpa a máscara antes de salvar no estado.
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const handlePhoneChange = (e: any) => {
    const value = e.target.value
    // Remove todos os caracteres que não são dígitos.
    const cleanValue = value.replace(/\D/g, '')
    // Atualiza o valor do campo 'telephone' no formulário com o dado limpo.
    setValue('telephone', cleanValue)
  }

  return (
    <div className="w-full h-full xl:w-2/3 bg-white flex justify-center items-center flex-col gap-4 xl:h-3/4 rounded-3xl shadow-2xl">
      <h2 className="text-2xl text-blue-400">Aproveite nossos serviços</h2>
      {/* O handleSubmit do react-hook-form garante que 'handleSignUp' só seja chamado se não houver erros de validação. */}
      <form
        className="w-full flex flex-col justify-center items-center gap-6"
        onSubmit={handleSubmit(handleSignUp)}
      >
        <Input
          {...register('name')}
          placeholder="Digite seu nome..."
          type="text"
          name="name"
          autoComplete="name"
          required
          id="name"
        />
        {errors.name && (
          <p className="text-red-500 text-[14px]">{errors.name.message}</p>
        )}
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
          autoComplete="new-password"
        />
        {errors.password && (
          <p className="text-red-500 text-[14px]">{errors.password.message}</p>
        )}
        <Input
          mask="(00) 0.0000-0000"
          placeholder="Digite seu telefone..."
          type="tel"
          id="phone"
          name="telephone"
          autoComplete="tel"
          // O onChange padrão do 'register' é sobrescrito para usar nossa função de limpeza de máscara.
          onChange={handlePhoneChange}
        />
        {errors.telephone && (
          <p className="text-red-500 text-[14px]">{errors.telephone.message}</p>
        )}
        <Button text="Cadastrar" type="submit" />
      </form>
      <p>
        Se já tem uma conta{' '}
        <Link
          href="/"
          className="text-blue-500 hover:text-blue-700 transition-colors"
        >
          Faça login.
        </Link>
      </p>
    </div>
  )
}

export default SignUpForm
