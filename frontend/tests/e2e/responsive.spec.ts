import { expect, test, type Page } from '@playwright/test'

// Garante que nenhuma tela tenha rolagem horizontal em celulares e tablets.
const viewports = [
  { name: 'celular-320', width: 320, height: 640 },
  { name: 'celular-375', width: 375, height: 740 },
  { name: 'tablet-768', width: 768, height: 1024 },
  { name: 'tablet-1024', width: 1024, height: 768 },
]

const inventory = {
  id: 10,
  name: 'Inventário da Biblioteca Central do Campus Universitário',
  description: 'Conferência anual dos bens patrimoniais da biblioteca',
  createdAt: '13/10/2026',
  updatedAt: null,
  plannedEndDate: '2026-12-20',
  completedAt: null,
  status: 'EM_ANDAMENTO',
  sectorId: 1,
  sectorName: 'Biblioteca Central',
  itemCount: 3,
}

const items = [1, 2, 3, 4, 5].map((id) => ({
  id,
  code: `PAT-00000${id}`,
  description: `Notebook corporativo de alto desempenho modelo ${id} com garantia estendida`,
  price: 4599.9,
  qrCode: `QR${id}`,
  responsible: 'Maria da Conceição Albuquerque Pereira',
  locale: 'Sala 12 - Bloco B',
  isValid: id % 2 === 0,
  observations: [],
}))

const sectors = [
  { id: 1, name: 'Biblioteca Central', code: 'BIB', active: true },
  { id: 2, name: 'Tecnologia da Informação', code: null, active: false },
]
const permissions = [
  {
    id: 201,
    name: 'MANAGE_SECTOR',
    description: 'Gerencia os inventários do setor e aloca usuários nele.',
  },
  {
    id: 202,
    name: 'MANAGE_ITEM',
    description: 'Opera os itens do setor: valida e registra observações.',
  },
]
const roles = [
  {
    id: 11,
    name: 'Gestor de Setor',
    description: 'Gestor',
    permissionIds: [201],
    management: true,
    allocationCount: 2,
    userCount: 2,
    deletable: false,
  },
  {
    id: 12,
    name: 'Consulta',
    description: 'Somente leitura',
    permissionIds: [],
    management: false,
    allocationCount: 0,
    userCount: 0,
    deletable: true,
  },
]
const allocation = (sectorId: number, roleId: number, roleName: string) => ({
  sectorId,
  roleId,
  roleName,
  permissions: roleId === 11 ? ['MANAGE_SECTOR'] : [],
})
const users = [
  {
    id: 1,
    name: 'Camille Maria Dias de Souza Albuquerque',
    email: 'camille.maria.dias.longo@example.com',
    sectorIds: [1],
    allocations: [allocation(1, 11, 'Gestor de Setor')],
    administrator: true,
    active: true,
  },
  {
    id: 2,
    name: 'Ana',
    email: 'ana@example.com',
    sectorIds: [1, 2],
    allocations: [
      allocation(1, 11, 'Gestor de Setor'),
      allocation(2, 12, 'Consulta'),
    ],
    administrator: false,
    active: true,
  },
]

const mock = async (page: Page) => {
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
    const send = (body: unknown) =>
      route.fulfill({
        status: request.method() === 'OPTIONS' ? 204 : 200,
        contentType: 'application/json',
        headers: {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Headers': 'Authorization, Content-Type',
          'Access-Control-Allow-Methods':
            'GET, POST, PUT, PATCH, DELETE, OPTIONS',
        },
        body: request.method() === 'OPTIONS' ? undefined : JSON.stringify(body),
      })
    if (path === '/auth/me') return send({ ...users[0], administrator: true })
    if (path === '/inventory/user-inventories')
      return send([inventory, { ...inventory, id: 11, status: 'VENCIDO' }])
    if (path === '/inventory/10') return send({ inventory, canFinalize: true })
    if (path === '/inventory/inventory-items') return send(items)
    if (path.startsWith('/item/')) return send(items[0])
    if (path === '/sectors') return send(sectors)
    if (/^\/sectors\/\d+\/users$/.test(path)) return send(users)
    if (path === '/roles') return send(roles)
    if (path === '/permissions') return send(permissions)
    if (path === '/users') return send(users)
    return send([])
  })
}

const overflow = (page: Page) =>
  page.evaluate(() => {
    const width = document.documentElement.clientWidth
    const offenders: string[] = []
    document.querySelectorAll<HTMLElement>('body *').forEach((el) => {
      const rect = el.getBoundingClientRect()
      if (rect.width === 0 || rect.height === 0) return
      if (el.closest('[data-scroll-container]')) return
      if (rect.right > width + 1 && getComputedStyle(el).position !== 'fixed') {
        // Ignora conteúdo dentro de contêineres que já rolam por conta própria.
        let parent = el.parentElement
        while (parent && parent !== document.body) {
          const overflowX = getComputedStyle(parent).overflowX
          if (overflowX === 'auto' || overflowX === 'scroll') return
          parent = parent.parentElement
        }
        offenders.push(
          `${el.tagName.toLowerCase()}.${String(el.className).slice(0, 60)} (${Math.round(rect.right)}px)`,
        )
      }
    })
    return {
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: width,
      offenders: offenders.slice(0, 8),
    }
  })

