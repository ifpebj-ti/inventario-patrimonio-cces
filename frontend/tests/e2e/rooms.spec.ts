import { expect, test, type Page, type Route } from '@playwright/test'
import { pickOption } from './select'

type Room = {
  id: number
  name: string
  sectorId: number
  itemCount: number
  deletable: boolean
}

const baseItem = {
  description: 'Monitor',
  responsible: 'TI',
  price: 75000,
  isValid: false,
  observations: [],
}

let rooms: Room[]
let items: Record<string, unknown>[]
let putBodies: Record<string, unknown>[]
let previewBody: unknown
let importBody: unknown

const headers = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'Authorization, Content-Type',
  'Access-Control-Allow-Methods': 'GET, POST, PUT, PATCH, DELETE, OPTIONS',
}
const respond = (route: Route, status: number, body: unknown) =>
  route.fulfill({
    status,
    headers,
    contentType: 'application/json',
    body: status === 204 ? undefined : JSON.stringify(body),
  })

const room = (id: number, name: string, itemCount = 0): Room => ({
  id,
  name,
  sectorId: 1,
  itemCount,
  deletable: itemCount === 0,
})

test.beforeEach(async ({ page, context }) => {
  rooms = [room(1, 'Sala 105', 2), room(2, 'Sala 203'), room(3, 'Laboratório')]
  items = [
    {
      ...baseItem,
      id: 1,
      code: 'PAT-1',
      locale: 'Sala 105',
      roomId: 1,
      roomName: 'Sala 105',
    },
    {
      ...baseItem,
      id: 2,
      code: 'PAT-2',
      locale: 'Sala 105',
      roomId: 1,
      roomName: 'Sala 105',
      isValid: true,
      foundRoomId: 2,
      foundRoomName: 'Sala 203',
      divergent: true,
    },
    { ...baseItem, id: 3, code: 'PAT-3', locale: null, roomId: null },
  ]
  putBodies = []
  previewBody = undefined
  importBody = undefined
  await context.addCookies([
    {
      name: 'inventarium.token',
      value: 'test-session',
      domain: '127.0.0.1',
      path: '/',
    },
  ])
  await page.route('http://127.0.0.1:18080/**', async (route) => {
    const request = route.request()
    const method = request.method()
    if (method === 'OPTIONS') return respond(route, 204, null)
    const path = new URL(request.url()).pathname
    if (path === '/auth/me')
      return respond(route, 200, {
        id: 1,
        name: 'Camille',
        email: 'test@example.com',
        administrator: true,
        sectorIds: [],
        allocations: [],
      })
    if (path === '/inventory/user-inventories') return respond(route, 200, [])
    if (path === '/inventory/1')
      return respond(route, 200, {
        inventory: {
          id: 1,
          name: 'Inventário A',
          description: 'Teste',
          createdAt: '12/10/2026',
          updatedAt: null,
          plannedEndDate: '2026-10-30',
          completedAt: null,
          status: 'EM_ANDAMENTO',
          sectorId: 1,
          sectorName: 'Biblioteca',
          itemCount: items.length,
        },
        canFinalize: true,
      })
    if (path === '/inventory/inventory-items') return respond(route, 200, items)
    if (path === '/sectors' && method === 'GET')
      return respond(route, 200, [
        {
          id: 1,
          name: 'Biblioteca',
          code: 'BIB',
          active: true,
          deletable: false,
        },
      ])
    if (path === '/sectors/1/rooms' && method === 'GET')
      return respond(route, 200, rooms)
    if (path === '/sectors/1/rooms' && method === 'POST') {
      const created = room(10 + rooms.length, request.postDataJSON().name)
      rooms.push(created)
      return respond(route, 201, created)
    }
    const roomPath = path.match(/^\/rooms\/(\d+)$/)
    if (roomPath && method === 'PUT') {
      const target = rooms.find((row) => row.id === Number(roomPath[1]))!
      target.name = request.postDataJSON().name
      return respond(route, 200, target)
    }
    if (roomPath && method === 'DELETE') {
      rooms = rooms.filter((row) => row.id !== Number(roomPath[1]))
      return respond(route, 204, null)
    }
    const itemPath = path.match(/^\/item\/(\d+)$/)
    if (itemPath && method === 'PUT') {
      const body = request.postDataJSON()
      putBodies.push(body)
      const found = rooms.find((row) => row.id === body.foundRoomId)
      const saved = {
        ...body,
        foundRoomName: found?.name ?? null,
        divergent: !!found && body.roomId != null && found.id !== body.roomId,
      }
      return respond(route, 200, saved)
    }
    if (path.match(/^\/item\/\d+\/notes$/))
      return respond(route, 200, { ...items[0], ...putBodies.at(-1) })
    if (path === '/inventory/validate-sheet') {
      previewBody = request.postData()
      return respond(route, 200, {
        message: 'O arquivo é válido e está pronto para o processamento.',
        newRooms: ['Almoxarifado', 'Sala 1O5'],
        existingRooms: ['Sala 105'],
      })
    }
    if (path === '/inventory/add-items') {
      importBody = request.postData()
      return respond(route, 201, {
        totalProcessed: 1,
        successes: 1,
        failures: 0,
        createdRooms: 2,
        failedItems: [],
      })
    }
    return respond(route, 200, [])
  })
})

