import { z } from 'zod'

// Vallidação por zod para email, com validação de email já pronta
export default z
  .string({
    error: (issue) =>
      issue.input === undefined ? 'Insira seu e-mail' : undefined,
  })
  .email('Por favor, insira um e-mail válido')
  .transform((v) => v.trim())
