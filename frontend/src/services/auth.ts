import { VerifyEmailError } from '@/commons/exceptions/VerifyEmailError'
import { api } from './api'
import { AxiosError } from 'axios'

// tipo de dado para login
type SignInRequestData = {
  email: string
  password: string
  recaptchaToken: string
}

// tipo de dado para trocar senha
type ResetPasswordRequestData = {
  token: string
  password: string
}

export const signInRequest = async (data: SignInRequestData) => {
  try {
    // tenta realizar login corretamente
    const response = await api.post('/auth/login', data)
    console.log(response)
    return response.data
  } catch (error) {
    // caso der erro verifica se foi de email e joga para tratamento posterior
    const axiosError = error as AxiosError
    const errorMessage = (axiosError.response?.data as string)
      ?.trim()
      .toLowerCase()
    console.log(errorMessage, typeof axiosError.response?.data)
    if (errorMessage === 'email not verified') {
      console.log('teste')
      throw new VerifyEmailError('Email não verificado')
    }
    // se for outro erro, como credenciais erradas envia para tratamento posterior
    throw new Error('Falha ao realizar o login')
  }
}

// requisicao para renovar dados do usuario atraves do token passado no interceptor
export const recoverUserInformation = async () => {
  const response = await api.get('/auth/me')
  return response
}

// requisicao para verificar usuario passando token de verificacao
export const verifyUserRequest = async (token: string) => {
  const response = await api.get(`auth/verify/${token}`)
  return response
}

// reqsuisicao para enviar email de troca de senha
export const askPasswordResetRequest = async (email: string) => {
  const response = await api.get('/auth/reset-password', {
    params: {
      email,
    },
  })
  console.log(response)
  return response
}

// requisicao para mudar a senha atraves de token de confirmacao e senha
export const resetPasswordRequest = async ({
  token,
  password,
}: ResetPasswordRequestData) => {
  const response = api.put(`auth/reset-password/${token}`, { password })
  return response
}