const scan = async (page: Page, code: string) => {
  await page
    .getByRole('button', { name: 'Escanear ou digitar código', exact: true })
    .click()
  const reader = page.getByRole('dialog', {
    name: 'Scanner de código de barras e QR Code',
  })
  await reader.getByLabel('Código patrimonial').fill(code)
  await reader.getByRole('button', { name: 'Buscar item' }).click()
  await expect(reader).toHaveCount(0)
}

test.describe('validar item informando a sala', () => {
  test('escanear abre o item sem validar, com a sala cadastrada selecionada; confirmar envia a sala', async ({
    page,
  }) => {
    await page.goto('/inventory/1')
    await scan(page, 'PAT-1')

    await expect(page.getByRole('heading', { name: 'PAT-1' })).toBeVisible()
    // Nada foi gravado só por escanear.
    expect(putBodies).toHaveLength(0)
    const select = page.getByRole('combobox', {
      name: 'Sala em que o item está',
    })
    await expect(select).toContainText('Sala 105')
    await expect(page.getByText('Divergente:')).toHaveCount(0)

    await page.getByRole('button', { name: 'Validar item' }).click()
    await expect(page.getByRole('heading', { name: 'PAT-1' })).toHaveCount(0)
    expect(putBodies).toHaveLength(1)
    expect(putBodies[0]).toMatchObject({
      id: 1,
      isValid: true,
      foundRoomId: 1,
      roomId: 1,
    })
  })

  test('trocar a sala mostra a divergência antes de confirmar e envia a sala encontrada', async ({
    page,
  }) => {
    await page.goto('/inventory/1')
    await scan(page, 'PAT-1')

    await pickOption(
      page.getByRole('combobox', { name: 'Sala em que o item está' }),
      'Sala 203',
    )
    await expect(page.getByRole('alert')).toContainText(
      'Divergente: cadastrada em Sala 105, encontrada em Sala 203',
    )
    await page.getByRole('button', { name: 'Validar item' }).click()
    await expect.poll(() => putBodies.length).toBe(1)
    expect(putBodies[0]).toMatchObject({ isValid: true, foundRoomId: 2 })
  })

  test('item sem sala cadastrada só valida depois de escolher a sala', async ({
    page,
  }) => {
    await page.goto('/inventory/1')
    await scan(page, 'PAT-3')

    const confirm = page.getByRole('button', { name: 'Validar item' })
    await expect(confirm).toBeDisabled()
    await expect(
      page.getByText('Escolha a sala para validar o item.'),
    ).toBeVisible()
    await pickOption(
      page.getByRole('combobox', { name: 'Sala em que o item está' }),
      'Laboratório',
    )
    await expect(confirm).toBeEnabled()
    await expect(page.getByText('Divergente:')).toHaveCount(0)
    await confirm.click()
    await expect.poll(() => putBodies.length).toBe(1)
    expect(putBodies[0]).toMatchObject({ isValid: true, foundRoomId: 3 })
  })

  test('tabela mostra a sala encontrada e o filtro de divergentes', async ({
    page,
  }) => {
    await page.goto('/inventory/1')
    await expect(
      page.getByRole('columnheader', { name: 'Encontrada' }),
    ).toBeVisible()
    await expect(page.getByRole('cell', { name: /Sala 203/ })).toBeVisible()
    await expect(page.getByText('Divergente').first()).toBeVisible()
    await expect(
      page.getByRole('cell', { name: 'PAT-1', exact: true }),
    ).toBeVisible()

    await page.getByLabel('Mostrar só divergentes (1)').click()
    await expect(
      page.getByRole('cell', { name: 'PAT-2', exact: true }),
    ).toBeVisible()
    await expect(
      page.getByRole('cell', { name: 'PAT-1', exact: true }),
    ).toHaveCount(0)
    await expect(
      page.getByRole('cell', { name: 'PAT-3', exact: true }),
    ).toHaveCount(0)
  })

  test('setor sem salas escaneia como antes, sem seletor', async ({ page }) => {
    rooms = []
    await page.goto('/inventory/1')
    await expect(
      page.getByRole('columnheader', { name: 'Encontrada' }),
    ).toHaveCount(0)
    await scan(page, 'PAT-1')

    await expect(page.getByRole('heading', { name: 'PAT-1' })).toBeVisible()
    await expect(
      page.getByRole('combobox', { name: 'Sala em que o item está' }),
    ).toHaveCount(0)
    // Validação imediata, como antes.
    await expect.poll(() => putBodies.length).toBe(1)
    expect(putBodies[0]).toMatchObject({ isValid: true })
  })
})

