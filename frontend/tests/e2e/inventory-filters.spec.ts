import { expect, test, type Page } from '@playwright/test'
import { optionTexts, pickOption } from './select'

const inventories = [
  {
    id: 1,
    name: 'Biblioteca central',
    description: '',
    sectorId: 1,
    sectorName: 'Biblioteca',
    status: 'EM_ANDAMENTO',
    createdAt: '05/10/2026',
    updatedAt: '06/10/2026',
    plannedEndDate: '2026-10-20',
    completedAt: null,
    itemCount: 0,
  },
  {
    id: 2,
    name: 'Biblioteca histórica',
    description: '',
    sectorId: 2,
    sectorName: 'Patrimônio',
    status: 'CONCLUIDO',
    createdAt: '01/09/2026',
    updatedAt: '12/10/2026',
    plannedEndDate: '2026-10-25',
    completedAt: '2026-10-13T23:55:00',
    itemCount: 0,
  },
  {
    id: 3,
    name: 'Laboratório TI',
    description: '',
    sectorId: 2,
    sectorName: 'Patrimônio',
    status: 'VENCIDO',
    createdAt: '01/10/2026',
    updatedAt: null,
    plannedEndDate: '2026-10-01',
    completedAt: null,
    itemCount: 0,
  },
  {
    id: 4,
    name: 'Inventário antigo',
    description: '',
    sectorId: null,
    sectorName: null,
    status: 'EM_ANDAMENTO',
    createdAt: '01/01/2025',
    updatedAt: null,
    plannedEndDate: null,
    completedAt: null,
    itemCount: 0,
  },
]

const setup = async (page: Page, query = '', admin = false) => {
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
    const options = request.method() === 'OPTIONS'
    const body =
      path === '/auth/me'
        ? {
            id: 7,
            name: 'Admin',
            email: 'admin@example.com',
            administrator: admin,
            sectorIds: admin ? [] : [2, 3],
            allocations: admin
              ? []
              : [2, 3].map((sectorId) => ({
                  sectorId,
                  roleId: 1,
                  roleName: 'Gestor de Setor',
                  permissions: ['MANAGE_SECTOR'],
                })),
          }
        : path === '/inventory/user-inventories'
          ? inventories
          : path === '/sectors'
            ? [
                { id: 1, name: 'Biblioteca', code: null, active: true },
                { id: 2, name: 'Patrimônio', code: null, active: true },
                { id: 3, name: 'Engenharia', code: null, active: true },
              ]
            : []
    await route.fulfill({
      status: options ? 204 : 200,
      contentType: 'application/json',
      headers: {
        'Access-Control-Allow-Origin': '*',
        'Access-Control-Allow-Headers': 'Authorization, Content-Type',
        'Access-Control-Allow-Methods': 'GET, POST, PUT, OPTIONS',
      },
      body: options ? undefined : JSON.stringify(body),
    })
  })
  await page.goto(`/inventory${query}`)
  await expect(
    page.getByRole('region', { name: 'Filtros dos inventários' }),
  ).toBeVisible()
  await expect(page.getByLabel('Buscar inventários')).toBeHidden()
  await page.getByRole('button', { name: 'Expandir filtros' }).click()
}

test('filtros combinam nome, status, setor e ano de criação', async ({
  page,
}) => {
  await setup(page)
  await page.setViewportSize({ width: 1190, height: 750 })
  await page.screenshot({
    path: '.next/inventory-reference.png',
    fullPage: true,
  })
  await expect(page.getByPlaceholder('Filtrar...')).toHaveCount(0)
  await expect(
    page.getByRole('region', { name: 'Filtros dos inventários' }),
  ).toHaveCount(1)
  await page.getByLabel('Buscar inventários').fill('HISTORICA')
  await pickOption(page.getByLabel('Filtrar por status'), 'Finalizados')
  await pickOption(page.getByLabel('Filtrar por setor'), 'Patrimônio')
  await pickOption(page.getByLabel('Filtrar por ano'), '2026')
  await expect(
    page.getByText('1 de 4 inventários', { exact: true }),
  ).toBeVisible()
  await expect(
    page
      .getByRole('table')
      .getByRole('cell', { name: 'Biblioteca histórica', exact: true }),
  ).toBeVisible()
  while (await page.getByRole('button', { name: /^Remover filtro / }).count())
    await page
      .getByRole('button', { name: /^Remover filtro / })
      .first()
      .click()
  await expect(
    page.getByText('4 de 4 inventários', { exact: true }),
  ).toBeVisible()
  await expect(page.getByLabel('Buscar inventários')).toHaveValue('')
})

