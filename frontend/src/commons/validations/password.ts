import { z } from 'zod'

// Validação com zod para a senha
export default z
  .string({ required_error: 'Insira a senha' })
  .min(8, 'Deve conter no mínimo 8 caracteres')
  .max(32, 'Deve conter no máximo 32 caracteres')
  .regex(/^\S+$/g, 'Não pode conter espaços em branco')
  .regex(
    /^(?=.*\d)(?=.*[a-zA-Z]).{8,32}$/gm,
    'Deve conter ao menos uma letra e um número',
  )
