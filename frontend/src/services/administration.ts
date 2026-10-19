import axios from 'axios'
import { z } from 'zod'
import { api } from './api'
import {
  assignableUserSchema,
  managedUserSchema,
  permissionSchema,
  roleSchema,
  sectorSchema,
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
// Usuários ativos que ainda não estão no setor (só administrador e gestor do setor).
export const listAssignableUsers = async (
  sectorId: number,
  signal?: AbortSignal,
) =>
  parse(
    z.array(assignableUserSchema),
    (await api.get(`/sectors/${sectorId}/assignable-users`, { signal })).data,
  )
// Aloca vários usuários no setor com o mesmo cargo, tudo ou nada; devolve os membros do setor.
export const allocateUsers = async (
  sectorId: number,
  userIds: number[],
  roleId: number,
) =>
  parse(
    z.array(managedUserSchema),
    (await api.put(`/sectors/${sectorId}/members`, { userIds, roleId })).data,
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

export const deleteSector = async (sectorId: number) => {
  await api.delete(`/sectors/${sectorId}`)
}

export const listRoles = async (signal?: AbortSignal) =>
  parse(z.array(roleSchema), (await api.get('/roles', { signal })).data)
export const saveRole = async (
  data: { name: string; description: string; permissionIds: number[] },
  id?: number,
) => {
  const body = {
    name: data.name.trim(),
    description: data.description.trim() || null,
    permissionIds: data.permissionIds,
  }
  const response = id
    ? await api.put(`/roles/${id}`, body)
    : await api.post('/roles', body)
  return parse(roleSchema, response.data)
}
export const deleteRole = async (roleId: number) => {
  await api.delete(`/roles/${roleId}`)
}
export const listPermissions = async (signal?: AbortSignal) =>
  parse(
    z.array(permissionSchema),
    (await api.get('/permissions', { signal })).data,
  )

export const listUsers = async (roleId?: number, signal?: AbortSignal) =>
  parse(
    z.array(managedUserSchema),
    (await api.get('/users', { params: { roleId }, signal })).data,
  )
// Concede ou retira o papel de administrador (só o administrador, e nunca na própria conta).
export const setUserAdministrator = async (
  userId: number,
  administrator: boolean,
) =>
  parse(
    managedUserSchema,
    (await api.patch(`/users/${userId}/administrator`, { administrator })).data,
  )
// Desativar impede o acesso mas preserva o histórico; só o administrador ativa ou desativa.
export const setUserActive = async (userId: number, active: boolean) =>
  parse(
    managedUserSchema,
    (await api.patch(`/users/${userId}/${active ? 'activate' : 'deactivate'}`))
      .data,
  )
// Aloca o usuário no setor com o cargo (ou troca o cargo dele nesse setor); roleId nulo desaloca.
export const setUserSector = async (
  userId: number,
  sectorId: number,
  roleId: number | null,
) => {
  const url = `/users/${userId}/sectors/${sectorId}`
  return parse(
    managedUserSchema,
    (roleId === null ? await api.delete(url) : await api.put(url, { roleId }))
      .data,
  )
}