test('ano filtra pela criação e pode ser removido individualmente', async ({
  page,
}) => {
  await setup(page)
  await expect(page.getByLabel('Tipo de data')).toHaveCount(0)
  await expect(page.getByLabel('Data inicial')).toHaveCount(0)
  await expect(page.getByLabel('Data final')).toHaveCount(0)
  await pickOption(page.getByLabel('Filtrar por ano'), '2025')
  await expect(
    page.getByText('1 de 4 inventários', { exact: true }),
  ).toBeVisible()
  await expect(
    page
      .getByRole('table')
      .getByRole('cell', { name: 'Inventário antigo', exact: true }),
  ).toBeVisible()
  await page.getByRole('button', { name: 'Remover filtro Ano: 2025' }).click()
  await expect(
    page.getByText('4 de 4 inventários', { exact: true }),
  ).toBeVisible()
  await pickOption(page.getByLabel('Filtrar por ano'), '2026')
  await expect(
    page.getByText('3 de 4 inventários', { exact: true }),
  ).toBeVisible()
})
test('card recolhe filtros e tabela oferece paginação conforme a referência', async ({
  page,
}) => {
  await setup(page)
  await page.getByRole('button', { name: 'Recolher filtros' }).click()
  await expect(page.getByLabel('Buscar inventários')).toBeHidden()
  await expect(page.getByRole('table')).toBeVisible()
  await page.getByRole('button', { name: 'Expandir filtros' }).click()
  await expect(page.getByLabel('Buscar inventários')).toBeVisible()
  await expect(page.getByLabel('Itens por página')).toHaveCount(0)
  await expect(page.getByLabel('Página 1')).toBeVisible()
  await expect(
    page.getByRole('button', { name: 'Página anterior' }),
  ).toHaveCount(0)
  await expect(
    page.getByRole('button', { name: 'Próxima página' }),
  ).toHaveCount(0)
})

test('preserva o filtro vindo do Dashboard e ordena pela data real de criação', async ({
  page,
}) => {
  await setup(page, '?status=OPEN')
  await expect(page.getByLabel('Filtrar por status')).toContainText(
    'Em andamento',
  )
  await expect(
    page.getByText('2 de 4 inventários', { exact: true }),
  ).toBeVisible()
  while (await page.getByRole('button', { name: /^Remover filtro / }).count())
    await page
      .getByRole('button', { name: /^Remover filtro / })
      .first()
      .click()
  await expect(page.getByLabel('Ordenar inventários')).toHaveCount(0)
  await expect(
    page.getByRole('table').locator('tbody tr').first(),
  ).toContainText('Biblioteca central')
})

test('filtros no celular têm rótulos visíveis e não causam rolagem horizontal da página', async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await setup(page)
  await page.getByLabel('Buscar inventários').fill('Laboratório')
  await pickOption(page.getByLabel('Filtrar por status'), 'Atrasados')
  await expect(
    page.getByText('1 de 4 inventários', { exact: true }),
  ).toBeVisible()
  await expect(page.getByLabel('Filtrar por ano')).toBeVisible()
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true)
})

test('status tem quatro opções, setores respeitam os vínculos e tabela mostra a conclusão efetiva', async ({
  page,
}) => {
  await setup(page)
  expect(await optionTexts(page.getByLabel('Filtrar por status'))).toEqual([
    'Todos os status',
    'Em andamento',
    'Finalizados',
    'Atrasados',
  ])
  expect(await optionTexts(page.getByLabel('Filtrar por setor'))).toEqual([
    'Todos os setores',
    'Patrimônio',
    'Engenharia',
  ])
  await expect(
    page.getByRole('columnheader', { name: /Atualização/ }),
  ).toHaveCount(0)
  await expect(
    page.getByRole('columnheader', { name: /Data de finalização/ }),
  ).toBeVisible()
  const concluded = page
    .getByRole('table')
    .locator('tbody tr')
    .filter({ hasText: 'Biblioteca histórica' })
  await expect(
    concluded.getByRole('cell', { name: '13/10/2026', exact: true }),
  ).toBeVisible()
  await expect(
    concluded.getByRole('cell', { name: '12/10/2026', exact: true }),
  ).toHaveCount(0)
  await pickOption(page.getByLabel('Filtrar por setor'), 'Engenharia')
  await expect(
    page.getByText('0 de 4 inventários', { exact: true }),
  ).toBeVisible()
})

test('admin sem vínculo pessoal consegue filtrar por todos os setores', async ({
  page,
}) => {
  await setup(page, '', true)
  expect(await optionTexts(page.getByLabel('Filtrar por setor'))).toEqual([
    'Todos os setores',
    'Biblioteca',
    'Patrimônio',
    'Engenharia',
  ])
  await pickOption(page.getByLabel('Filtrar por setor'), 'Biblioteca')
  await expect(
    page.getByText('1 de 4 inventários', { exact: true }),
  ).toBeVisible()
  await expect(page.getByLabel('Ordenar inventários')).toHaveCount(0)
  await expect(page.getByLabel('Itens por página')).toHaveCount(0)
})

