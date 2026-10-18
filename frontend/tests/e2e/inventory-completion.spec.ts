import { expect, test, type Page } from '@playwright/test'

const setup = async (
  page: Page,
  canFinalize = true,
  failure = 0,
  completed = false,
) => {
  const inventory = {
    id: 10,
    name: 'Biblioteca',
    description: '',
    createdAt: '13/10/2026',
    updatedAt: null,
    plannedEndDate: '2026-10-20',
    completedAt: completed ? '2026-10-13T14:30:00' : null,
    status: completed ? 'CONCLUIDO' : 'EM_ANDAMENTO',
    sectorId: 3,
    sectorName: 'Biblioteca',
    itemCount: 0,
  }
  const state = { submissions: 0, reads: 0 }
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
    const send = (status: number, body: unknown) =>
      route.fulfill({
        status,
        contentType: 'application/json',
        headers: {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Headers': 'Authorization, Content-Type',
          'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
        },
        body: status === 204 ? undefined : JSON.stringify(body),
      })
    if (request.method() === 'OPTIONS') return send(204, null)
    if (path === '/auth/me')
      return send(200, {
        id: 7,
        name: 'Responsável',
        email: 'gestor@example.com',
        profileId: 2,
        sectorIds: [3],
        permissions: ['MANAGE_SECTOR'],
      })
    if (path === '/inventory/user-inventories') {
      state.reads += 1
      return send(200, [inventory])
    }
    if (path === '/inventory/10') return send(200, { inventory, canFinalize })
    if (path === '/inventory/inventory-items') return send(200, [])
    if (path === '/inventory/10/finalize') {
      state.submissions += 1
      if (failure === 409) {
        inventory.status = 'CONCLUIDO'
        inventory.completedAt = '2026-10-13T14:30:00'
      }
      if (failure) return send(failure, { message: 'Falha' })
      inventory.status = 'CONCLUIDO'
      inventory.completedAt = '2026-10-13T14:30:00'
      return send(200, inventory)
    }
    return send(200, [])
  })
  await page.goto('/inventory/10')
  return state
}

test('confirma finalização, exibe data e atualiza os inventários', async ({
  page,
}) => {
  const state = await setup(page)
  const section = page.getByRole('region', { name: 'Situação do inventário' })
  await expect(section.getByText('Em andamento', { exact: true })).toBeVisible()
  await section
    .getByRole('button', { name: 'Finalizar inventário', exact: true })
    .click()
  const dialog = page.getByRole('dialog', { name: 'Finalizar inventário' })
  await expect(dialog).toBeVisible()
  await dialog.getByRole('button', { name: 'Cancelar' }).click()
  expect(state.submissions).toBe(0)
  await section
    .getByRole('button', { name: 'Finalizar inventário', exact: true })
    .click()
  const readsBefore = state.reads
  await dialog.getByRole('button', { name: 'Sim, finalizar' }).click()
  await expect(section.getByText('Finalizado', { exact: true })).toBeVisible()
  await expect(section.getByText(/Finalizado em/)).toContainText('13/10/2026')
  await expect(
    section.getByRole('button', { name: 'Finalizar inventário', exact: true }),
  ).toHaveCount(0)
  await expect.poll(() => state.reads).toBeGreaterThan(readsBefore)
  expect(state.submissions).toBe(1)
})

test('oculta a ação para usuário sem autorização', async ({ page }) => {
  await setup(page, false)
  await expect(page.getByText('Em andamento', { exact: true })).toBeVisible()
  await expect(
    page.getByRole('button', { name: 'Finalizar inventário', exact: true }),
  ).toHaveCount(0)
})

test('falha mantém o inventário aberto e permite nova tentativa', async ({
  page,
}) => {
  await setup(page, true, 500)
  await page
    .getByRole('button', { name: 'Finalizar inventário', exact: true })
    .click()
  await page.getByRole('button', { name: 'Sim, finalizar' }).click()
  await expect(
    page
      .getByRole('region', { name: 'Situação do inventário' })
      .getByRole('alert'),
  ).toContainText('Não foi possível concluir')
  await expect(page.getByText('Em andamento', { exact: true })).toBeVisible()
  await expect(
    page.getByRole('button', { name: 'Finalizar inventário', exact: true }),
  ).toBeEnabled()
  await expect(page.getByRole('dialog')).toHaveCount(0)
})

