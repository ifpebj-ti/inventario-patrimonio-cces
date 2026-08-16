import axios from 'axios'
import { parseCookies } from 'nookies'

// criando porta da api com axios
// basicamente vai permitir o frontend fazer requisicoes ao backend
export const api = axios.create({
  baseURL: `http://localhost:8080`, // URL base do backend, esta hospedada na porta 8080
  headers: {
    'Content-Type': 'application/json', // indica que vai comunicar via arquivos json
  },
})

// Serve para garantir que seja enviado o token mais recente
// Interceptor de requisição para inserir token sempre que houver
api.interceptors.request.use((config) => {
  const { 'inventarium.token': token } = parseCookies()

  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`
  }

  return config
})

// interceptor sempre vai rodar quando fizer a rquisicao a fim de verificar
// se o usuario nao esta autorizado, se nao estiver retorna erro no console
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status

    if (status === 401) {
      console.warn('Usuário não autorizado.')
    }

    // const message =
    // error.response?.data?.message || `Erro inesperado. código ${status}`

    return Promise.reject(error) // retorna erro para ser tratado posteriormente no código
  },
)
