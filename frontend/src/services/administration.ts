import axios from 'axios'
import { z } from 'zod'
import { api } from './api'
import {
  managedUserSchema,
  permissionSchema,
  profileSchema,
  sectorSchema,
  type CreateUserRequest,
} from '@/commons/models/administration'

export class AdministrationError extends Error {
  constructor(
    readonly status: number | null,
    message: string,
  ) {
    super(message)
    this.name = 'AdministrationError'
  }
}

export const administrationFailure = (error: unknown, conflict?: string) => {
  if (error instanceof AdministrationError) return error
  if (axios.isAxiosError(error)) {
    const status = error.response?.status ?? null
    const messages: Record<number, string> = {
      400: 'Dados inválidos ou nome já cadastrado. Confira os campos e tente novamente.',
      401: 'Sua sessão não é mais válida. Entre novamente para continuar.',
      403: 'Você não tem permissão para realizar esta operação.',
      404: 'O recurso não está mais disponível. Atualize a lista.',
      405: 'Esta operação não está disponível na API.',
      409:
        conflict ||
        'A operação entra em conflito com o estado atual do recurso. Atualize a lista.',
    }
    return new AdministrationError(
      status,
      status === null
        ? 'Não foi possível conectar ao servidor. Verifique a conexão e tente novamente.'
        : messages[status] ||
            'O serviço está indisponível. Tente novamente mais tarde.',
    )
  }
  return new AdministrationError(
    null,
    'Não foi possível concluir a operação. Tente novamente.',
  )
}

const parse = <T extends z.ZodTypeAny>(
  schema: T,
  data: unknown,
): z.output<T> => {
  const result = schema.safeParse(data)
  if (!result.success)
    throw new AdministrationError(
      null,
      'O servidor retornou dados inesperados. Atualize a página ou tente novamente.',
    )
  return result.data
}

export const listSectors = async (signal?: AbortSignal) =>
  parse(z.array(sectorSchema), (await api.get('/sectors', { signal })).data)
export const listSectorUsers = async (id: number, signal?: AbortSignal) =>
  parse(
    z.array(managedUserSchema),
    (await api.get(`/sectors/${id}/users`, { signal })).data,
  )
export const saveSector = async (
  data: { name: string; code: string },
  id?: number,
) => {
  const body = { name: data.name.trim(), code: data.code || null }
  const response = id
    ? await api.put(`/sectors/${id}`, body)
    : await api.post('/sectors', body)
  return parse(sectorSchema, response.data)
}
export const setSectorActive = async (sectorId: number, active: boolean) =>
  parse(
    sectorSchema,
    (
      await api.patch(
        `/sectors/${sectorId}/${active ? 'activate' : 'deactivate'}`,
      )
    ).data,
  )

export const listProfiles = async (signal?: AbortSignal) =>
  parse(z.array(profileSchema), (await api.get('/profiles', { signal })).data)
export const listPermissions = async (signal?: AbortSignal) =>
  parse(
    z.array(permissionSchema),
    (await api.get('/permissions', { signal })).data,
  )

export const listUsers = async (profileId?: number, signal?: AbortSignal) =>
  parse(
    z.array(managedUserSchema),
    (await api.get('/users', { params: { profileId }, signal })).data,
  )
export const createUser = async (data: CreateUserRequest) => {
  try {
    return parse(managedUserSchema, (await api.post('/users', data)).data)
  } catch (error) {
    const problem = administrationFailure(
      error,
      'Já existe um usuário com esse e-mail. Busque-o na lista para gerenciar o acesso.',
    )
    if (problem.status === 400)
      throw new AdministrationError(
        400,
        'Confira o e-mail, o perfil e os setores. O perfil e os setores selecionados precisam estar disponíveis.',
      )
    throw problem
  }
}
export const assignUserProfile = async (
  userId: number,
  profileId: number | null,
) =>
  parse(
    managedUserSchema,
    (await api.patch(`/users/${userId}/profile`, { profileId })).data,
  )
// Desativar impede o acesso mas preserva o histórico; excluir remove a conta e os vínculos.
export const setUserActive = async (userId: number, active: boolean) =>
  parse(
    managedUserSchema,
    (await api.patch(`/users/${userId}/${active ? 'activate' : 'deactivate'}`))
      .data,
  )
export const deleteUser = async (userId: number) => {
  try {
    await api.delete(`/users/${userId}`)
  } catch (error) {
    throw administrationFailure(
      error,
      'Este usuário criou inventários e não pode ser excluído. Desative a conta para preservar o histórico.',
    )
  }
}
export const setUserSector = async (
  userId: number,
  sectorId: number,
  allocated: boolean,
) => {
  const url = `/users/${userId}/sectors/${sectorId}`
  return parse(
    managedUserSchema,
    (allocated ? await api.post(url) : await api.delete(url)).data,
  )
}
