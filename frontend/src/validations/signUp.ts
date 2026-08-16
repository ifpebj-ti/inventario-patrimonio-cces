import { z } from 'zod'
import email from '@/commons/validations/email'
import telephone from '@/commons/validations/telephone'
import password from '@/commons/validations/password'

// Usamos zod para validar o cadastro do usuário a partir de um objeto zod
export default z.object({
  name: z
    .string({ required_error: 'Insira seu nome' })
    .min(1, 'O nome é obrigatório'),
  email,
  password,
  telephone,
})
