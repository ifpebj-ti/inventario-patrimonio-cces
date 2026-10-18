import { expect, test, type Page } from '@playwright/test'
import { expandFilters, pickOption } from './select'

const fixture = () => ({
  admin: true,
  reader: true,
  fail: {} as Record<string, number>,
  requests: [] as {
    method: string
    path: string
    body: Record<string, unknown> | null
  }[],
  sectors: [
    { id: 1, name: 'Patrimônio', code: 'PAT', active: true },
    { id: 2, name: 'Biblioteca', code: null, active: false },
    { id: 3, name: 'Tecnologia', code: 'TI', active: true },
  ],
  profiles: [
    {
      id: 11,
      name: 'ADMIN_ORGANIZATION',
      description: 'Administrador',
      permissionIds: [201],
    },
    {
      id: 12,
      name: 'GESTOR_SETOR',
      description: 'Gestor',
      permissionIds: [202],
    },
    {
      id: 13,
      name: 'OPERADOR_CAMPO',
      description: 'Operador',
      permissionIds: [203],
    },
    {
      id: 14,
      name: 'CONSULTA',
      description: null,
      permissionIds: [] as number[],
    },
    {
      id: 15,
      name: 'COORDENACAO',
      description: 'Perfil personalizado',
      permissionIds: [201],
    },
  ],
  permissions: [
    { id: 201, name: 'ADMIN', description: 'Administração' },
    { id: 202, name: 'MANAGE_SECTOR', description: 'Gestão de setores' },
    { id: 203, name: 'MANAGE_ITEM', description: 'Gestão de itens' },
  ],
  users: [
    {
      id: 1,
      name: 'Camille',
      email: 'camille@example.com',
      sectorIds: [1],
      profileId: 15 as number | null,
      active: true,
    },
    {
      id: 2,
      name: 'Maria',
      email: 'maria@example.com',
      sectorIds: [] as number[],
      profileId: null as number | null,
      active: true,
    },
    {
      id: 3,
      name: 'Ana',
      email: 'ana@example.com',
      sectorIds: [1, 2],
      profileId: 12 as number | null,
      active: true,
    },
  ],
})

