import { expect, test, type Page } from '@playwright/test'
import { expandFilters, optionTexts, pickOption } from './select'

// Alocação (usuário em setor com um cargo). O cargo vale só naquele setor.
const GESTOR = 12
const OPERADOR = 13
const CONSULTA = 14
const alloc = (sectorId: number, roleId = CONSULTA) => ({ sectorId, roleId })

const fixture = () => ({
  admin: true,
  reader: true,
  // Setores em que o usuário logado (gestor) só opera ou consulta: sem gestão neles.
  viewOnly: [] as number[],
  fail: {} as Record<string, number>,
  requests: [] as {
    method: string
    path: string
    body: Record<string, unknown> | null
  }[],
  sectors: [
    { id: 1, name: 'Patrimônio', code: 'PAT', active: true, deletable: false },
    { id: 2, name: 'Biblioteca', code: null, active: false, deletable: false },
    { id: 3, name: 'Tecnologia', code: 'TI', active: true, deletable: true },
  ],
  roles: [
    {
      id: GESTOR,
      name: 'Gestor de Setor',
      description: 'Gestor',
      permissionIds: [201],
    },
    {
      id: OPERADOR,
      name: 'Operador de Campo',
      description: 'Operador',
      permissionIds: [202],
    },
    {
      id: CONSULTA,
      name: 'Consulta',
      description: null as string | null,
      permissionIds: [] as number[],
    },
  ],
  permissions: [
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
  ],
  users: [
    {
      id: 1,
      name: 'Camille',
      email: 'camille@example.com',
      administrator: true,
      allocations: [alloc(1)],
      active: true,
    },
    {
      id: 2,
      name: 'Maria',
      email: 'maria@example.com',
      administrator: false,
      allocations: [] as { sectorId: number; roleId: number }[],
      active: true,
    },
    {
      id: 3,
      name: 'Ana',
      email: 'ana@example.com',
      administrator: false,
      allocations: [alloc(1, GESTOR), alloc(2, CONSULTA)],
      active: true,
    },
  ],
})

type State = ReturnType<typeof fixture>
type FixtureUser = State['users'][number]
const sectorIdsOf = (user: FixtureUser) =>
  user.allocations.map((allocation) => allocation.sectorId)

const setup = async (page: Page, state = fixture()) => {
  await page.context().addCookies([
    {
      name: 'inventarium.token',
      value: 'test-session',
      domain: '127.0.0.1',
      path: '/',
    },
  ])
  const roleOf = (id: number) => state.roles.find((role) => role.id === id)
  const permissionNames = (roleId: number) =>
    (roleOf(roleId)?.permissionIds ?? []).map(
      (id) =>
        state.permissions.find((permission) => permission.id === id)!.name,
    )
  const allocationsOut = (user: FixtureUser, as?: number) =>
    user.allocations.map(({ sectorId, roleId }) => {
      const id = as ?? roleId
      return {
        sectorId,
        roleId: id,
        roleName: roleOf(id)?.name ?? `Cargo #${id}`,
        permissions: permissionNames(id),
      }
    })
  const userOut = (user: FixtureUser) => ({
    id: user.id,
    name: user.name,
    email: user.email,
    administrator: user.administrator,
    active: user.active,
    sectorIds: sectorIdsOf(user),
    allocations: allocationsOut(user),
  })
  const roleOut = (role: State['roles'][number]) => {
    const allocated = state.users.flatMap((user) =>
      user.allocations.filter((allocation) => allocation.roleId === role.id),
    )
    const userCount = state.users.filter((user) =>
      user.allocations.some((allocation) => allocation.roleId === role.id),
    ).length
    return {
      ...role,
      management: role.permissionIds.includes(201),
      allocationCount: allocated.length,
      userCount,
      deletable: allocated.length === 0 && role.permissionIds.length === 0,
    }
  }
  await page.route('http://127.0.0.1:18080/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname
    const method = request.method()
    const send = (status: number, body: unknown) =>
      route.fulfill({
        status,
        headers: {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Headers': 'Authorization, Content-Type',
          'Access-Control-Allow-Methods':
            'GET, POST, PUT, PATCH, DELETE, OPTIONS',
        },
        contentType: 'application/json',
        body: status === 204 ? undefined : JSON.stringify(body),
      })
    if (method === 'OPTIONS') return send(204, null)
    const body = request.postData()
      ? (request.postDataJSON() as Record<string, unknown>)
      : null
    state.requests.push({ method, path, body })
    if (state.fail[`${method} ${path}`])
      return send(state.fail[`${method} ${path}`], { error: 'Failure' })
    if (path === '/auth/me') {
      const me = state.users[0]
      // Quem não é administrador tem o cargo de gestor (reader) ou de consulta em cada setor.
      const as = state.admin ? undefined : state.reader ? GESTOR : CONSULTA
      return send(200, {
        id: me.id,
        name: me.name,
        email: me.email,
        administrator: state.admin,
        sectorIds: sectorIdsOf(me),
        allocations: allocationsOut(me, as).map((allocation) =>
          state.viewOnly.includes(allocation.sectorId)
            ? {
                ...allocation,
                roleId: CONSULTA,
                roleName: 'Consulta',
                permissions: [],
              }
            : allocation,
        ),
      })
    }
    if (path === '/inventory/user-inventories') return send(200, [])
    if (
      path.startsWith('/roles') ||
      path === '/permissions' ||
      path === '/users'
    ) {
      if (!state.admin && !state.reader)
        return send(403, { error: 'Forbidden' })
    }
    if (path.startsWith('/sectors') && !state.reader && !state.admin)
      return send(403, { error: 'Forbidden' })
    if (path === '/permissions') return send(200, state.permissions)
    if (path === '/roles' && method === 'GET')
      return send(200, state.roles.map(roleOut))
    if (path === '/roles' && method === 'POST') {
      const role = {
        id: 16,
        name: String(body?.name),
        description: (body?.description as string | null) ?? null,
        permissionIds: (body?.permissionIds as number[]) ?? [],
      }
      state.roles.push(role)
      return send(201, roleOut(role))
    }
    const roleUpdate = path.match(/^\/roles\/(\d+)$/)
    if (roleUpdate) {
      const role = state.roles.find((row) => row.id === Number(roleUpdate[1]))
      if (!role) return send(404, {})
      if (method === 'DELETE') {
        if (!roleOut(role).deletable) return send(409, {})
        state.roles.splice(state.roles.indexOf(role), 1)
        return send(204, null)
      }
      if (method === 'PUT') {
        role.name = String(body?.name)
        role.description = (body?.description as string | null) ?? null
        role.permissionIds = (body?.permissionIds as number[]) ?? []
      }
      return send(200, roleOut(role))
    }
    if (path === '/users' && method === 'GET' && !state.admin)
      // O gestor lista apenas os usuários que compartilham algum setor com ele.
      return send(
        200,
        state.users
          .filter((user) =>
            sectorIdsOf(user).some((id) =>
              sectorIdsOf(state.users[0]).includes(id),
            ),
          )
          .map(userOut),
      )
    if (path === '/users' && method === 'GET') {
      const roleId = url.searchParams.get('roleId')
      return send(
        200,
        state.users
          .filter(
            (user) =>
              !roleId ||
              user.allocations.some(
                (allocation) => allocation.roleId === Number(roleId),
              ),
          )
          .map(userOut),
      )
    }
    if (path === '/sectors' && method === 'GET')
      return send(
        200,
        state.admin
          ? state.sectors
          : state.sectors.filter((sector) =>
              sectorIdsOf(state.users[0]).includes(sector.id),
            ),
      )
    const sectorMembers = path.match(/^\/sectors\/(\d+)\/users$/)
    if (sectorMembers)
      return send(
        200,
        state.users
          .filter((user) =>
            sectorIdsOf(user).includes(Number(sectorMembers[1])),
          )
          .map(userOut),
      )
    const assignable = path.match(/^\/sectors\/(\d+)\/assignable-users$/)
    if (assignable && method === 'GET')
      return send(
        200,
        state.users
          .filter(
            (user) =>
              user.active && !sectorIdsOf(user).includes(Number(assignable[1])),
          )
          .map(({ id, name, email }) => ({ id, name, email })),
      )
    const sectorBatch = path.match(/^\/sectors\/(\d+)\/members$/)
    if (sectorBatch && method === 'PUT') {
      const sectorId = Number(sectorBatch[1])
      for (const userId of body?.userIds as number[]) {
        const user = state.users.find((row) => row.id === userId)!
        user.allocations = user.allocations.filter(
          (a) => a.sectorId !== sectorId,
        )
        user.allocations.push({ sectorId, roleId: Number(body?.roleId) })
      }
      return send(
        200,
        state.users
          .filter((user) => sectorIdsOf(user).includes(sectorId))
          .map(userOut),
      )
    }
    if (path === '/sectors' && method === 'POST') {
      const sector = {
        id: 4,
        name: String(body?.name),
        code: body?.code as string | null,
        active: true,
        deletable: true,
      }
      state.sectors.push(sector)
      return send(201, sector)
    }
    const sectorUpdate = path.match(
      /^\/sectors\/(\d+)(?:\/(activate|deactivate))?$/,
    )
    if (sectorUpdate) {
      const sector = state.sectors.find(
        (row) => row.id === Number(sectorUpdate[1]),
      )
      if (!sector) return send(404, {})
      if (method === 'DELETE') {
        state.sectors.splice(state.sectors.indexOf(sector), 1)
        return send(204, null)
      }
      if (method === 'PUT') {
        sector.name = String(body?.name)
        sector.code = body?.code as string | null
      }
      if (method === 'PATCH') sector.active = sectorUpdate[2] === 'activate'
      return send(200, sector)
    }
    const userActive = path.match(/^\/users\/(\d+)\/(activate|deactivate)$/)
    if (userActive && method === 'PATCH') {
      const user = state.users.find((row) => row.id === Number(userActive[1]))!
      user.active = userActive[2] === 'activate'
      return send(200, userOut(user))
    }
    const userAdministrator = path.match(/^\/users\/(\d+)\/administrator$/)
    if (userAdministrator && method === 'PATCH') {
      const user = state.users.find(
        (row) => row.id === Number(userAdministrator[1]),
      )!
      user.administrator = body?.administrator as boolean
      return send(200, userOut(user))
    }
    const userSector = path.match(/^\/users\/(\d+)\/sectors\/(\d+)$/)
    if (userSector) {
      const user = state.users.find((row) => row.id === Number(userSector[1]))!
      const id = Number(userSector[2])
      user.allocations = user.allocations.filter((a) => a.sectorId !== id)
      if (method === 'PUT')
        user.allocations.push({ sectorId: id, roleId: Number(body?.roleId) })
      return send(200, userOut(user))
    }
    return send(404, {})
  })
  return state
}

