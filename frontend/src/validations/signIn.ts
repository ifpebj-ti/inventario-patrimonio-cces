import email from '@/commons/validations/email'
import { z } from 'zod'

// usamos zod para validar o login a partir de um objeto zod
export default z.object({
  email,
  password: z.string(),
  recaptchaToken: z.string().optional(),
})
