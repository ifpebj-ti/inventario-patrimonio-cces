import { z } from 'zod'

const currentDate = new Date().toISOString().slice(0, 10)

// Usamos o zod para fazer uma validacao nos cadastros de inventários
export default z.object({
  name: z
    .string({ required_error: 'Insira o nome do inventário' })
    .min(1, 'O nome é obrigatório'),
  description: z.string().nullable(),
  plannedEndDate: z
    .string({ required_error: 'Informe a data prevista de finalização' })
    .regex(/^\d{4}-\d{2}-\d{2}$/, 'Informe uma data válida')
    .refine(
      (date) => date >= currentDate,
      'A data prevista não pode ser anterior à data de criação',
    ),
  sectorId: z.coerce
    .number({ required_error: 'Selecione o setor responsável' })
    .int()
    .positive('Selecione o setor responsável'),
})
