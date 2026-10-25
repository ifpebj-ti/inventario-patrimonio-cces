import test from 'node:test'
import assert from 'node:assert/strict'

import emailSchema from '../../src/commons/validations/email.ts'
import inventorySchema from '../../src/validations/inventory.ts'
import loginSchema from '../../src/validations/login.ts'

function messages(result: {
  success: boolean
  error?: { issues: { message: string }[] }
}) {
  return result.success ? [] : result.error!.issues.map((i) => i.message)
}

const futureDate = '2999-12-31'

test('e-mail: ausente, inválido e válido (com trim)', () => {
  assert.deepEqual(messages(emailSchema.safeParse(undefined)), [
    'Insira seu e-mail',
  ])
  assert.deepEqual(messages(emailSchema.safeParse('sem-arroba')), [
    'Por favor, insira um e-mail válido',
  ])
  assert.equal(emailSchema.parse('pessoa@ifpe.edu.br'), 'pessoa@ifpe.edu.br')
})

test('login: usuário e senha ausentes ou vazios pedem o preenchimento', () => {
  assert.ok(messages(loginSchema.safeParse({})).includes('Informe o usuário'))
  assert.ok(messages(loginSchema.safeParse({})).includes('Informe a senha'))
  assert.ok(
    messages(loginSchema.safeParse({ username: '', password: '' })).includes(
      'Informe a senha',
    ),
  )
})

test('inventário: aceita os dados válidos e converte o setor para número', () => {
  const parsed = inventorySchema.parse({
    name: 'Inventário 2026',
    description: null,
    plannedEndDate: futureDate,
    sectorId: '7',
  })
  assert.equal(parsed.sectorId, 7)
})

test('inventário: setor vazio, zero ou ausente pede a seleção do setor', () => {
  const base = { name: 'X', description: null, plannedEndDate: futureDate }
  for (const sectorId of ['', 0, undefined]) {
    assert.ok(
      messages(inventorySchema.safeParse({ ...base, sectorId })).some((m) =>
        m.startsWith('Selecione o setor responsável'),
      ),
      `sectorId=${String(sectorId)}`,
    )
  }
})

test('inventário: nome ausente, data no passado e formato inválido', () => {
  const ok = { description: null, sectorId: 1 }
  assert.ok(
    messages(
      inventorySchema.safeParse({ ...ok, plannedEndDate: futureDate }),
    ).includes('Insira o nome do inventário'),
  )
  assert.ok(
    messages(
      inventorySchema.safeParse({
        ...ok,
        name: 'X',
        plannedEndDate: '2000-01-01',
      }),
    ).includes('A data prevista não pode ser anterior à data de criação'),
  )
  assert.ok(
    messages(
      inventorySchema.safeParse({ ...ok, name: 'X', plannedEndDate: '31/12' }),
    ).includes('Informe uma data válida'),
  )
})