const setup = async (page: Page, state = fixture()) => {
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
    if (path === '/auth/me')
      return send(200, {
        ...state.users[0],
        permissions: [
          ...(state.admin ? ['ADMIN'] : []),
          ...(!state.admin && state.reader ? ['MANAGE_SECTOR'] : []),
        ],
      })
    if (path === '/inventory/user-inventories') return send(200, [])
    if (
      path.startsWith('/profiles') ||
      path === '/permissions' ||
      path === '/users'
    ) {
      if (!state.admin && !state.reader)
        return send(403, { error: 'Forbidden' })
    }
    if (path.startsWith('/sectors') && !state.reader && !state.admin)
      return send(403, { error: 'Forbidden' })
    if (path === '/permissions') return send(200, state.permissions)
    if (path === '/profiles' && method === 'GET')
      return send(200, state.profiles)
    if (path === '/users' && method === 'GET' && !state.admin)
      // O gestor lista apenas os usuários que compartilham algum setor com ele.
      return send(
        200,
        state.users.filter((user) =>
          user.sectorIds.some((id) => state.users[0].sectorIds.includes(id)),
        ),
      )
    if (path === '/users' && method === 'GET')
      return send(
        200,
        url.searchParams.has('profileId')
          ? state.users.filter(
              (user) =>
                user.profileId === Number(url.searchParams.get('profileId')),
            )
          : state.users,
      )
    if (path === '/sectors' && method === 'GET')
      return send(
        200,
        state.admin
          ? state.sectors
          : state.sectors.filter((sector) =>
              state.users[0].sectorIds.includes(sector.id),
            ),
      )
    const sectorMembers = path.match(/^\/sectors\/(\d+)\/users$/)
    if (sectorMembers)
      return send(
        200,
        state.users.filter((user) =>
          user.sectorIds.includes(Number(sectorMembers[1])),
        ),
      )
    if (path === '/sectors' && method === 'POST') {
      const sector = {
        id: 4,
        name: String(body?.name),
        code: body?.code as string | null,
        active: true,
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
      return send(200, user)
    }
    const userProfile = path.match(/^\/users\/(\d+)\/profile$/)
    if (userProfile) {
      const user = state.users.find((row) => row.id === Number(userProfile[1]))!
      user.profileId = body?.profileId as number | null
      if (user.id === 1 && user.profileId !== 11 && user.profileId !== 15) {
        // Sai de administrador: só o perfil de gestor mantém a gestão de usuários.
        state.admin = false
        state.reader = user.profileId === 12
      }
      return send(200, user)
    }
    const userSector = path.match(/^\/users\/(\d+)\/sectors\/(\d+)$/)
    if (userSector) {
      const user = state.users.find((row) => row.id === Number(userSector[1]))!
      const id = Number(userSector[2])
      user.sectorIds = user.sectorIds.filter((value) => value !== id)
      if (method === 'POST') user.sectorIds.push(id)
      return send(200, user)
    }
    if (path === '/profiles' && method === 'POST') {
      const profile = {
        id: 16,
        name: String(body?.name),
        description: String(body?.description || ''),
        permissionIds: [] as number[],
      }
      state.profiles.push(profile)
      return send(201, profile)
    }
    const profileUpdate = path.match(
      /^\/profiles\/(\d+)(?:\/permissions\/(\d+))?$/,
    )
    if (profileUpdate) {
      const profile = state.profiles.find(
        (row) => row.id === Number(profileUpdate[1]),
      )!
      if (profileUpdate[2]) {
        const id = Number(profileUpdate[2])
        profile.permissionIds = profile.permissionIds.filter(
          (value) => value !== id,
        )
        if (method === 'POST') profile.permissionIds.push(id)
        if (profile.id === 15 && !profile.permissionIds.includes(201))
          state.admin = false
      } else if (method === 'DELETE') {
        state.profiles = state.profiles.filter((row) => row.id !== profile.id)
        return send(204, null)
      } else if (method === 'PUT') {
        profile.name = String(body?.name)
        profile.description = String(body?.description || '')
      }
      return send(200, profile)
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
      name: /^(Confirmar|Desativar|Reativar|Ativar|Alterar perfil|Alocar|Desalocar)$/,
    })
    .click()

test('perfil personalizado com ADMIN recebe menus administrativos', async ({
  page,
}) => {
  await setup(page)
  await page.goto('/sectors')
  await expect(
    page.getByRole('button', { name: 'Novo setor', exact: true }),
  ).toBeEnabled()
  await expect(
    page.getByRole('link', { name: 'Usuários', exact: true }),
  ).toBeVisible()
  await expect(
    page.getByRole('link', { name: 'Perfis e permissões', exact: true }),
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
  await page.goto('/users')
  await expect(page.getByRole('heading', { name: 'Usuários' })).toBeVisible()
  // O gestor lista só os usuários do seu setor e não vê o catálogo de perfis.
  await expect(card(page, 'Ana')).toBeVisible()
  await expect(card(page, 'Maria')).toHaveCount(0)
  await expect(
    page.getByRole('link', { name: 'Perfis e permissões' }),
  ).toHaveCount(0)
  // Outro gestor do mesmo setor só pode ser alterado pelo administrador.
  await expect(card(page, 'Ana')).toContainText(
    'Somente o administrador altera este acesso.',
  )
  // Usuário só nasce no primeiro login pelo Google: não há cadastro na tela.
  await expect(page.getByRole('button', { name: 'Novo usuário' })).toHaveCount(
    0,
  )
  // O gestor só atribui Operador de Campo e Consulta, nunca gestor nem administrador.
  await expect(card(page, 'Ana')).toContainText(
    'Somente o administrador altera este acesso.',
  )
})

test('gestor sem setor tem lista vazia', async ({ page }) => {
  const state = fixture()
  state.admin = false
  state.users[0].sectorIds = []
  await setup(page, state)
  await page.goto('/sectors')
  await expect(page.getByText('0 setores encontrados')).toBeVisible()
  await expect(page.locator('article')).toHaveCount(0)
})

test('gestor vinculado a múltiplos setores vê todos os seus vínculos', async ({
  page,
}) => {
  const state = fixture()
  state.admin = false
  state.users[0].sectorIds = [1, 3]
  await setup(page, state)
  await page.goto('/sectors')
  await expect(card(page, 'Patrimônio')).toBeVisible()
  await expect(card(page, 'Tecnologia')).toBeVisible()
  await expect(card(page, 'Biblioteca')).toHaveCount(0)
})

test('consulta e sem perfil não recebem acesso direto às telas administrativas', async ({
  page,
}) => {
  const state = fixture()
  state.admin = false
  state.reader = false
  state.users[0].profileId = null
  await setup(page, state)
  for (const path of ['/sectors', '/profiles', '/users']) {
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
  expect(state.users[2].sectorIds).toEqual([1, 2])
  await card(page, 'Patrimônio')
    .getByRole('button', { name: 'Ativar', exact: true })
    .click()
  await confirm(page, 'Ativar setor')
  await expect(card(page, 'Patrimônio')).toContainText('Ativo')
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

test('filtros de setor, perfil e busca se combinam', async ({ page }) => {
  await setup(page)
  await page.goto('/users')
  await expandFilters(page)
  await expect(card(page, 'Ana')).toBeVisible()
  await pickOption(page.getByLabel('Filtrar por setor'), 'Sem setor')
  await expect(card(page, 'Maria')).toBeVisible()
  await expect(card(page, 'Ana')).toHaveCount(0)
  await pickOption(page.getByLabel('Filtrar por setor'), 'Patrimônio')
  await pickOption(page.getByLabel('Filtrar por perfil'), 'Gestor de Setor')
  await expect(card(page, 'Ana')).toBeVisible()
  await expect(card(page, 'Camille')).toHaveCount(0)
  await page.getByLabel('Buscar usuário').fill('ana@example.com')
  await expect(card(page, 'Ana')).toBeVisible()
})

test('usuários filtram Sem perfil e enviam profileId ao servidor', async ({
  page,
}) => {
  const state = await setup(page)
  await page.goto('/users')
  await expandFilters(page)
  await expect(card(page, 'Maria')).toContainText('Sem perfil')
  await pickOption(page.getByLabel('Filtrar por perfil'), 'Sem perfil')
  await expect(card(page, 'Camille')).toHaveCount(0)
  await expect(card(page, 'Maria')).toBeVisible()
  await pickOption(page.getByLabel('Filtrar por perfil'), 'Gestor de Setor')
  await expect(card(page, 'Ana')).toBeVisible()
  await expect(card(page, 'Maria')).toHaveCount(0)
  expect(
    state.requests.filter((request) => request.path === '/users').length,
  ).toBeGreaterThan(1)
})

test('perfil e múltiplos setores são alterados somente após confirmação', async ({
  page,
}) => {
  const state = await setup(page)
  await page.goto('/users')
  await card(page, 'Maria')
    .getByRole('button', { name: 'Gerenciar acesso' })
    .click()
  await pickOption(page.getByLabel('Perfil do usuário'), 'Gestor de Setor')
  await page
    .getByRole('button', { name: 'Confirmar alteração de perfil' })
    .click()
  expect(state.users[1].profileId).toBeNull()
  await confirm(page, 'Alterar perfil do usuário')
  await expect(page.getByLabel('Perfil do usuário')).toContainText(
    'Gestor de Setor',
  )
  await pickOption(page.getByLabel('Adicionar setor'), 'Patrimônio')
  await page.getByRole('button', { name: 'Alocar no setor' }).click()
  await confirm(page, 'Alocar usuário')
  await expect(
    page.getByRole('button', { name: 'Desalocar de Patrimônio' }),
  ).toBeVisible()
  await pickOption(page.getByLabel('Adicionar setor'), 'Tecnologia')
  await page.getByRole('button', { name: 'Alocar no setor' }).click()
  await confirm(page, 'Alocar usuário')
  await expect(
    page.getByRole('button', { name: 'Desalocar de Tecnologia' }),
  ).toBeVisible()
  await page.getByRole('button', { name: 'Desalocar de Patrimônio' }).click()
  await confirm(page, 'Desalocar usuário')
  await expect(
    page.getByRole('button', { name: 'Desalocar de Patrimônio' }),
  ).toHaveCount(0)
  expect(state.users[1].sectorIds).toEqual([3])
  await expect(
    page
      .getByLabel('Adicionar setor')
      .getByRole('option', { name: 'Biblioteca' }),
  ).toHaveCount(0)
})

test('remover o próprio acesso administrativo bloqueia a tela e os menus', async ({
  page,
}) => {
  await setup(page)
  await page.goto('/users')
  await card(page, 'Camille')
    .getByRole('button', { name: 'Gerenciar acesso' })
    .click()
  await pickOption(page.getByLabel('Perfil do usuário'), 'Consulta')
  await page
    .getByRole('button', { name: 'Confirmar alteração de perfil' })
    .click()
  await expect(
    page.getByRole('dialog', { name: 'Alterar perfil do usuário' }),
  ).toContainText('perder seu acesso')
  await confirm(page, 'Alterar perfil do usuário')
  await expect(
    page.getByRole('heading', { name: 'Acesso restrito' }),
  ).toBeVisible()
  await expect(
    page
      .getByRole('navigation', { name: 'Menu principal' })
      .getByRole('link', { name: 'Usuários', exact: true }),
  ).toHaveCount(0)
})

test('catálogo exibe perfis e permissões somente para consulta', async ({
  page,
}) => {
  const state = await setup(page)
  await page.goto('/profiles')
  const table = page.getByRole('table', {
    name: 'Perfis de acesso e suas permissões',
  })
  await expect(table).toBeVisible()
  await expect(
    table.getByRole('row').filter({ hasText: 'Administrador da Organização' }),
  ).toContainText('Administrador')
  await expect(
    table.getByRole('row').filter({ hasText: 'Gestor de Setor' }),
  ).toContainText('Gerenciar Setor')
  await expect(
    table.getByRole('row').filter({ hasText: 'Consulta' }),
  ).toContainText('Nenhuma permissão atribuída')
  await expect(page.getByText('Somente para consulta')).toBeVisible()
  await expect(
    page.getByRole('button', {
      name: /Novo perfil|Editar perfil|Excluir perfil|Configurar permissões/,
    }),
  ).toHaveCount(0)
  await expect(page.getByRole('checkbox')).toHaveCount(0)
  expect(
    state.requests.filter(
      (request) =>
        request.path.startsWith('/profiles') && request.method !== 'GET',
    ),
  ).toHaveLength(0)
  await page.getByRole('link', { name: 'Gerenciar usuários' }).click()
  await expect(page.getByRole('heading', { name: 'Usuários' })).toBeVisible()
})

test('busca do catálogo filtra por perfil e permissão', async ({ page }) => {
  await setup(page)
  await page.goto('/profiles')
  const table = page.getByRole('table')
  await expect(table).toBeVisible()
  await page.getByLabel('Buscar perfil ou permissão').fill('MANAGE_ITEM')
  await expect(
    table.getByRole('rowheader', { name: 'Operador de Campo' }),
  ).toBeVisible()
  await expect(
    table.getByRole('rowheader', { name: 'Administrador da Organização' }),
  ).toHaveCount(0)
  await page.getByLabel('Buscar perfil ou permissão').fill('nao existe')
  await expect(
    page.getByText('Nenhum perfil encontrado para esta busca.'),
  ).toBeVisible()
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
  await page.route('http://127.0.0.1:18080/profiles', (route) =>
    route.abort('failed'),
  )
  await page.goto('/profiles')
  await expect(
    page.getByRole('alert').filter({ hasText: 'conectar ao servidor' }),
  ).toBeVisible()
  await expect(
    page.getByRole('button', { name: 'Tentar novamente' }),
  ).toBeVisible()
  await page.unroute('http://127.0.0.1:18080/profiles')
  await page.getByRole('button', { name: 'Tentar novamente' }).click()
  await expect(
    page.getByRole('heading', { name: 'Perfis e permissões' }),
  ).toBeVisible()
})

test('resposta de listagem não concede ADMIN sem permissão na sessão', async ({
  page,
}) => {
  const state = fixture()
  state.admin = false
  state.reader = false
  await setup(page, state)
  await page.route('http://127.0.0.1:18080/profiles', (route) =>
    route.fulfill({
      status: 200,
      headers: { 'Access-Control-Allow-Origin': '*' },
      contentType: 'application/json',
      body: JSON.stringify({ message: 'OK' }),
    }),
  )
  await page.goto('/profiles')
  await expect(
    page.getByRole('heading', { name: 'Acesso restrito' }),
  ).toBeVisible()
  await expect(page.getByRole('button', { name: 'Novo perfil' })).toHaveCount(0)
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

test('gestor não ativa nem desativa usuários, nem do próprio setor', async ({
  page,
}) => {
  const state = fixture()
  state.admin = false
  // Operador atuando só no setor do gestor (1): ainda assim só o administrador ativa ou desativa.
  state.users[1].sectorIds = [1]
  state.users[1].profileId = 13
  await setup(page, state)
  await page.goto('/users')
  await expect(
    card(page, 'Maria').getByRole('button', { name: /^(Desativar|Ativar) / }),
  ).toHaveCount(0)
  await card(page, 'Maria')
    .getByRole('button', { name: 'Gerenciar acesso' })
    .click()
  await expect(
    page.getByRole('button', { name: /Desativar usuário|Reativar usuário/ }),
  ).toHaveCount(0)
  await expect(
    page.getByText('Somente o administrador ativa ou desativa usuários.'),
  ).toBeVisible()
})

test('modal de acesso mostra quem é o usuário e deixa a situação da conta por último', async ({
  page,
}) => {
  await setup(page)
  await page.goto('/users')
  await card(page, 'Maria')
    .getByRole('button', { name: 'Gerenciar acesso' })
    .click()
  const modal = page.getByRole('dialog', { name: 'Acesso de Maria' })
  // Identidade e estado no topo.
  await expect(modal).toContainText('maria@example.com')
  await expect(modal.getByText('Ativo', { exact: true })).toBeVisible()
  await expect(modal.getByText('Sem perfil', { exact: true })).toBeVisible()
  // Quem ainda não tem perfil recebe a orientação e só altera depois de escolher um.
  await expect(modal).toContainText('Este usuário ainda não tem perfil')
  const change = modal.getByRole('button', {
    name: 'Confirmar alteração de perfil',
  })
  await expect(change).toBeDisabled()
  await pickOption(modal.getByLabel('Perfil do usuário'), 'Consulta')
  await expect(change).toBeEnabled()
  // A ação de cuidado (desativar) fica isolada no fim.
  expect(
    await modal.getByRole('heading', { level: 3 }).allTextContents(),
  ).toEqual(['Perfil de acesso', 'Setores associados (0)', 'Situação da conta'])
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