const routes = [
  '/dashboard',
  '/inventory',
  '/inventory/10',
  '/inventory/10/item/1',
  '/sectors',
  '/users',
  '/roles',
]

for (const viewport of viewports) {
  test.describe(viewport.name, () => {
    test.use({ viewport: { width: viewport.width, height: viewport.height } })

    for (const route of routes) {
      test(`sem rolagem horizontal em ${route}`, async ({ page }) => {
        await mock(page)
        await page.goto(route)
        await page.waitForLoadState('networkidle')
        await page.waitForTimeout(600)
        // Uma tela de erro do Next não pode contar como página sem overflow.
        await expect(
          page.getByText(/Runtime Error|Build Error|Unhandled Runtime Error/),
        ).toHaveCount(0)
        await page.screenshot({
          path: `.next/responsive/${viewport.name}${route.replace(/\//g, '_')}.png`,
          fullPage: true,
        })
        const result = await overflow(page)
        expect(result.offenders, JSON.stringify(result)).toEqual([])
        expect(result.scrollWidth).toBeLessThanOrEqual(result.clientWidth)
      })
    }

    test('sem rolagem horizontal no login', async ({ page }) => {
      await page.goto('/')
      await page.waitForLoadState('networkidle')
      await page.screenshot({
        path: `.next/responsive/${viewport.name}_login.png`,
        fullPage: true,
      })
      const result = await overflow(page)
      expect(result.offenders, JSON.stringify(result)).toEqual([])
    })
  })
}

// Janelas sobrepostas (modais, gavetas, menu) devem caber na tela e ser roláveis.
const fitsViewport = async (page: Page, name: string) => {
  const dialog = page.getByRole('dialog', name ? { name } : undefined).first()
  await expect(dialog).toBeVisible()
  await page.waitForTimeout(500)
  const box = await dialog.boundingBox()
  const size = page.viewportSize()!
  expect(box, 'janela sem dimensões').not.toBeNull()
  expect(box!.x).toBeGreaterThanOrEqual(-1)
  expect(box!.y).toBeGreaterThanOrEqual(-1)
  expect(box!.x + box!.width).toBeLessThanOrEqual(size.width + 1)
  expect(box!.y + box!.height).toBeLessThanOrEqual(size.height + 1)
  const scroll = await page.evaluate(() => document.documentElement.scrollWidth)
  expect(scroll).toBeLessThanOrEqual(size.width)
}

for (const viewport of viewports.slice(0, 2)) {
  test.describe(`janelas ${viewport.name}`, () => {
    test.use({ viewport: { width: viewport.width, height: viewport.height } })

    const shot = (page: Page, name: string) =>
      page.screenshot({ path: `.next/responsive/${viewport.name}_${name}.png` })

    test('menu lateral', async ({ page }) => {
      await mock(page)
      await page.goto('/inventory')
      await page
        .getByRole('button', { name: 'Abrir menu de navegação' })
        .click()
      await fitsViewport(page, 'Menu de navegação principal')
      // O menu do usuário e a saída ficam no rodapé e precisam estar sempre à vista.
      const logout = page.getByRole('button', { name: 'Sair da conta' })
      await expect(logout).toBeVisible()
      const box = (await logout.boundingBox())!
      expect(box.y + box.height).toBeLessThanOrEqual(viewport.height)
      await shot(page, 'menu')
    })

    test('novo inventário', async ({ page }) => {
      await mock(page)
      await page.goto('/inventory')
      await page.getByRole('button', { name: /Novo Inventário/ }).click()
      await fitsViewport(page, '')
      await shot(page, 'novo-inventario')
    })

    test('usuários: botão rápido e gerenciar acesso', async ({ page }) => {
      await mock(page)
      await page.goto('/users')
      await expect(
        page.getByRole('button', { name: /^(Desativar|Ativar) / }).first(),
      ).toBeVisible()
      // A lista, com os dois botões em cada linha, não pode estourar a largura da tela.
      const size = page.viewportSize()!
      expect(
        await page.evaluate(() => document.documentElement.scrollWidth),
      ).toBeLessThanOrEqual(size.width)
      await shot(page, 'usuarios')
      await page
        .getByRole('button', { name: 'Gerenciar acesso' })
        .first()
        .click()
      await fitsViewport(page, '')
      await shot(page, 'gerenciar-acesso')
    })

    test('setores: novo setor', async ({ page }) => {
      await mock(page)
      await page.goto('/sectors')
      await page.getByRole('button', { name: /Novo setor/ }).click()
      await fitsViewport(page, '')
      await shot(page, 'novo-setor')
    })
  })
}

// Com a sidebar fixa (>= 1024px), os três cards do topo do inventário só cabem lado a lado
// a partir de xl; antes disso ficavam estreitos, com títulos e botões sobrepostos.
test.describe('cards do inventário no tablet', () => {
  test.use({ viewport: { width: 1024, height: 768 } })

  test('não ficam espremidos', async ({ page }) => {
    await mock(page)
    await page.goto('/inventory/10')
    for (const name of ['Estatísticas', 'Escaneamento', 'Importação']) {
      const card = page
        .locator('[data-slot="card"]')
        .filter({ has: page.getByRole('heading', { name }) })
      const box = await card.first().boundingBox()
      expect(box, `card ${name}`).not.toBeNull()
      expect(box!.width, `largura do card ${name}`).toBeGreaterThanOrEqual(280)
    }
  })
})
