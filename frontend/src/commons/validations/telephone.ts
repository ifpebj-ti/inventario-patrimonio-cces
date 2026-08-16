import { z } from 'zod'

// Validação com zod do telefone
export default z
  .string()
  .min(7, 'Deve ter no mínimo 7 caracteres')
  .regex(/^[A-Za-zÀ-ú0-9\s-]+$/u, 'Não utilize caracteres especiais')
  .transform((value) => value.replace(/\s+/g, ' ').trim())
  .optional()
  .or(z.literal('')) // Permite uma string vazia como uma opção válida