const card = (page: Page, name: string) =>
  page.locator('article').filter({
    has: page.getByRole('heading', { name: new RegExp(`^${name}`) }),
  })
const confirm = (page: Page, title: string) =>
  page
    .getByRole('dialog', { name: title, exact: true })
    .getByRole('button', {
      name: /^(Confirmar|Desativar|Reativar|Ativar|Salvar)$/,
    })
    .click()

test('administrador recebe menus administrativos', async ({ page }) => {
  await setup(page)
  await page.goto('/sectors')
  await expect(
    page.getByRole('button', { name: 'Novo setor', exact: true }),
  ).toBeEnabled()
  await expect(
    page.getByRole('link', { name: 'Usuários', exact: true }),
  ).toBeVisible()
  await expect(
    page.getByRole('link', { name: 'Cargos e permissões', exact: true }),
  ).toBeVisible()
})

test('gestor consulta somente setores vinculados e não recebe controles administrativos', async ({
  page,
}) => {
  const state = fixture()
  state.admin = false
  await setup(page, state)
  await page.goto('/sectors')
  await expect(card(page, 'Patrimônio')).toBeVisible()
  await expect(card(page, 'Biblioteca')).toHaveCount(0)
  await expect(card(page, 'Tecnologia')).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Novo setor' })).toHaveCount(0)
  await expect(
    page.getByRole('button', { name: 'Editar', exact: true }),
  ).toHaveCount(0)
  await card(page, 'Patrimônio')
    .getByRole('button', { name: 'Usuários associados' })
    .click()
  await expect(page.getByRole('dialog')).toContainText('Ana')
  // O menu do gestor não tem Usuários nem os Cargos.
  const menu = page.getByRole('navigation', { name: 'Menu principal' })
  await expect(menu.getByRole('link', { name: 'Usuários' })).toHaveCount(0)
  await expect(
    menu.getByRole('link', { name: 'Cargos e permissões' }),
  ).toHaveCount(0)
})

test('sem alocação ninguém é gestor de nada: não vê setores nem o menu', async ({
  page,
}) => {
  const state = fixture()
  state.admin = false
  state.users[0].allocations = []
  await setup(page, state)
  await page.goto('/sectors')
  await expect(
    page.getByRole('heading', { name: 'Acesso restrito' }),
  ).toBeVisible()
  await expect(page.locator('article')).toHaveCount(0)
  await expect(
    page
      .getByRole('navigation', { name: 'Menu principal' })
      .getByRole('link', { name: 'Setores', exact: true }),
  ).toHaveCount(0)
})

test('gestor vinculado a múltiplos setores vê todos os seus vínculos', async ({
  page,
}) => {
  const state = fixture()
  state.admin = false
  state.users[0].allocations = [alloc(1, GESTOR), alloc(3, GESTOR)]
  await setup(page, state)
  await page.goto('/sectors')
  await expect(card(page, 'Patrimônio')).toBeVisible()
  await expect(card(page, 'Tecnologia')).toBeVisible()
  await expect(card(page, 'Biblioteca')).toHaveCount(0)
})

