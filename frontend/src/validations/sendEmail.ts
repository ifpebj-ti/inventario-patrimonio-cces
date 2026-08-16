import { z } from 'zod'
import email from '@/commons/validations/email'

// Usamos zod para validar o envio do email criando um objeto zod
export default z.object({
  email,
  subject: z.string().nullable(),
  message: z.string().nullable(),
})
