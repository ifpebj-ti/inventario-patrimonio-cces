import { z } from 'zod'
import { api } from './api'
import { AdministrationError } from './administration'
import { roomSchema } from '@/commons/models/room'

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

// Salas do setor: quem lê o setor lista; só administrador e gestor do setor alteram.
export const listSectorRooms = async (sectorId: number, signal?: AbortSignal) =>
  parse(
    z.array(roomSchema),
    (await api.get(`/sectors/${sectorId}/rooms`, { signal })).data,
  )

export const createRoom = async (sectorId: number, name: string) =>
  parse(
    roomSchema,
    (await api.post(`/sectors/${sectorId}/rooms`, { name: name.trim() })).data,
  )

export const renameRoom = async (roomId: number, name: string) =>
  parse(
    roomSchema,
    (await api.put(`/rooms/${roomId}`, { name: name.trim() })).data,
  )

export const deleteRoom = async (roomId: number) => {
  await api.delete(`/rooms/${roomId}`)
}