test('consulta e sem alocação não recebem acesso direto às telas administrativas', async ({
  page,
}) => {
  const state = fixture()
  state.admin = false
  state.reader = false
  state.users[0].allocations = []
  await setup(page, state)
  for (const path of ['/sectors', '/roles', '/users']) {
    await page.goto(path)
    await expect(
      page.getByRole('heading', { name: 'Acesso restrito' }),
    ).toBeVisible()
  }
})

test('setores oferecem busca e filtros de ativos/inativos', async ({
  page,
}) => {
  await setup(page)
  await page.goto('/sectors')
  await expandFilters(page)
  await pickOption(
    page.getByRole('combobox', { name: 'Situação', exact: true }),
    'Inativos',
  )
  await expect(card(page, 'Biblioteca')).toBeVisible()
  await expect(card(page, 'Patrimônio')).toHaveCount(0)
  await pickOption(
    page.getByRole('combobox', { name: 'Situação', exact: true }),
    'Todos',
  )
  await page.getByLabel('Buscar setor', { exact: true }).fill('PAT')
  await expect(card(page, 'Patrimônio')).toBeVisible()
  await expect(card(page, 'Biblioteca')).toHaveCount(0)
})

test('setor valida nome, normaliza espaços e salva o contrato correto', async ({
  page,
}) => {
  const state = await setup(page)
  await page.goto('/sectors')
  await page.getByRole('button', { name: 'Novo setor' }).click()
  const dialog = page.getByRole('dialog', { name: 'Novo setor', exact: true })
  await dialog.getByLabel('Nome do setor').fill('   ')
  await dialog.getByRole('button', { name: 'Salvar setor' }).click()
  await expect(dialog.getByRole('alert')).toContainText('Informe o nome')
  await dialog.getByLabel('Nome do setor').fill(' Patrimônio ')
  await dialog.getByRole('button', { name: 'Salvar setor' }).click()
  await expect(dialog.getByRole('alert')).toContainText('Já existe')
  await dialog.getByLabel('Nome do setor').fill(' Financeiro ')
  await dialog.getByRole('button', { name: 'Salvar setor' }).click()
  await expect(card(page, 'Financeiro')).toBeVisible()
  expect(
    state.requests.find(
      (request) => request.method === 'POST' && request.path === '/sectors',
    )?.body,
  ).toEqual({ name: 'Financeiro', code: null })
})

test('edição e ativação de setor respeitam confirmação e preservam associações', async ({
  page,
}) => {
  const state = await setup(page)
  await page.goto('/sectors')
  await card(page, 'Patrimônio')
    .getByRole('button', { name: 'Editar', exact: true })
    .click()
  await page.getByLabel('Código (opcional)').fill('PATR')
  await page.getByRole('button', { name: 'Salvar setor' }).click()
  await expect(card(page, 'Patrimônio')).toContainText('PATR')
  await card(page, 'Patrimônio')
    .getByRole('button', { name: 'Desativar' })
    .click()
  await page
    .getByRole('dialog', { name: 'Desativar setor' })
    .getByRole('button', { name: 'Cancelar' })
    .click()
  expect(state.sectors[0].active).toBe(true)
  await card(page, 'Patrimônio')
    .getByRole('button', { name: 'Desativar' })
    .click()
  await confirm(page, 'Desativar setor')
  await expect(card(page, 'Patrimônio')).toContainText('Inativo')
  expect(sectorIdsOf(state.users[2])).toEqual([1, 2])
  await card(page, 'Patrimônio')
    .getByRole('button', { name: 'Ativar', exact: true })
    .click()
  await confirm(page, 'Ativar setor')
  await expect(card(page, 'Patrimônio')).toContainText('Ativo')
})

test('admin exclui só setor sem vínculos, depois de confirmar', async ({
  page,
}) => {
  const state = await setup(page)
  await page.goto('/sectors')
  // Setor com usuários ou inventários só pode ser ativado/desativado.
  for (const name of ['Patrimônio', 'Biblioteca']) {
    await expect(
      card(page, name).getByRole('button', { name: 'Excluir' }),
    ).toHaveCount(0)
  }
  await card(page, 'Tecnologia')
    .getByRole('button', { name: 'Excluir' })
    .click()
  const dialog = page.getByRole('dialog', { name: 'Excluir setor' })
  await expect(dialog).toContainText('Tecnologia')
  await dialog.getByRole('button', { name: 'Cancelar' }).click()
  await expect(card(page, 'Tecnologia')).toBeVisible()
  expect(state.requests.some((r) => r.method === 'DELETE')).toBe(false)
  await card(page, 'Tecnologia')
    .getByRole('button', { name: 'Excluir' })
    .click()
  await page
    .getByRole('dialog', { name: 'Excluir setor' })
    .getByRole('button', { name: 'Excluir' })
    .click()
  await expect(card(page, 'Tecnologia')).toHaveCount(0)
  expect(
    state.requests.filter((r) => r.method === 'DELETE').map((r) => r.path),
  ).toEqual(['/sectors/3'])
  expect(state.sectors.map((sector) => sector.id)).toEqual([1, 2])
})

test('exclusão recusada pela API (409) mostra o erro e mantém o setor', async ({
  page,
}) => {
  const state = await setup(page)
  state.fail['DELETE /sectors/3'] = 409
  await page.goto('/sectors')
  await card(page, 'Tecnologia')
    .getByRole('button', { name: 'Excluir' })
    .click()
  await page
    .getByRole('dialog', { name: 'Excluir setor' })
    .getByRole('button', { name: 'Excluir' })
    .click()
  await expect(page.getByText('não pode ser excluído')).toBeVisible()
  await expect(card(page, 'Tecnologia')).toBeVisible()
  expect(state.sectors.map((sector) => sector.id)).toEqual([1, 2, 3])
})

test('gestor não vê Excluir nem em setor sem vínculos', async ({ page }) => {
  const state = fixture()
  state.admin = false
  state.users[0].allocations = [alloc(3, GESTOR)]
  await setup(page, state)
  await page.goto('/sectors')
  await expect(card(page, 'Tecnologia')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Excluir' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Novo setor' })).toHaveCount(0)
})

for (const status of [400, 401, 403, 404, 409, 500]) {
  test(`salvar setor trata HTTP ${status} sem apresentar sucesso`, async ({
    page,
  }) => {
    const state = await setup(page)
    state.fail['POST /sectors'] = status
    await page.goto('/sectors')
    await page.getByRole('button', { name: 'Novo setor' }).click()
    await page.getByLabel('Nome do setor').fill('Financeiro')
    await page.getByRole('button', { name: 'Salvar setor' }).click()
    await expect(
      page.getByRole('dialog', { name: 'Novo setor' }).getByRole('alert'),
    ).toBeVisible()
    await expect(page.getByLabel('Nome do setor')).toHaveValue('Financeiro')
    expect(state.sectors).toHaveLength(3)
  })
}

