import { z } from 'zod'

// Validação para nome com zod
export const nameSchema = z
  .string({ required_error: 'O nome é obrigatório' })
  .min(1, 'O nome é obrigatório')
  .transform((value) => value.trim()) // Remove espaços em branco do início e fim
  .refine((value) => value.length > 0, 'O nome é obrigatório') // Garante que, após o trim, ainda haja conteúdo