test.describe('salas do setor', () => {
  test('cadastra, renomeia e exclui; sala com item não tem exclusão', async ({
    page,
  }) => {
    await page.goto('/sectors')
    await page.getByRole('button', { name: 'Salas', exact: true }).click()
    const sheet = page.getByRole('dialog', { name: 'Salas de Biblioteca' })
    await expect(sheet).toContainText('3 salas')
    await expect(sheet.getByText('2 itens')).toBeVisible()
    await expect(
      sheet.getByRole('button', { name: 'Excluir sala Sala 105' }),
    ).toHaveCount(0)

    await sheet.getByLabel('Nome da nova sala').fill('  Sala   301 ')
    await sheet.getByRole('button', { name: 'Adicionar' }).click()
    await expect(sheet.getByText('Sala 301')).toBeVisible()

    // Mesmo nome com outra grafia é recusado antes de chamar a API.
    await sheet.getByLabel('Nome da nova sala').fill('LABORATORIO')
    await sheet.getByRole('button', { name: 'Adicionar' }).click()
    await expect(sheet.getByRole('alert')).toContainText('Já existe uma sala')

    await sheet.getByRole('button', { name: 'Renomear sala Sala 203' }).click()
    await sheet.getByLabel('Novo nome da sala Sala 203').fill('Sala 204')
    await sheet.getByRole('button', { name: 'Salvar', exact: true }).click()
    await expect(sheet.getByText('Sala 204')).toBeVisible()

    await sheet.getByRole('button', { name: 'Excluir sala Sala 204' }).click()
    await page.getByRole('button', { name: 'Excluir', exact: true }).click()
    await expect(sheet.getByText('Sala 204')).toHaveCount(0)
  })
})

test.describe('importação de planilha', () => {
  test('mostra as salas novas antes de importar e informa quantas foram criadas', async ({
    page,
  }) => {
    items = []
    await page.goto('/inventory/1')
    await page.getByLabel('Arquivo CSV', { exact: true }).setInputFiles({
      name: 'patrimonio.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from(
        'Código;Descrição;Responsável;Valor;Sala;Observações\n001;Monitor;TI;750,00;Almoxarifado;Bom\n',
      ),
    })
    const preview = page.getByTestId('rooms-preview')
    await expect(preview).toBeVisible()
    await expect(page.getByTestId('new-rooms')).toHaveText(
      'Almoxarifado, Sala 1O5',
    )
    await expect(preview).toContainText('2 salas novas serão criadas')
    await expect(page.getByTestId('existing-rooms')).toContainText('Sala 105')
    expect(String(previewBody)).toContain('name="inventoryId"')
    // Nada foi importado só por validar.
    expect(importBody).toBeUndefined()

    await page.getByRole('button', { name: 'Processar', exact: true }).click()
    await expect(
      page.getByText(/2 salas criadas no setor/).first(),
    ).toBeVisible()
  })
})
