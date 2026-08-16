import { z } from 'zod'

// Usamos o zod para fazer uma validacao nos cadastros de inventários
export default z.object({
  name: z
    .string({ required_error: 'Insira o nome do inventário' })
    .min(1, 'O nome é obrigatório'),
  description: z.string().nullable(),
})