test('filtros de setor, cargo e busca se combinam', async ({ page }) => {
  await setup(page)
  await page.goto('/users')
  await expandFilters(page)
  await expect(card(page, 'Ana')).toBeVisible()
  await pickOption(page.getByLabel('Filtrar por setor'), 'Sem setor')
  await expect(card(page, 'Maria')).toBeVisible()
  await expect(card(page, 'Ana')).toHaveCount(0)
  await pickOption(page.getByLabel('Filtrar por setor'), 'Patrimônio')
  await pickOption(page.getByLabel('Filtrar por cargo'), 'Gestor de Setor')
  await expect(card(page, 'Ana')).toBeVisible()
  await expect(card(page, 'Camille')).toHaveCount(0)
  await page.getByLabel('Buscar usuário').fill('ana@example.com')
  await expect(card(page, 'Ana')).toBeVisible()
})

test('usuários filtram por papel e por cargo e enviam roleId ao servidor', async ({
  page,
}) => {
  const state = await setup(page)
  await page.goto('/users')
  await expandFilters(page)
  await expect(card(page, 'Maria')).toContainText('Nenhum setor associado')
  await expect(card(page, 'Camille')).toContainText('Administrador')
  await expect(card(page, 'Ana')).toContainText('Biblioteca · Consulta')
  await expect(card(page, 'Ana')).toContainText('Patrimônio · Gestor de Setor')
  await pickOption(page.getByLabel('Filtrar por cargo'), 'Sem acesso')
  await expect(card(page, 'Camille')).toHaveCount(0)
  await expect(card(page, 'Maria')).toBeVisible()
  await pickOption(page.getByLabel('Filtrar por cargo'), 'Administradores')
  await expect(card(page, 'Camille')).toBeVisible()
  await expect(card(page, 'Maria')).toHaveCount(0)
  await pickOption(page.getByLabel('Filtrar por cargo'), 'Gestor de Setor')
  await expect(card(page, 'Ana')).toBeVisible()
  await expect(card(page, 'Maria')).toHaveCount(0)
  expect(
    state.requests.filter((request) => request.path === '/users').length,
  ).toBeGreaterThan(1)
})

test('o papel de administrador fica pendente e é salvo', async ({ page }) => {
  const state = await setup(page)
  await page.goto('/users')
  await card(page, 'Maria')
    .getByRole('button', { name: 'Gerenciar acesso' })
    .click()
  const modal = page.getByRole('dialog', { name: 'Acesso de Maria' })
  const save = modal.getByRole('button', { name: 'Salvar alterações' })
  await expect(modal).toContainText('Nenhuma alteração pendente')
  await expect(save).toBeDisabled()

  await modal.getByRole('checkbox', { name: 'Administrador' }).click()
  await expect(modal).toContainText('1 alteração pendente')
  // Nada vai ao servidor antes de salvar.
  expect(state.requests.filter((r) => r.method !== 'GET')).toHaveLength(0)
  expect(state.users[1].administrator).toBe(false)

  await save.click()
  await expect(page.getByText('Alterações salvas.')).toBeVisible()
  await expect(modal).toContainText('Nenhuma alteração pendente')
  expect(state.users[1].administrator).toBe(true)
  expect(
    state.requests
      .filter((r) => r.method !== 'GET')
      .map((r) => `${r.method} ${r.path}`),
  ).toEqual(['PATCH /users/2/administrator'])
})

test('falha ao salvar o administrador mostra o erro e mantém a alteração pendente', async ({
  page,
}) => {
  const state = fixture()
  state.fail['PATCH /users/2/administrator'] = 409
  await setup(page, state)
  await page.goto('/users')
  await card(page, 'Maria')
    .getByRole('button', { name: 'Gerenciar acesso' })
    .click()
  const modal = page.getByRole('dialog', { name: 'Acesso de Maria' })
  await modal.getByRole('checkbox', { name: 'Administrador' }).click()
  await modal.getByRole('button', { name: 'Salvar alterações' }).click()
  await expect(modal.getByRole('alert')).toContainText('entra em conflito')
  expect(state.users[1].administrator).toBe(false)
  await expect(modal).toContainText('1 alteração pendente')
})

test('o modal de acesso não aloca: setores e cargos são só leitura e levam para Setores', async ({
  page,
}) => {
  const state = await setup(page)
  await page.goto('/users')
  await card(page, 'Ana')
    .getByRole('button', { name: 'Gerenciar acesso' })
    .click()
  const modal = page.getByRole('dialog', { name: 'Acesso de Ana' })
  await expect(modal).toContainText('Patrimônio')
  await expect(modal).toContainText('Gestor de Setor')
  await expect(modal).toContainText('Biblioteca')
  await expect(modal).toContainText('Consulta')
  // Sem campo de adicionar setor, sem seletor de cargo e sem remover.
  await expect(modal.getByLabel('Adicionar setor')).toHaveCount(0)
  await expect(modal.getByLabel(/^Cargo em /)).toHaveCount(0)
  await expect(modal.getByRole('button', { name: /^Desalocar/ })).toHaveCount(0)
  expect(state.requests.filter((r) => r.method !== 'GET')).toHaveLength(0)
  await modal
    .getByRole('link', { name: /Setores → Usuários associados/ })
    .click()
  await expect(page).toHaveURL(/\/sectors$/)
})

test('ninguém altera o próprio papel de administrador', async ({ page }) => {
  const state = await setup(page)
  await page.goto('/users')
  await card(page, 'Camille')
    .getByRole('button', { name: 'Gerenciar acesso' })
    .click()
  const modal = page.getByRole('dialog', { name: 'Acesso de Camille' })
  const administrator = modal.getByRole('checkbox', { name: 'Administrador' })
  await expect(administrator).toBeChecked()
  await expect(administrator).toBeDisabled()
  await expect(modal).toContainText(
    'Você não pode alterar o seu próprio papel de administrador.',
  )
  await expect(
    modal.getByRole('button', { name: 'Salvar alterações' }),
  ).toBeDisabled()
  expect(state.requests.filter((r) => r.method !== 'GET')).toHaveLength(0)
})

const rolesTable = (page: Page) =>
  page.getByRole('table', { name: 'Cargos e suas permissões' })
const roleRow = (page: Page, name: string) =>
  rolesTable(page).getByRole('row').filter({ hasText: name })

test('cargos exibem permissões e uso, e o administrador tem as ações', async ({
  page,
}) => {
  await setup(page)
  await page.goto('/roles')
  await expect(rolesTable(page)).toBeVisible()
  await expect(roleRow(page, 'Gestor de Setor')).toContainText(
    'Gerenciar Setor',
  )
  await expect(roleRow(page, 'Gestor de Setor')).toContainText(
    '1 usuário em 1 alocação',
  )
  await expect(roleRow(page, 'Operador de Campo')).toContainText('Sem uso')
  await expect(roleRow(page, 'Consulta')).toContainText(
    'Nenhuma permissão atribuída',
  )
  await expect(page.getByRole('button', { name: 'Novo cargo' })).toBeEnabled()
  await expect(
    page.getByRole('button', { name: 'Editar cargo Consulta' }),
  ).toBeVisible()
  // Excluir só aparece em cargo sem alocação e sem permissão: aqui, nenhum.
  await expect(
    page.getByRole('button', { name: /^Excluir cargo / }),
  ).toHaveCount(0)
})