test('conflito atualiza a tela com a finalização feita por outro usuário', async ({
  page,
}) => {
  await setup(page, true, 409)
  await page
    .getByRole('button', { name: 'Finalizar inventário', exact: true })
    .click()
  await page.getByRole('button', { name: 'Sim, finalizar' }).click()
  await expect(page.getByText('Finalizado', { exact: true })).toBeVisible()
  await expect(
    page
      .getByRole('region', { name: 'Situação do inventário' })
      .getByRole('alert'),
  ).toContainText('já foi finalizado')
  await expect(page.getByRole('dialog')).toHaveCount(0)
})

test('inventário já finalizado mostra a data sem oferecer nova finalização', async ({
  page,
}) => {
  await setup(page, true, 0, true)
  await expect(page.getByText(/Finalizado em/)).toContainText('13/10/2026')
  await expect(
    page.getByRole('button', { name: 'Finalizar inventário', exact: true }),
  ).toHaveCount(0)
})

test('Inventários na barra superior retorna para a listagem', async ({
  page,
}) => {
  await setup(page)
  const breadcrumb = page.getByRole('navigation', { name: 'breadcrumb' })
  const inventoriesLink = breadcrumb.getByRole('link', {
    name: 'Inventários',
    exact: true,
  })
  await expect(inventoriesLink).toHaveAttribute('href', '/inventory')
  await inventoriesLink.click()
  await expect(page).toHaveURL(/\/inventory$/)
  await expect(
    page.getByRole('heading', { name: 'Inventários', exact: true }),
  ).toBeVisible()
})

test('edição do item e observações usam modais independentes e preservam erros', async ({
  page,
}) => {
  await setup(page)
  let item = {
    id: 20,
    code: 'PAT-20',
    description: 'Mesa',
    responsible: 'Ana',
    price: 100,
    locale: '',
    isValid: false,
    observations: [{ id: 1, content: 'Nota original' }],
  }
  let updates = 0
  let notesFail = true
  await page.route('http://127.0.0.1:18080/item/20**', async (route) => {
    const request = route.request()
    const headers = {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Headers': 'Authorization, Content-Type',
      'Access-Control-Allow-Methods': 'GET, PUT, PATCH, OPTIONS',
    }
    if (request.method() === 'OPTIONS')
      return route.fulfill({ status: 204, headers })
    if (request.method() === 'PUT') {
      updates++
      const payload = request.postDataJSON()
      expect(payload.observations).toEqual(item.observations)
      item = { ...item, ...payload }
    }
    if (request.method() === 'PATCH') {
      if (notesFail) return route.fulfill({ status: 500, headers, body: '{}' })
      item = { ...item, observations: request.postDataJSON() }
    }
    await route.fulfill({
      status: 200,
      headers,
      contentType: 'application/json',
      body: JSON.stringify(item),
    })
  })
  await page.goto('/inventory/10/item/20')
  await page.getByRole('button', { name: 'Editar item', exact: true }).click()
  const edit = page.getByRole('dialog', { name: 'Editar item', exact: true })
  await edit.getByLabel('Descrição').fill('Descartada')
  await edit.getByRole('button', { name: 'Cancelar', exact: true }).click()
  expect(updates).toBe(0)
  await page.getByRole('button', { name: 'Editar item', exact: true }).click()
  await expect(edit.getByLabel('Descrição')).toHaveValue('Mesa')
  await edit.getByLabel('Sala / Local').fill('Sala 2')
  await edit.getByLabel('Valor (R$)').fill('150,50')
  await edit.getByRole('button', { name: 'Salvar item', exact: true }).click()
  await expect(edit).toHaveCount(0)
  expect(item.price).toBe(150.5)
  expect(item.locale).toBe('Sala 2')
  await page
    .getByRole('button', { name: 'Editar observações', exact: true })
    .click()
  const notes = page.getByRole('dialog', {
    name: 'Editar observações',
    exact: true,
  })
  await notes.getByPlaceholder('Nova observação').fill('Nova nota')
  await notes
    .getByRole('button', { name: 'Salvar observações', exact: true })
    .click()
  await expect(notes.getByRole('alert')).toContainText(
    'Não foi possível salvar',
  )
  await expect(notes.getByPlaceholder('Nova observação')).toHaveValue(
    'Nova nota',
  )
  notesFail = false
  await notes
    .getByRole('button', { name: 'Salvar observações', exact: true })
    .click()
  await expect(notes).toHaveCount(0)
  await expect(
    page.getByRole('region', { name: 'Observações do item' }),
  ).toContainText('Nova nota')
  expect(updates).toBe(1)
})

