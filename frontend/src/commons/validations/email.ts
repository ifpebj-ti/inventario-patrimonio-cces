import { z } from 'zod'

// Vallidação por zod para email, com validação de email já pronta
export default z
  .string({ required_error: 'Insira seu e-mail' })
  .email('Por favor, insira um e-mail válido')
  .transform((v) => v.trim())
