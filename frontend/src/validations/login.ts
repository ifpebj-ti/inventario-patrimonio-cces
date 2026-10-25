import { z } from 'zod'

// Login da conta de sistema. Sem tamanho mínimo: a regra dos 12 caracteres é do
// backend ao criar a conta, não ao entrar. O identificador não é e-mail (ex:
// "admin"), então não reaproveita commons/validations/email.
export default z.object({
  username: z
    .string({
      error: (issue) =>
        issue.input === undefined ? 'Informe o usuário' : undefined,
    })
    .trim()
    .min(1, 'Informe o usuário'),
  password: z
    .string({
      error: (issue) =>
        issue.input === undefined ? 'Informe a senha' : undefined,
    })
    .min(1, 'Informe a senha'),
})