test('administrador cria um cargo escolhendo as permissões', async ({
  page,
}) => {
  const state = await setup(page)
  await page.goto('/roles')
  await page.getByRole('button', { name: 'Novo cargo' }).click()
  const modal = page.getByRole('dialog', { name: 'Novo cargo' })
  await modal.getByLabel('Nome do cargo').fill('  Auditoria  ')
  await modal.getByLabel('Descrição (opcional)').fill('Confere os inventários')
  await modal.getByRole('checkbox', { name: 'Gerenciar Item' }).click()
  await modal.getByRole('button', { name: 'Salvar cargo' }).click()
  await expect(page.getByText('Cargo salvo com sucesso.')).toBeVisible()
  await expect(roleRow(page, 'Auditoria')).toContainText('Gerenciar Item')
  expect(
    state.requests.find((r) => r.method === 'POST' && r.path === '/roles')
      ?.body,
  ).toEqual({
    name: 'Auditoria',
    description: 'Confere os inventários',
    permissionIds: [202],
  })
})

test('cargo valida nome vazio e nome repetido', async ({ page }) => {
  const state = await setup(page)
  await page.goto('/roles')
  await page.getByRole('button', { name: 'Novo cargo' }).click()
  const modal = page.getByRole('dialog', { name: 'Novo cargo' })
  await modal.getByLabel('Nome do cargo').fill('Consulta')
  await modal.getByRole('button', { name: 'Salvar cargo' }).click()
  await expect(modal.getByRole('alert')).toContainText(
    'Já existe um cargo com este nome.',
  )
  expect(
    state.requests.filter((r) => r.method === 'POST' && r.path === '/roles'),
  ).toHaveLength(0)
})

test('mudar as permissões de um cargo em uso pede confirmação com o impacto', async ({
  page,
}) => {
  const state = await setup(page)
  await page.goto('/roles')
  await page
    .getByRole('button', { name: 'Editar cargo Gestor de Setor' })
    .click()
  const modal = page.getByRole('dialog', { name: 'Editar cargo' })
  await expect(modal).toContainText('usado por 1 usuário em 1 alocação')
  await modal.getByRole('checkbox', { name: 'Gerenciar Setor' }).click()
  await modal.getByRole('button', { name: 'Salvar cargo' }).click()
  const confirmation = page.getByRole('dialog', {
    name: 'Alterar permissões do cargo',
    exact: true,
  })
  await expect(confirmation).toContainText('1 usuário em 1 alocação')
  await expect(confirmation).toContainText('vale na hora')
  // Cancelar não envia nada.
  await confirmation.getByRole('button', { name: 'Cancelar' }).click()
  expect(state.requests.filter((r) => r.method === 'PUT')).toHaveLength(0)
  await modal.getByRole('button', { name: 'Salvar cargo' }).click()
  await page
    .getByRole('dialog', { name: 'Alterar permissões do cargo', exact: true })
    .getByRole('button', { name: 'Salvar' })
    .click()
  await expect(page.getByText('Cargo salvo com sucesso.')).toBeVisible()
  expect(state.roles[0].permissionIds).toEqual([])
  expect(state.requests.filter((r) => r.method === 'PUT')).toHaveLength(1)
})

test('editar só o nome de um cargo em uso não pede confirmação', async ({
  page,
}) => {
  const state = await setup(page)
  await page.goto('/roles')
  await page
    .getByRole('button', { name: 'Editar cargo Gestor de Setor' })
    .click()
  const modal = page.getByRole('dialog', { name: 'Editar cargo' })
  await modal.getByLabel('Nome do cargo').fill('Coordenação')
  await modal.getByRole('button', { name: 'Salvar cargo' }).click()
  await expect(page.getByText('Cargo salvo com sucesso.')).toBeVisible()
  expect(state.roles[0].name).toBe('Coordenação')
})

test('exclusão de cargo: só sem alocação e sem permissão, com confirmação', async ({
  page,
}) => {
  const state = fixture()
  state.roles.push({
    id: 16,
    name: 'Provisório',
    description: null,
    permissionIds: [],
  })
  await setup(page, state)
  await page.goto('/roles')
  // Em uso (Consulta) ou com permissão (Operador): sem o botão.
  await expect(
    page.getByRole('button', { name: 'Excluir cargo Consulta' }),
  ).toHaveCount(0)
  await expect(
    page.getByRole('button', { name: 'Excluir cargo Operador de Campo' }),
  ).toHaveCount(0)
  await page.getByRole('button', { name: 'Excluir cargo Provisório' }).click()
  const dialog = page.getByRole('dialog', {
    name: 'Excluir cargo',
    exact: true,
  })
  await expect(dialog).toContainText('Provisório')
  await dialog.getByRole('button', { name: 'Cancelar' }).click()
  expect(state.requests.some((r) => r.method === 'DELETE')).toBe(false)
  await page.getByRole('button', { name: 'Excluir cargo Provisório' }).click()
  await page
    .getByRole('dialog', { name: 'Excluir cargo', exact: true })
    .getByRole('button', { name: 'Excluir' })
    .click()
  await expect(roleRow(page, 'Provisório')).toHaveCount(0)
  expect(
    state.requests.filter((r) => r.method === 'DELETE').map((r) => r.path),
  ).toEqual(['/roles/16'])
})

test('exclusão de cargo recusada pela API (409) mostra o aviso e atualiza a lista', async ({
  page,
}) => {
  const state = fixture()
  state.roles.push({
    id: 16,
    name: 'Provisório',
    description: null,
    permissionIds: [],
  })
  state.fail['DELETE /roles/16'] = 409
  await setup(page, state)
  await page.goto('/roles')
  await page.getByRole('button', { name: 'Excluir cargo Provisório' }).click()
  await page
    .getByRole('dialog', { name: 'Excluir cargo', exact: true })
    .getByRole('button', { name: 'Excluir' })
    .click()
  await expect(page.getByText('não pode ser excluído')).toBeVisible()
  await expect(roleRow(page, 'Provisório')).toBeVisible()
})

test('busca de cargos filtra por nome e permissão', async ({ page }) => {
  await setup(page)
  await page.goto('/roles')
  const table = rolesTable(page)
  await expect(table).toBeVisible()
  await page.getByLabel('Buscar cargo ou permissão').fill('Gerenciar Item')
  await expect(
    table.getByRole('rowheader', { name: /Operador de Campo/ }),
  ).toBeVisible()
  await expect(
    table.getByRole('rowheader', { name: /Gestor de Setor/ }),
  ).toHaveCount(0)
  await page.getByLabel('Buscar cargo ou permissão').fill('nao existe')
  await expect(
    page.getByText('Nenhum cargo encontrado para esta busca.'),
  ).toBeVisible()
})

