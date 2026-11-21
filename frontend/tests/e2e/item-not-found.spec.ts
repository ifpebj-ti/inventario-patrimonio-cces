import { expect, test, type Page } from '@playwright/test'

// Situação do item (pendente, verificado, não localizado). O back-end autoriza de fato (testes em
// Java); aqui conferimos o que a interface mostra e envia.
const REASON = 'Procurado nas salas 101 e 102; devolvido ao almoxarifado.'

const inventory = {
  id: 1,
  name: 'Inventário A',
  description: '',
  createdAt: '13/10/2026',
  updatedAt: null,
  plannedEndDate: '2026-10-30',
  completedAt: null,
  status: 'EM_ANDAMENTO',
  sectorId: 1,
  sectorName: 'Setor A',
  itemCount: 3,
}

const baseItem = {
  description: 'Mesa',
  responsible: 'Maria',
  price: 'R$ 10,00',
  locale: 'Sala 1',
  observations: [] as unknown[],
}

const roles = {
  manager: {
    roleId: 1,
    roleName: 'Gestor de Setor',
    permissions: ['MANAGE_SECTOR'],
  },
  operator: {
    roleId: 2,
    roleName: 'Operador de Campo',
    permissions: ['MANAGE_ITEM'],
  },
  consult: { roleId: 3, roleName: 'Consulta', permissions: [] as string[] },
}

type Role = keyof typeof roles

type Calls = { notFound: unknown[] }

const setup = async (
  page: Page,
  role: Role,
  options: {
    status?: 'PENDENTE' | 'VALIDADO' | 'NAO_LOCALIZADO'
    notFoundResponse?: number
    items?: unknown[]
  } = {},
): Promise<Calls> => {
  const calls: Calls = { notFound: [] }
  let status = options.status ?? 'PENDENTE'
  const item = () => ({
    ...baseItem,
    id: 1,
    code: 'PAT-1',
    isValid: status === 'VALIDADO',
    validationStatus: status,
    observations:
      status === 'NAO_LOCALIZADO'
        ? [{ id: 9, content: `Não localizado: ${REASON}` }]
        : [],
  })
  await page.context().addCookies([
    {
      name: 'inventarium.token',
      value: 'test-session',
      domain: '127.0.0.1',
      path: '/',
    },
  ])
  await page.route('http://127.0.0.1:18080/**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    const send = (code: number, body: unknown) =>
      route.fulfill({
        status: code,
        contentType: 'application/json',
        headers: {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Headers': 'Authorization, Content-Type',
          'Access-Control-Allow-Methods':
            'GET, POST, PUT, PATCH, DELETE, OPTIONS',
        },
        body: code === 204 ? undefined : JSON.stringify(body),
      })
    if (request.method() === 'OPTIONS') return send(204, null)
    if (path === '/auth/me')
      return send(200, {
        id: 7,
        name: 'Pessoa',
        email: 'pessoa@example.com',
        administrator: false,
        sectorIds: [1],
        allocations: [{ sectorId: 1, ...roles[role] }],
      })
    if (path === '/inventory/user-inventories') return send(200, [inventory])
    if (path === '/inventory/1')
      return send(200, { inventory, canFinalize: role === 'manager' })
    if (path === '/inventory/inventory-items')
      return send(200, options.items ?? [])
    if (path === '/item/1/not-found') {
      calls.notFound.push(request.postDataJSON())
      if (options.notFoundResponse)
        return send(options.notFoundResponse, { message: 'Falha' })
      status = 'NAO_LOCALIZADO'
      return send(200, item())
    }
    if (path === '/item/1') return send(200, item())
    if (path === '/sectors')
      return send(200, [{ id: 1, name: 'Setor A', code: null, active: true }])
    return send(200, [])
  })
  return calls
}

const openDialog = async (page: Page) => {
  await page
    .getByRole('button', { name: 'Marcar como não localizado' })
    .first()
    .click()
  const dialog = page.getByRole('dialog', {
    name: 'Marcar como não localizado',
  })
  await expect(dialog).toBeVisible()
  return dialog
}

