import axios from 'axios'
import { api } from './api'
import { AuthError } from '@/commons/exceptions/AuthError'
import { User } from '@/contexts/Auth/types'

type GoogleAuthResponse = {
  token: string
  user: User
}

// Troca o ID token do Google pelo JWT da aplicação.
//
// O backend responde 401 com o corpo em texto puro "Invalid Google token" tanto
// para token inválido quanto para domínio não permitido — é uma escolha
// deliberada dele não revelar qual verificação falhou. Por isso classificamos
// pelo status, nunca pelo texto do corpo.
export const googleSignInRequest = async (
  credential: string,
): Promise<GoogleAuthResponse> => {
  try {
    const response = await api.post<GoogleAuthResponse>('/auth/google', {
      credential,
    })

    return response.data
  } catch (error) {
    if (axios.isAxiosError(error)) {
      const status = error.response?.status

      if (status === 401 || status === 400) {
        throw new AuthError('unauthorized', 'Login com Google recusado')
      }

      if (!error.response || (status && status >= 500)) {
        throw new AuthError(
          'unavailable',
          'Serviço de autenticação indisponível',
        )
      }
    }

    throw new AuthError('unexpected', 'Falha inesperada ao autenticar')
  }
}

export const recoverUserInformation = async () => {
  return api.get<User>('/auth/me')
}
