import { z } from 'zod'

export const sectorSchema = z.object({
  id: z.number().int().positive(),
  name: z.string(),
  code: z.string().nullable(),
  active: z.boolean(),
  // Sem usuário, inventário nem auditoria: só nesse caso o admin pode excluir o setor.
  deletable: z.boolean().default(false),
  createdAt: z.string().nullable().optional(),
  updatedAt: z.string().nullable().optional(),
})

export const profileSchema = z.object({
  id: z.number().int().positive(),
  name: z.string(),
  description: z.string().nullable(),
  permissionIds: z.array(z.number().int().positive()),
  createdAt: z.string().nullable().optional(),
  updatedAt: z.string().nullable().optional(),
})

export const permissionSchema = z.object({
  id: z.number().int().positive(),
  name: z.string(),
  description: z.string().nullable(),
})

export const managedUserSchema = z.object({
  id: z.number().int().positive(),
  name: z.string(),
  email: z.string(),
  sectorIds: z.array(z.number().int().positive()),
  profileId: z.number().int().positive().nullable(),
  active: z.boolean().default(true),
})

export type Sector = z.infer<typeof sectorSchema>
export type Profile = z.infer<typeof profileSchema>
export type Permission = z.infer<typeof permissionSchema>
export type ManagedUser = z.infer<typeof managedUserSchema>

export const sectorFormSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, 'Informe o nome do setor.')
    .max(255, 'Use até 255 caracteres no nome.'),
  code: z.string().max(100, 'Use até 100 caracteres no código.'),
})
