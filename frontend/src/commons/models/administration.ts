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

// Cargo: conjunto de permissões que vale no setor em que o usuário está alocado.
export const roleSchema = z.object({
  id: z.number().int().positive(),
  name: z.string(),
  description: z.string().nullable(),
  permissionIds: z.array(z.number().int().positive()),
  // Tem MANAGE_SECTOR: o gestor não atribui cargos de gestão.
  management: z.boolean().default(false),
  // Em quantas alocações e para quantos usuários o cargo vale.
  allocationCount: z.number().int().nonnegative().default(0),
  userCount: z.number().int().nonnegative().default(0),
  // Sem alocação e sem permissão: só nesse caso o admin pode excluir o cargo.
  deletable: z.boolean().default(false),
  createdAt: z.string().nullable().optional(),
  updatedAt: z.string().nullable().optional(),
})

// Alocação de um usuário em um setor, com o cargo dele nesse setor.
export const allocationSchema = z.object({
  sectorId: z.number().int().positive(),
  roleId: z.number().int().positive(),
  roleName: z.string(),
  permissions: z.array(z.string()).default([]),
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
  allocations: z.array(allocationSchema).default([]),
  administrator: z.boolean().default(false),
  active: z.boolean().default(true),
})

export type Sector = z.infer<typeof sectorSchema>
export type Role = z.infer<typeof roleSchema>
export type Allocation = z.infer<typeof allocationSchema>
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

export const roleFormSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, 'Informe o nome do cargo.')
    .max(100, 'Use até 100 caracteres no nome.'),
  description: z.string().max(255, 'Use até 255 caracteres na descrição.'),
  permissionIds: z.array(z.number().int().positive()),
})
