import { z } from 'zod'

const currentDate = new Date().toISOString().slice(0, 10)

// Usamos o zod para fazer uma validacao nos cadastros de inventários
export default z.object({
  name: z
    .string({
      error: (issue) =>
        issue.input === undefined ? 'Insira o nome do inventário' : undefined,
    })
    .min(1, 'O nome é obrigatório'),
  description: z.string().nullable(),
  plannedEndDate: z
    .string({
      error: (issue) =>
        issue.input === undefined
          ? 'Informe a data prevista de finalização'
          : undefined,
    })
    .regex(/^\d{4}-\d{2}-\d{2}$/, 'Informe uma data válida')
    .refine(
      (date) => date >= currentDate,
      'A data prevista não pode ser anterior à data de criação',
    ),
  sectorId: z.coerce
    .number<number>({ error: 'Selecione o setor responsável' })
    .int()
    .positive('Selecione o setor responsável'),
})