test('novo inventário carrega setores sem apresentar erro por cancelamento', async ({
  page,
}) => {
  await setup(page, '', true)
  await page
    .getByRole('button', { name: 'Novo Inventário', exact: true })
    .click()
  await expect(page.getByLabel('Setor responsável')).toContainText(
    'Selecione o setor',
  )
  expect(await optionTexts(page.getByLabel('Setor responsável'))).toEqual([
    'Biblioteca',
    'Patrimônio',
    'Engenharia',
  ])
  await expect(page.getByLabel('Data de criação')).toHaveCount(0)
  await expect(page.locator('form label')).toHaveText([
    'Nome do inventário',
    'Descrição',
    'Setor responsável',
    'Data prevista de finalização',
  ])
  await pickOption(page.getByLabel('Setor responsável'), 'Patrimônio')
  await expect(page.getByLabel('Setor responsável')).toContainText('Patrimônio')
  await expect(
    page.getByText('Não foi possível carregar os setores.', { exact: true }),
  ).toHaveCount(0)
})

test('edição apresenta setor atual e envia a troca para a API', async ({
  page,
}) => {
  await setup(page, '', true)
  let saved: Record<string, unknown> | null = null
  await page.route('http://127.0.0.1:18080/inventory/1', async (route) => {
    if (route.request().method() === 'OPTIONS') {
      return route.fulfill({
        status: 204,
        headers: {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Headers': 'Authorization, Content-Type',
          'Access-Control-Allow-Methods': 'PUT, OPTIONS',
        },
      })
    }
    saved = route.request().postDataJSON()
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      headers: { 'Access-Control-Allow-Origin': '*' },
      body: JSON.stringify({
        ...inventories[0],
        ...saved,
        sectorName: 'Patrimônio',
      }),
    })
  })
  await page
    .getByRole('button', { name: 'Editar Biblioteca central', exact: true })
    .click()
  const modal = page.getByRole('dialog', { name: 'Editar Inventário' })
  await expect(modal.locator('label')).toHaveText([
    'Nome',
    'Descrição',
    'Setor responsável',
    'Data prevista de finalização',
  ])
  await expect(modal.getByLabel('Data de criação')).toHaveCount(0)
  await expect(modal.getByLabel('Setor responsável')).toBeEnabled()
  await expect(modal.getByLabel('Setor responsável')).toContainText(
    'Biblioteca',
  )
  await pickOption(modal.getByLabel('Setor responsável'), 'Patrimônio')
  await modal.getByRole('button', { name: 'Salvar Alterações' }).click()
  await expect(modal).toHaveCount(0)
  expect(saved).toMatchObject({ sectorId: 2, name: 'Biblioteca central' })
})

test('menu mostra no máximo cinco inventários ao abrir a listagem', async ({
  page,
}) => {
  await setup(page, '', true)
  const expanded = [
    ...inventories,
    ...[5, 6, 7].map((id) => ({
      ...inventories[0],
      id,
      name: `Inventário ${id}`,
    })),
  ]
  await page.route(
    'http://127.0.0.1:18080/inventory/user-inventories',
    (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        headers: { 'Access-Control-Allow-Origin': '*' },
        body: JSON.stringify(expanded),
      }),
  )
  await page.reload()
  const menu = page
    .getByRole('navigation', { name: 'Menu principal' })
    .filter({ visible: true })
  await expect(menu.locator('a[href^="/inventory/"]')).toHaveCount(0)
  await menu.getByRole('link', { name: 'Abrir lista de inventários' }).click()
  await expect(page).toHaveURL(/\/inventory$/)
  await expect(menu.locator('a[href^="/inventory/"]')).toHaveCount(0)
  await menu
    .getByRole('button', { name: 'Expandir inventários do menu' })
    .click()
  await expect(
    menu.getByRole('link', { name: 'Todos os inventários', exact: true }),
  ).toBeVisible()
  await expect(menu.locator('a[href^="/inventory/"]')).toHaveCount(5)
  await expect(
    menu.getByRole('link', { name: 'Inventário 5', exact: true }),
  ).toBeVisible()
  await expect(
    menu.getByRole('link', { name: 'Inventário 6', exact: true }),
  ).toHaveCount(0)
  await menu
    .getByRole('button', { name: 'Recolher inventários do menu' })
    .click()
  await expect(menu.locator('a[href^="/inventory/"]')).toHaveCount(0)
  await menu
    .getByRole('button', { name: 'Expandir inventários do menu' })
    .click()
  await expect(menu.locator('a[href^="/inventory/"]')).toHaveCount(5)
})
