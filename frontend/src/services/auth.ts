import axios from 'axios'
import { api } from './api'
import { AuthError } from '@/commons/exceptions/AuthError'
import { User } from '@/contexts/Auth/types'

type AuthResponse = {
  token: string
  user: User
}

// O backend responde 401 com corpo em texto puro e a mesma mensagem para todo
// tipo de recusa — é uma escolha deliberada dele não revelar qual verificação
// falhou. Por isso classificamos pelo status, nunca pelo texto do corpo.
const toAuthError = (
  error: unknown,
  unauthorizedMessage: string,
): AuthError => {
  if (axios.isAxiosError(error)) {
    const status = error.response?.status

    if (status === 401 || status === 400) {
      return new AuthError('unauthorized', unauthorizedMessage)
    }

    if (status === 429) {
      const retryAfter = Number(error.response?.headers?.['retry-after'])

      return new AuthError(
        'rateLimited',
        'Muitas tentativas de login',
        Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : undefined,
      )
    }

    if (!error.response || (status && status >= 500)) {
      return new AuthError(
        'unavailable',
        'Serviço de autenticação indisponível',
      )
    }
  }

  return new AuthError('unexpected', 'Falha inesperada ao autenticar')
}

// Troca o ID token do Google pelo JWT da aplicação.
//
// O 401 cobre token inválido E domínio não permitido ("Invalid Google token").
export const googleSignInRequest = async (
  credential: string,
): Promise<AuthResponse> => {
  try {
    const response = await api.post<AuthResponse>('/auth/google', {
      credential,
    })

    return response.data
  } catch (error) {
    throw toAuthError(error, 'Login com Google recusado')
  }
}

// Login da conta de sistema (usuário e senha, sem Google). Devolve o mesmo
// { token, user } do login Google.
//
// O 401 ("Invalid credentials") cobre usuário inexistente, senha errada e login
// por senha desligado no backend (sem SYSTEM_ADMIN_PASSWORD).
export const passwordSignInRequest = async (
  username: string,
  password: string,
): Promise<AuthResponse> => {
  try {
    const response = await api.post<AuthResponse>('/auth/login', {
      username,
      password,
    })

    return response.data
  } catch (error) {
    throw toAuthError(error, 'Usuário ou senha incorretos')
  }
}

export const recoverUserInformation = async () => {
  return api.get<User>('/auth/me')
}