const drawer = (page: Page, sector: string) =>
  page.getByRole('dialog', { name: `Usuários de ${sector}` })
const openMembers = async (page: Page, sector: string) => {
  await card(page, sector)
    .getByRole('button', { name: 'Usuários associados' })
    .click()
  return drawer(page, sector)
}

test('barra lateral lista os usuários do setor agrupados por cargo', async ({
  page,
}) => {
  await setup(page)
  await page.goto('/sectors')
  const sheet = await openMembers(page, 'Patrimônio')
  await expect(sheet).toContainText('2 usuários, agrupados por cargo.')
  expect(
    await sheet.getByRole('heading', { level: 3 }).allTextContents(),
  ).toEqual(['Gestor de Setor (1)', 'Consulta (1)'])
  await expect(
    sheet.locator('section').filter({ hasText: 'Gestor de Setor (1)' }),
  ).toContainText('Ana')
  await expect(
    sheet.locator('section').filter({ hasText: 'Consulta (1)' }),
  ).toContainText('Camille')
  await sheet.getByRole('button', { name: 'Fechar' }).click()
  await expect(sheet).toHaveCount(0)
  // Setor sem ninguém.
  const empty = await openMembers(page, 'Tecnologia')
  await expect(empty).toContainText('Nenhum usuário associado a este setor.')
})

test('administrador adiciona vários usuários de uma vez com um cargo', async ({
  page,
}) => {
  const state = await setup(page)
  await page.goto('/sectors')
  const sheet = await openMembers(page, 'Tecnologia')
  await sheet.getByRole('button', { name: 'Adicionar usuários' }).click()
  const modal = page.getByRole('dialog', {
    name: 'Adicionar usuários a Tecnologia',
  })
  // Todos os usuários ativos que ainda não estão no setor.
  await expect(
    modal.getByRole('checkbox', { name: 'Selecionar Camille' }),
  ).toBeVisible()
  await expect(
    modal.getByRole('checkbox', { name: 'Selecionar Maria' }),
  ).toBeVisible()
  const allocate = modal.getByRole('button', { name: /^Alocar / })
  await expect(allocate).toBeDisabled()
  await expect(modal).toContainText('Nenhum selecionado')
  // O cargo de menos permissões vem escolhido.
  await expect(modal.getByLabel('Cargo dos usuários')).toContainText('Consulta')

  await modal.getByRole('checkbox', { name: 'Selecionar Maria' }).click()
  await modal.getByRole('checkbox', { name: 'Selecionar Ana' }).click()
  await expect(modal).toContainText('2 selecionados')
  await pickOption(modal.getByLabel('Cargo dos usuários'), 'Operador de Campo')
  // Nada vai ao servidor antes de confirmar.
  expect(state.requests.filter((r) => r.method !== 'GET')).toHaveLength(0)
  await modal.getByRole('button', { name: 'Alocar 2 usuários' }).click()

  await expect(page.getByText('2 usuários alocados.')).toBeVisible()
  await expect(modal).toHaveCount(0)
  await expect(
    sheet.getByRole('heading', { name: 'Operador de Campo (2)' }),
  ).toBeVisible()
  expect(
    state.requests.find(
      (r) => r.method === 'PUT' && r.path === '/sectors/3/members',
    )?.body,
  ).toEqual({ userIds: [2, 3], roleId: OPERADOR })
  expect(state.users[1].allocations).toEqual([
    { sectorId: 3, roleId: OPERADOR },
  ])
})

test('o modal de alocar busca por nome ou e-mail e seleciona todos os filtrados', async ({
  page,
}) => {
  const state = await setup(page)
  await page.goto('/sectors')
  const sheet = await openMembers(page, 'Tecnologia')
  await sheet.getByRole('button', { name: 'Adicionar usuários' }).click()
  const modal = page.getByRole('dialog', {
    name: 'Adicionar usuários a Tecnologia',
  })
  await modal.getByLabel('Buscar usuário').fill('ana@')
  await expect(
    modal.getByRole('checkbox', { name: 'Selecionar Ana' }),
  ).toBeVisible()
  await expect(
    modal.getByRole('checkbox', { name: 'Selecionar Maria' }),
  ).toHaveCount(0)
  await modal
    .getByRole('checkbox', { name: 'Selecionar todos os filtrados' })
    .click()
  await expect(modal).toContainText('1 selecionado')
  // A seleção sobrevive a uma nova busca.
  await modal.getByLabel('Buscar usuário').fill('')
  await expect(modal).toContainText('1 selecionado')
  await modal.getByLabel('Buscar usuário').fill('ninguém assim')
  await expect(modal).toContainText(
    'Nenhum usuário encontrado para esta busca.',
  )
  await modal.getByLabel('Buscar usuário').fill('')
  await modal
    .getByRole('checkbox', { name: 'Selecionar todos os filtrados' })
    .click()
  await expect(modal).toContainText('3 selecionados')
  await modal
    .getByRole('checkbox', { name: 'Selecionar todos os filtrados' })
    .click()
  await expect(modal).toContainText('Nenhum selecionado')
  expect(state.requests.filter((r) => r.method !== 'GET')).toHaveLength(0)
})

test('falha ao alocar mostra o erro no modal e mantém a seleção', async ({
  page,
}) => {
  const state = fixture()
  state.fail['PUT /sectors/3/members'] = 409
  await setup(page, state)
  await page.goto('/sectors')
  const sheet = await openMembers(page, 'Tecnologia')
  await sheet.getByRole('button', { name: 'Adicionar usuários' }).click()
  const modal = page.getByRole('dialog', {
    name: 'Adicionar usuários a Tecnologia',
  })
  await modal.getByRole('checkbox', { name: 'Selecionar Maria' }).click()
  await modal.getByRole('button', { name: 'Alocar 1 usuário' }).click()
  await expect(modal.getByRole('alert')).toContainText('entra em conflito')
  await expect(modal).toContainText('1 selecionado')
  expect(state.users[1].allocations).toEqual([])
})