test('leitor permite digitar o patrimônio sem câmera e abre o painel de observações', async ({
  page,
}) => {
  await setup(page)
  const item = {
    id: 20,
    code: 'PAT-20',
    description: 'Mesa',
    responsible: 'Ana',
    price: 100,
    locale: 'Sala 2',
    isValid: false,
    observations: [],
  }
  const headers = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Authorization, Content-Type',
    'Access-Control-Allow-Methods': 'GET, PUT, OPTIONS',
  }
  await page.route(
    'http://127.0.0.1:18080/inventory/inventory-items**',
    (route) =>
      route.fulfill({
        status: 200,
        headers,
        contentType: 'application/json',
        body: JSON.stringify([item]),
      }),
  )
  await page.route('http://127.0.0.1:18080/item/20', (route) =>
    route.fulfill({
      status: route.request().method() === 'OPTIONS' ? 204 : 200,
      headers,
      contentType: 'application/json',
      body:
        route.request().method() === 'OPTIONS'
          ? undefined
          : JSON.stringify({ ...item, isValid: true }),
    }),
  )
  await page.reload()
  await page
    .getByRole('button', { name: 'Escanear ou digitar código', exact: true })
    .click()
  const reader = page.getByRole('dialog', {
    name: 'Scanner de código de barras e QR Code',
  })
  await expect(
    reader.getByRole('button', { name: 'Buscar item' }),
  ).toBeDisabled()
  await reader.getByLabel('Código patrimonial').fill('PAT-20')
  await reader.getByRole('button', { name: 'Buscar item' }).click()
  await expect(reader).toHaveCount(0)
  await expect(
    page.getByRole('heading', { name: 'PAT-20', exact: true }),
  ).toBeVisible()
  await expect(page.getByLabel('Nova observação')).toBeVisible()
  const data = page.getByRole('region', { name: 'Dados cadastrais do item' })
  await expect(data).toContainText('Mesa')
  await expect(data).toContainText('Sala 2')
  await expect(data.locator('input, textarea')).toHaveCount(0)
  await page.getByLabel('Nova observação').fill('Encontrado na sala 203')
  await page
    .getByRole('button', { name: 'Adicionar observação à lista' })
    .click()
  await expect(page.getByLabel('Nova observação')).toHaveValue('')
  await expect(
    page.getByText('Encontrado na sala 203', { exact: true }),
  ).toBeVisible()
  await page
    .getByRole('button', { name: 'Deletar observação', exact: true })
    .click()
  const confirmation = page.getByRole('dialog', { name: 'Excluir observação' })
  await confirmation
    .getByRole('button', { name: 'Cancelar', exact: true })
    .click()
  await expect(
    page.getByText('Encontrado na sala 203', { exact: true }),
  ).toBeVisible()
  await page
    .getByRole('button', { name: 'Deletar observação', exact: true })
    .click()
  await confirmation
    .getByRole('button', { name: 'Excluir observação', exact: true })
    .click()
  await expect(
    page.getByText('Encontrado na sala 203', { exact: true }),
  ).toHaveCount(0)
})

test('Excel usa nome do inventário e envio usa destinatário preenchido com usuário logado', async ({
  page,
}) => {
  await setup(page)
  const headers = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Authorization, Content-Type',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  }
  await page.route('http://127.0.0.1:18080/item/export-sheet**', (route) =>
    route.fulfill({ status: 200, headers, body: 'test-sheet' }),
  )
  let payload: Record<string, unknown> | null = null
  await page.route(
    'http://127.0.0.1:18080/item/send-email-sheet**',
    (route) => {
      if (route.request().method() === 'OPTIONS')
        return route.fulfill({ status: 204, headers })
      payload = route.request().postDataJSON()
      return route.fulfill({ status: 200, headers, body: '' })
    },
  )
  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Exportar planilha Excel' }).click()
  expect((await download).suggestedFilename()).toBe('Biblioteca.xlsx')
  await page.getByRole('button', { name: 'Enviar planilha por e-mail' }).click()
  const modal = page.getByRole('dialog', { name: 'Enviar planilha por e-mail' })
  await expect(modal.getByLabel('Destinatário')).toHaveValue(
    'gestor@example.com',
  )
  await modal.getByLabel('Destinatário').fill('destino@example.com')
  await modal.getByLabel('Assunto').fill('Biblioteca')
  await modal.getByLabel('Descrição').fill('Segue planilha.')
  await modal
    .getByRole('button', { name: 'Enviar planilha', exact: true })
    .click()
  await expect(modal).toHaveCount(0)
  expect(payload).toEqual({
    email: 'destino@example.com',
    subject: 'Biblioteca',
    message: 'Segue planilha.',
  })
})
