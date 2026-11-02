import { z } from 'zod'

// Sala de um setor. `itemCount` são os itens cadastrados nela; `deletable` é falso quando algum
// item (cadastrado ou encontrado) ou registro de auditoria aponta para ela.
export const roomSchema = z.object({
  id: z.number().int().positive(),
  name: z.string(),
  sectorId: z.number().int().positive(),
  itemCount: z.number().int().nonnegative(),
  deletable: z.boolean(),
})

export type Room = z.infer<typeof roomSchema>

export const ROOM_NAME_MAX_LENGTH = 100

export const roomNameSchema = z
  .string()
  .trim()
  .min(1, 'Informe o nome da sala.')
  .max(
    ROOM_NAME_MAX_LENGTH,
    `Use até ${ROOM_NAME_MAX_LENGTH} caracteres no nome.`,
  )
