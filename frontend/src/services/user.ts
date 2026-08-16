import { api } from './api'

// Tipo para cadastro de item
export type SignUpRequestData = {
  name: string
  email: string
  password: string
  phone?: string
}

// Requisição para cadastro de usuário
export const signUpRequest = async (data: SignUpRequestData) => {
  try {
    const response = await api.post('/new-user', data)
    return response.data
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
  } catch (error: any) {
    throw new Error('Erro ao cadastrar usuário', error)
  }
}