test.describe('marcar como não localizado', () => {
  test('operador marca o item pendente com o motivo e o item muda de situação', async ({
    page,
  }) => {
    const calls = await setup(page, 'operator')
    await page.goto('/inventory/1/item/1')
    await expect(page.getByText('Pendente', { exact: true })).toBeVisible()

    const dialog = await openDialog(page)
    await dialog.getByLabel('Motivo').fill(`  ${REASON}  `)
    await dialog
      .getByRole('button', { name: 'Marcar como não localizado' })
      .click()

    await expect(page.getByRole('dialog')).toHaveCount(0)
    // O motivo vai sem os espaços das pontas.
    expect(calls.notFound).toEqual([{ reason: REASON }])
    await expect(
      page.getByText('Não localizado', { exact: true }).first(),
    ).toBeVisible()
    await expect(
      page.getByText(/Item marcado como não localizado: o motivo está/),
    ).toBeVisible()
    await expect(page.getByText(`Não localizado: ${REASON}`)).toBeVisible()
    // Marcado, não há mais o que marcar.
    await expect(
      page.getByRole('button', { name: 'Marcar como não localizado' }),
    ).toHaveCount(0)
  })

  test('motivo só com espaços não é enviado', async ({ page }) => {
    const calls = await setup(page, 'operator')
    await page.goto('/inventory/1/item/1')

    const dialog = await openDialog(page)
    await dialog.getByLabel('Motivo').fill('     ')
    await dialog
      .getByRole('button', { name: 'Marcar como não localizado' })
      .click()

    await expect(
      dialog.getByText('Informe o motivo de o item não ter sido localizado.'),
    ).toBeVisible()
    expect(calls.notFound).toHaveLength(0)
  })

  test('cancelar fecha o diálogo sem enviar nada', async ({ page }) => {
    const calls = await setup(page, 'operator')
    await page.goto('/inventory/1/item/1')

    const dialog = await openDialog(page)
    await dialog.getByLabel('Motivo').fill('Qualquer')
    await dialog.getByRole('button', { name: 'Cancelar' }).click()

    await expect(page.getByRole('dialog')).toHaveCount(0)
    expect(calls.notFound).toHaveLength(0)
    await expect(page.getByText('Pendente', { exact: true })).toBeVisible()
  })

  test('erro 409 mostra a mensagem e mantém o item pendente', async ({
    page,
  }) => {
    await setup(page, 'operator', { notFoundResponse: 409 })
    await page.goto('/inventory/1/item/1')

    const dialog = await openDialog(page)
    await dialog.getByLabel('Motivo').fill(REASON)
    await dialog
      .getByRole('button', { name: 'Marcar como não localizado' })
      .click()

    await expect(dialog.getByRole('alert')).toContainText(
      'O inventário está finalizado ou o item já foi conferido.',
    )
    await expect(dialog).toBeVisible()
  })

  test('gestor também marca; consulta não vê a ação', async ({ page }) => {
    await setup(page, 'manager')
    await page.goto('/inventory/1/item/1')
    await expect(
      page.getByRole('button', { name: 'Marcar como não localizado' }),
    ).toBeVisible()
  })

  test('consulta não vê a ação de marcar', async ({ page }) => {
    await setup(page, 'consult')
    await page.goto('/inventory/1/item/1')
    await expect(page.getByText('Mesa').first()).toBeVisible()
    await expect(
      page.getByRole('button', { name: 'Marcar como não localizado' }),
    ).toHaveCount(0)
  })

  test('item já marcado não oferece a ação e trava a edição de dados', async ({
    page,
  }) => {
    await setup(page, 'manager', { status: 'NAO_LOCALIZADO' })
    await page.goto('/inventory/1/item/1')

    await expect(
      page.getByText('Não localizado', { exact: true }).first(),
    ).toBeVisible()
    await expect(
      page.getByRole('button', { name: 'Marcar como não localizado' }),
    ).toHaveCount(0)
    // Só observações: o gestor precisa desfazer a marcação antes de corrigir os dados.
    await expect(page.getByRole('button', { name: 'Editar item' })).toHaveCount(
      0,
    )
  })

  test('item verificado não oferece a ação de marcar', async ({ page }) => {
    await setup(page, 'manager', { status: 'VALIDADO' })
    await page.goto('/inventory/1/item/1')

    await expect(page.getByText('Verificado', { exact: true })).toBeVisible()
    await expect(
      page.getByRole('button', { name: 'Marcar como não localizado' }),
    ).toHaveCount(0)
  })
})

test('os contadores separam verificados, não localizados e pendentes', async ({
  page,
}) => {
  const items = [
    {
      ...baseItem,
      id: 1,
      code: 'A-1',
      isValid: true,
      validationStatus: 'VALIDADO',
    },
    {
      ...baseItem,
      id: 2,
      code: 'A-2',
      isValid: true,
      validationStatus: 'VALIDADO',
    },
    {
      ...baseItem,
      id: 3,
      code: 'A-3',
      isValid: false,
      validationStatus: 'NAO_LOCALIZADO',
    },
    {
      ...baseItem,
      id: 4,
      code: 'A-4',
      isValid: false,
      validationStatus: 'PENDENTE',
    },
    {
      ...baseItem,
      id: 5,
      code: 'A-5',
      isValid: false,
      validationStatus: 'PENDENTE',
    },
    {
      ...baseItem,
      id: 6,
      code: 'A-6',
      isValid: false,
      validationStatus: 'PENDENTE',
    },
  ]
  await setup(page, 'manager', { items })
  await page.goto('/inventory/1')

  const counter = (label: string) =>
    page
      .getByText(label, { exact: true })
      .locator('xpath=ancestor::div[contains(@class, "text-center")][1]')
  await expect(counter('Itens verificados')).toContainText('2')
  await expect(counter('Não localizados')).toContainText('1')
  await expect(counter('Itens pendentes')).toContainText('3')
})