test('administrador troca o cargo e remove um usuário do setor, com confirmação', async ({
  page,
}) => {
  const state = await setup(page)
  await page.goto('/sectors')
  const sheet = await openMembers(page, 'Patrimônio')
  await pickOption(sheet.getByLabel('Cargo de Ana'), 'Operador de Campo')
  await expect(page.getByText('Cargo alterado.')).toBeVisible()
  await expect(
    sheet.getByRole('heading', { name: 'Operador de Campo (1)' }),
  ).toBeVisible()
  expect(
    state.requests.find(
      (r) => r.method === 'PUT' && r.path === '/users/3/sectors/1',
    )?.body,
  ).toEqual({ roleId: OPERADOR })

  await sheet.getByRole('button', { name: 'Remover Camille do setor' }).click()
  const dialog = page.getByRole('dialog', {
    name: 'Remover do setor',
    exact: true,
  })
  await expect(dialog).toContainText('Camille')
  await dialog.getByRole('button', { name: 'Cancelar' }).click()
  expect(state.requests.some((r) => r.method === 'DELETE')).toBe(false)
  await sheet.getByRole('button', { name: 'Remover Camille do setor' }).click()
  await page
    .getByRole('dialog', { name: 'Remover do setor', exact: true })
    .getByRole('button', { name: 'Remover' })
    .click()
  await expect(page.getByText('Usuário removido do setor.')).toBeVisible()
  await expect(sheet).not.toContainText('Camille')
  expect(
    state.requests.filter((r) => r.method === 'DELETE').map((r) => r.path),
  ).toEqual(['/users/1/sectors/1'])
  expect(sectorIdsOf(state.users[0])).toEqual([])
})

test('gestor vê o botão só nos setores que gerencia', async ({ page }) => {
  const state = fixture()
  state.admin = false
  state.users[0].allocations = [alloc(1, GESTOR), alloc(3, CONSULTA)]
  state.viewOnly = [3]
  await setup(page, state)
  await page.goto('/sectors')
  await expect(
    card(page, 'Patrimônio').getByRole('button', {
      name: 'Usuários associados',
    }),
  ).toBeVisible()
  // Em Tecnologia ele só consulta: o setor aparece, mas sem a gestão de usuários.
  await expect(card(page, 'Tecnologia')).toBeVisible()
  await expect(
    card(page, 'Tecnologia').getByRole('button', {
      name: 'Usuários associados',
    }),
  ).toHaveCount(0)
})

test('gestor só atribui cargos sem gestão e não mexe em outro gestor', async ({
  page,
}) => {
  const state = fixture()
  state.admin = false
  state.users[0].allocations = [alloc(1, GESTOR)]
  state.users[1].allocations = [alloc(1, OPERADOR)]
  // Pedro acabou de entrar pelo Google: sem setor, e mesmo assim o gestor pode alocá-lo.
  state.users.push({
    id: 4,
    name: 'Pedro',
    email: 'pedro@example.com',
    administrator: false,
    allocations: [],
    active: true,
  })
  await setup(page, state)
  await page.goto('/sectors')
  const sheet = await openMembers(page, 'Patrimônio')
  // Ana é gestora do setor: sem seletor de cargo nem remover para quem não é administrador.
  await expect(sheet.getByLabel('Cargo de Ana')).toHaveCount(0)
  await expect(
    sheet.getByRole('button', { name: 'Remover Ana do setor' }),
  ).toHaveCount(0)
  // Maria é operadora: o gestor pode trocar o cargo dela, mas não para um cargo de gestão.
  await expect(sheet.getByLabel('Cargo de Maria')).toBeVisible()
  expect(await optionTexts(sheet.getByLabel('Cargo de Maria'))).toEqual([
    'Operador de Campo',
    'Consulta',
  ])
  await sheet.getByRole('button', { name: 'Adicionar usuários' }).click()
  const modal = page.getByRole('dialog', {
    name: 'Adicionar usuários a Patrimônio',
  })
  expect(await optionTexts(modal.getByLabel('Cargo dos usuários'))).toEqual([
    'Operador de Campo',
    'Consulta',
  ])
  await expect(
    modal.getByRole('checkbox', { name: 'Selecionar Pedro' }),
  ).toBeVisible()
  await modal.getByRole('checkbox', { name: 'Selecionar Pedro' }).click()
  await modal.getByRole('button', { name: 'Alocar 1 usuário' }).click()
  await expect(page.getByText('1 usuário alocado.')).toBeVisible()
  expect(state.users[3].allocations).toEqual([
    { sectorId: 1, roleId: CONSULTA },
  ])
})

test('mobile permite navegar e preencher formulários sem perder foco ao digitar', async ({
  page,
}) => {
  await setup(page)
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/sectors')
  await page.getByRole('button', { name: 'Novo setor' }).click()
  await page.getByLabel('Nome do setor').pressSequentially('Financeiro')
  await expect(page.getByLabel('Nome do setor')).toHaveValue('Financeiro')
  await expect(page.getByLabel('Nome do setor')).toBeFocused()
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBe(true)
})

test('falha de rede na listagem apresenta erro e permite tentar novamente', async ({
  page,
}) => {
  await setup(page)
  await page.route('http://127.0.0.1:18080/roles', (route) =>
    route.abort('failed'),
  )
  await page.goto('/roles')
  await expect(
    page.getByRole('alert').filter({ hasText: 'conectar ao servidor' }),
  ).toBeVisible()
  await expect(
    page.getByRole('button', { name: 'Tentar novamente' }),
  ).toBeVisible()
  await page.unroute('http://127.0.0.1:18080/roles')
  await page.getByRole('button', { name: 'Tentar novamente' }).click()
  await expect(
    page.getByRole('heading', { name: 'Cargos e permissões' }),
  ).toBeVisible()
})

test('resposta de listagem não concede acesso de administrador sem papel na sessão', async ({
  page,
}) => {
  const state = fixture()
  state.admin = false
  state.reader = false
  await setup(page, state)
  await page.route('http://127.0.0.1:18080/roles', (route) =>
    route.fulfill({
      status: 200,
      headers: { 'Access-Control-Allow-Origin': '*' },
      contentType: 'application/json',
      body: JSON.stringify({ message: 'OK' }),
    }),
  )
  await page.goto('/roles')
  await expect(
    page.getByRole('heading', { name: 'Acesso restrito' }),
  ).toBeVisible()
  await expect(page.getByRole('button', { name: 'Novo cargo' })).toHaveCount(0)
})

test('admin desativa e reativa um usuário, com confirmação', async ({
  page,
}) => {
  const state = await setup(page)
  await page.goto('/users')
  await card(page, 'Maria')
    .getByRole('button', { name: 'Gerenciar acesso' })
    .click()
  await page.getByRole('button', { name: 'Desativar usuário' }).click()
  await confirm(page, 'Desativar usuário')
  await expect(
    page.getByRole('button', { name: 'Reativar usuário' }),
  ).toBeVisible()
  expect(
    state.requests.some(
      (r) => r.method === 'PATCH' && r.path === '/users/2/deactivate',
    ),
  ).toBe(true)
  await page.getByRole('button', { name: 'Fechar diálogo' }).click()
  await expect(card(page, 'Maria')).toContainText('Inativo')

  await card(page, 'Maria')
    .getByRole('button', { name: 'Gerenciar acesso' })
    .click()
  await page.getByRole('button', { name: 'Reativar usuário' }).click()
  await confirm(page, 'Reativar usuário')
  await expect(
    page.getByRole('button', { name: 'Desativar usuário' }),
  ).toBeVisible()
  expect(
    state.requests.some(
      (r) => r.method === 'PATCH' && r.path === '/users/2/activate',
    ),
  ).toBe(true)
})

