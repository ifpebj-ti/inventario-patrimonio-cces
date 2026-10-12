import { z } from 'zod'

// Login da conta de sistema. Sem tamanho mínimo: a regra dos 12 caracteres é do
// backend ao criar a conta, não ao entrar. O identificador não é e-mail (ex:
// "admin"), então não reaproveita commons/validations/email.
export default z.object({
  username: z
    .string({ required_error: 'Informe o usuário' })
    .trim()
    .min(1, 'Informe o usuário'),
  password: z
    .string({ required_error: 'Informe a senha' })
    .min(1, 'Informe a senha'),
})