test('usuário não tem cadastro nem exclusão na tela', async ({ page }) => {
  const state = await setup(page)
  await page.goto('/users')
  await expect(card(page, 'Maria')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Novo usuário' })).toHaveCount(
    0,
  )
  await card(page, 'Maria')
    .getByRole('button', { name: 'Gerenciar acesso' })
    .click()
  await expect(page.getByRole('button', { name: /Excluir/ })).toHaveCount(0)
  await expect(
    page.getByRole('button', { name: 'Desativar usuário' }),
  ).toBeVisible()
  expect(
    state.requests.filter((r) => r.path === '/users' && r.method !== 'GET'),
  ).toHaveLength(0)
})

test('botão rápido desativa com confirmação e reativa sem confirmar', async ({
  page,
}) => {
  const state = await setup(page)
  await page.goto('/users')
  const maria = card(page, 'Maria')
  await expect(maria).toContainText('Ativo')

  await maria.getByRole('button', { name: 'Desativar Maria' }).click()
  // A confirmação explica as consequências, tem um só caminho de saída ("Cancelar") e põe o foco
  // nele, para um Enter distraído não desativar ninguém.
  const dialog = page.getByRole('dialog', {
    name: 'Desativar usuário',
    exact: true,
  })
  await expect(dialog).toContainText('Deseja desativar Maria?')
  await expect(dialog).toContainText('inclusive a sessão que já estiver aberta')
  await expect(dialog.getByRole('button', { name: 'Fechar' })).toHaveCount(0)
  await expect(dialog.getByRole('button', { name: 'Cancelar' })).toBeFocused()
  await confirm(page, 'Desativar usuário')
  await expect(maria).toContainText('Inativo')
  // O modal "Gerenciar acesso" não abre por causa do clique rápido.
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(
    maria.getByRole('button', { name: 'Ativar Maria' }),
  ).toBeVisible()
  expect(
    state.requests.some(
      (r) => r.method === 'PATCH' && r.path === '/users/2/deactivate',
    ),
  ).toBe(true)

  // Reativar não pede confirmação.
  await maria.getByRole('button', { name: 'Ativar Maria' }).click()
  await expect(maria).toContainText('Ativo')
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(
    maria.getByRole('button', { name: 'Desativar Maria' }),
  ).toBeVisible()
  expect(
    state.requests.some(
      (r) => r.method === 'PATCH' && r.path === '/users/2/activate',
    ),
  ).toBe(true)
})

test('botão rápido mostra o erro em um aviso e mantém a situação', async ({
  page,
}) => {
  const state = fixture()
  state.fail['PATCH /users/2/deactivate'] = 409
  await setup(page, state)
  await page.goto('/users')
  const maria = card(page, 'Maria')
  await maria.getByRole('button', { name: 'Desativar Maria' }).click()
  await confirm(page, 'Desativar usuário')
  await expect(page.getByText('A operação entra em conflito')).toBeVisible()
  await expect(maria).toContainText('Ativo')
  await expect(page.getByRole('dialog')).toHaveCount(0)
})

test('ninguém desativa a própria conta, nem pelo botão rápido', async ({
  page,
}) => {
  await setup(page)
  await page.goto('/users')
  await expect(
    card(page, 'Camille').getByRole('button', { name: /^(Desativar|Ativar) / }),
  ).toHaveCount(0)
  await card(page, 'Camille')
    .getByRole('button', { name: 'Gerenciar acesso' })
    .click()
  await expect(
    page.getByRole('button', { name: 'Desativar usuário' }),
  ).toHaveCount(0)
  await expect(page.getByText('própria conta')).toBeVisible()
})

test('gestor não acessa a tela de usuários, nem pelo endereço', async ({
  page,
}) => {
  const state = fixture()
  state.admin = false
  await setup(page, state)
  await page.goto('/users')
  await expect(
    page.getByRole('heading', { name: 'Acesso restrito' }),
  ).toBeVisible()
  await expect(
    page
      .getByRole('navigation', { name: 'Menu principal' })
      .getByRole('link', { name: 'Usuários', exact: true }),
  ).toHaveCount(0)
  // Nenhuma chamada de usuários é feita para quem não pode ver a tela.
  expect(
    state.requests.filter((r) => r.path.startsWith('/users')),
  ).toHaveLength(0)
})

test('modal de acesso mostra quem é o usuário, orienta quem não tem setor e deixa desativar por último', async ({
  page,
}) => {
  await setup(page)
  await page.goto('/users')
  await card(page, 'Maria')
    .getByRole('button', { name: 'Gerenciar acesso' })
    .click()
  const modal = page.getByRole('dialog', { name: 'Acesso de Maria' })
  // Identidade e estado no cabeçalho.
  await expect(modal).toContainText('maria@example.com')
  await expect(modal.getByText('Ativo', { exact: true })).toBeVisible()
  // Sem administrador marcado e sem setor: ela ainda não tem acesso a nada.
  await expect(
    modal.getByRole('checkbox', { name: 'Administrador' }),
  ).not.toBeChecked()
  await expect(modal).toContainText('Nenhum setor associado')
  // A ação de cuidado fica isolada no fim.
  expect(
    await modal.getByRole('heading', { level: 3 }).allTextContents(),
  ).toEqual(['Administrador', 'Setores e cargos (0)', 'Desativar conta'])
})

test('modal mostra o cargo do usuário em cada setor e abre o botão de desativar com confirmação', async ({
  page,
}) => {
  await setup(page)
  await page.goto('/users')
  await card(page, 'Ana')
    .getByRole('button', { name: 'Gerenciar acesso' })
    .click()
  const modal = page.getByRole('dialog', { name: 'Acesso de Ana' })
  await expect(
    modal.getByRole('checkbox', { name: 'Administrador' }),
  ).not.toBeChecked()
  await expect(
    modal.getByRole('heading', { name: /Setores e cargos \(2\)/ }),
  ).toBeVisible()
  await modal.getByRole('button', { name: 'Desativar usuário' }).click()
  await expect(
    page.getByRole('dialog', { name: 'Desativar usuário', exact: true }),
  ).toBeVisible()
})

test('modal de um usuário inativo mostra o estado e oferece reativar', async ({
  page,
}) => {
  const state = fixture()
  state.users[1].active = false
  await setup(page, state)
  await page.goto('/users')
  await card(page, 'Maria')
    .getByRole('button', { name: 'Gerenciar acesso' })
    .click()
  const modal = page.getByRole('dialog', { name: 'Acesso de Maria' })
  await expect(modal.getByText('Inativo', { exact: true })).toBeVisible()
  await expect(modal).toContainText('o usuário não consegue entrar')
  await expect(
    modal.getByRole('button', { name: 'Reativar usuário' }),
  ).toBeVisible()
  await expect(
    modal.getByRole('button', { name: 'Desativar usuário' }),
  ).toHaveCount(0)
})
