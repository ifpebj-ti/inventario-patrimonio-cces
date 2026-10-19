import { expect, test, type Page } from '@playwright/test'

// Controle de acesso por cargo na interface. O back-end autoriza de fato (testes em Java); aqui
// conferimos o que cada cargo vê e consegue usar. O cargo vale por setor: 'dual' é gestor no setor
// 1 e só opera itens no setor 2; 'none' é quem entrou pelo Google e ainda não foi alocado.
type Role = 'admin' | 'manager' | 'operator' | 'consult'
type Persona = Role | 'dual' | 'none'

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
const allocation = (sectorId: number, role: keyof typeof roles) => ({
  sectorId,
  ...roles[role],
})
const allocationsOf = (persona: Persona) =>
  persona === 'admin' || persona === 'none'
    ? []
    : persona === 'dual'
      ? [allocation(1, 'manager'), allocation(2, 'operator')]
      : [allocation(1, persona)]

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
  itemCount: 0,
}

const inventoryB = {
  ...inventory,
  id: 2,
  name: 'Inventário B',
  sectorId: 2,
  sectorName: 'Setor B',
}

const item = {
  id: 1,
  code: 'PAT-1',
  description: 'Mesa',
  responsible: 'Maria',
  price: 'R$ 10,00',
  locale: 'Sala 1',
  isValid: false,
  observations: [] as unknown[],
}

const setup = async (page: Page, role: Persona) => {
  await page.context().addCookies([
    {
      name: 'inventarium.token',
      value: 'test-session',
      domain: '127.0.0.1',
      path: '/',
    },
  ])
  await page.route('http://127.0.0.1:18080/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    const send = (status: number, body: unknown) =>
      route.fulfill({
        status,
        contentType: 'application/json',
        headers: {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Headers': 'Authorization, Content-Type',
          'Access-Control-Allow-Methods':
            'GET, POST, PUT, PATCH, DELETE, OPTIONS',
        },
        body: JSON.stringify(body),
      })
    if (route.request().method() === 'OPTIONS') return send(204, null)
    if (path === '/auth/me')
      return send(200, {
        id: 7,
        name: 'Pessoa',
        email: 'pessoa@example.com',
        administrator: role === 'admin',
        sectorIds: allocationsOf(role).map((a) => a.sectorId),
        allocations: allocationsOf(role),
      })
    if (path === '/inventory/user-inventories')
      return send(200, role === 'dual' ? [inventory, inventoryB] : [inventory])
    if (path === '/inventory/1')
      return send(200, {
        inventory,
        canFinalize: role === 'admin' || role === 'manager' || role === 'dual',
      })
    if (path === '/inventory/2')
      return send(200, { inventory: inventoryB, canFinalize: false })
    if (path === '/inventory/inventory-items') return send(200, [])
    if (path === '/item/1') return send(200, item)
    if (path === '/sectors')
      return send(200, [
        { id: 1, name: 'Setor A', code: null, active: true },
        ...(role === 'dual'
          ? [{ id: 2, name: 'Setor B', code: null, active: true }]
          : []),
      ])
    return send(200, [])
  })
}

const menu = (page: Page) =>
  page.getByRole('navigation', { name: 'Menu principal' })

test.describe('menu lateral por cargo', () => {
  const expected: Record<Role, { visible: string[]; hidden: string[] }> = {
    admin: {
      visible: ['Dashboard', 'Setores', 'Usuários', 'Cargos e permissões'],
      hidden: [],
    },
    manager: {
      visible: ['Setores'],
      hidden: ['Dashboard', 'Usuários', 'Cargos e permissões'],
    },
    operator: {
      visible: [],
      hidden: ['Dashboard', 'Setores', 'Usuários', 'Cargos e permissões'],
    },
    consult: {
      visible: [],
      hidden: ['Dashboard', 'Setores', 'Usuários', 'Cargos e permissões'],
    },
  }
  for (const role of Object.keys(expected) as Role[]) {
    test(`cargo ${role}`, async ({ page }) => {
      await setup(page, role)
      await page.goto('/inventory')
      await expect(
        menu(page)
          .getByRole('link', { name: /inventários/i })
          .first(),
      ).toBeVisible()
      for (const name of expected[role].visible)
        await expect(menu(page).getByRole('link', { name })).toBeVisible()
      for (const name of expected[role].hidden)
        await expect(menu(page).getByRole('link', { name })).toHaveCount(0)
    })
  }
})

test.describe('lista de inventários', () => {
  for (const role of ['admin', 'manager'] as Role[]) {
    test(`${role} cria, edita e exclui inventários`, async ({ page }) => {
      await setup(page, role)
      await page.goto('/inventory')
      await expect(
        page.getByRole('button', { name: 'Novo Inventário' }),
      ).toBeVisible()
      await expect(
        page.getByRole('button', { name: 'Editar Inventário A', exact: true }),
      ).toBeVisible()
      await expect(
        page.getByRole('button', { name: 'Deletar Inventário A', exact: true }),
      ).toBeVisible()
    })
  }
  for (const role of ['operator', 'consult'] as Role[]) {
    test(`${role} só consulta os inventários`, async ({ page }) => {
      await setup(page, role)
      await page.goto('/inventory')
      await expect(
        page.getByRole('button', { name: 'Visualizar Inventário A' }),
      ).toBeVisible()
      await expect(
        page.getByRole('button', { name: 'Novo Inventário' }),
      ).toHaveCount(0)
      await expect(page.getByRole('button', { name: /^Editar / })).toHaveCount(
        0,
      )
      await expect(page.getByRole('button', { name: /^Deletar / })).toHaveCount(
        0,
      )
    })
  }
})

test.describe('detalhe do inventário', () => {
  const scan = (page: Page) =>
    page.getByRole('button', { name: 'Escanear ou digitar código' })
  const importButton = (page: Page) =>
    page.getByRole('button', { name: 'Importar Planilha CSV' })
  const clear = (page: Page) =>
    page.getByRole('button', { name: 'Inventário já está vazio' })

  test('gestor escaneia, importa e pode limpar itens', async ({ page }) => {
    await setup(page, 'manager')
    await page.goto('/inventory/1')
    await expect(scan(page)).toBeEnabled()
    await expect(importButton(page)).toBeEnabled()
    await expect(clear(page)).toBeVisible()
  })

  test('operador escaneia mas não importa nem limpa', async ({ page }) => {
    await setup(page, 'operator')
    await page.goto('/inventory/1')
    await expect(scan(page)).toBeEnabled()
    await expect(importButton(page)).toBeDisabled()
    await expect(
      page.getByText('Seu cargo neste setor não permite importar planilhas.'),
    ).toBeVisible()
    await expect(clear(page)).toHaveCount(0)
  })

  test('consulta é somente leitura', async ({ page }) => {
    await setup(page, 'consult')
    await page.goto('/inventory/1')
    await expect(scan(page)).toBeDisabled()
    await expect(
      page.getByText(
        'Seu cargo neste setor é somente leitura: não é possível escanear itens.',
      ),
    ).toBeVisible()
    await expect(importButton(page)).toBeDisabled()
    await expect(clear(page)).toHaveCount(0)
    // Pode exportar para acompanhar o andamento.
    await expect(
      page.getByRole('button', { name: 'Exportar planilha Excel' }),
    ).toBeVisible()
  })
})

test.describe('página do item', () => {
  test('gestor edita dados e observações', async ({ page }) => {
    await setup(page, 'manager')
    await page.goto('/inventory/1/item/1')
    await expect(
      page.getByRole('button', { name: 'Editar item' }),
    ).toBeVisible()
    await expect(
      page.getByRole('button', { name: 'Editar observações' }),
    ).toBeVisible()
  })

  test('operador só adiciona observações', async ({ page }) => {
    await setup(page, 'operator')
    await page.goto('/inventory/1/item/1')
    await expect(
      page.getByRole('button', { name: 'Editar observações' }),
    ).toBeVisible()
    await expect(page.getByRole('button', { name: 'Editar item' })).toHaveCount(
      0,
    )
  })

  test('consulta não altera nada', async ({ page }) => {
    await setup(page, 'consult')
    await page.goto('/inventory/1/item/1')
    await expect(page.getByText('Mesa').first()).toBeVisible()
    await expect(
      page.getByRole('button', { name: 'Editar observações' }),
    ).toHaveCount(0)
    await expect(page.getByRole('button', { name: 'Editar item' })).toHaveCount(
      0,
    )
  })
})

test.describe('telas restritas', () => {
  // A tela de Usuários é só do administrador: nem o gestor entra.
  for (const role of ['manager', 'operator', 'consult'] as Role[]) {
    test(`${role} não acessa usuários e é levado a Inventários fora do dashboard`, async ({
      page,
    }) => {
      await setup(page, role)
      await page.goto('/users')
      await expect(
        page.getByRole('heading', { name: 'Acesso restrito' }),
      ).toBeVisible()
      await expect(
        page.getByRole('link', { name: 'Ir para Inventários' }),
      ).toBeVisible()
      await page.goto('/dashboard')
      await expect(page).toHaveURL(/\/inventory$/)
    })
  }

  // Quem entra pela primeira vez com o Google nasce sem alocação e sem nenhuma permissão, até
  // alguém o alocar em um setor com um cargo: a interface o trata como sem acesso, sem erro.
  test('usuário sem alocação não acessa usuários nem dashboard', async ({
    page,
  }) => {
    await setup(page, 'none')
    await page.goto('/users')
    await expect(
      page.getByRole('heading', { name: 'Acesso restrito' }),
    ).toBeVisible()
    await page.goto('/dashboard')
    await expect(page).toHaveURL(/\/inventory$/)
  })

  test('gestor não acessa cargos e permissões', async ({ page }) => {
    await setup(page, 'manager')
    await page.goto('/roles')
    await expect(
      page.getByRole('heading', { name: 'Acesso restrito' }),
    ).toBeVisible()
  })
})

test.describe('cargo diferente em cada setor', () => {
  // O mesmo usuário é gestor no setor A (1) e só opera itens no setor B (2).
  test('lista: gerencia só o inventário do setor em que é gestor', async ({
    page,
  }) => {
    await setup(page, 'dual')
    await page.goto('/inventory')
    await expect(
      page.getByRole('button', { name: 'Novo Inventário' }),
    ).toBeVisible()
    await expect(
      page.getByRole('button', { name: 'Editar Inventário A', exact: true }),
    ).toBeVisible()
    await expect(
      page.getByRole('button', { name: 'Deletar Inventário A', exact: true }),
    ).toBeVisible()
    await expect(
      page.getByRole('button', { name: 'Visualizar Inventário B' }),
    ).toBeVisible()
    await expect(
      page.getByRole('button', { name: 'Editar Inventário B', exact: true }),
    ).toHaveCount(0)
    await expect(
      page.getByRole('button', { name: 'Deletar Inventário B', exact: true }),
    ).toHaveCount(0)
  })

  test('menu: ele vê Setores (é gestor em algum) mas não Usuários nem Cargos', async ({
    page,
  }) => {
    await setup(page, 'dual')
    await page.goto('/inventory')
    await expect(
      menu(page).getByRole('link', { name: 'Setores' }),
    ).toBeVisible()
    for (const name of ['Dashboard', 'Usuários', 'Cargos e permissões'])
      await expect(menu(page).getByRole('link', { name })).toHaveCount(0)
  })

  test('detalhe: importa no setor em que gerencia; no outro só escaneia', async ({
    page,
  }) => {
    await setup(page, 'dual')
    await page.goto('/inventory/1')
    await expect(
      page.getByRole('button', { name: 'Importar Planilha CSV' }),
    ).toBeEnabled()
    await page.goto('/inventory/2')
    await expect(
      page.getByRole('button', { name: 'Escanear ou digitar código' }),
    ).toBeEnabled()
    await expect(
      page.getByRole('button', { name: 'Importar Planilha CSV' }),
    ).toBeDisabled()
    await expect(
      page.getByText('Seu cargo neste setor não permite importar planilhas.'),
    ).toBeVisible()
  })

  test('item: edita dados no setor em que gerencia; no outro só observações', async ({
    page,
  }) => {
    await setup(page, 'dual')
    await page.goto('/inventory/1/item/1')
    await expect(
      page.getByRole('button', { name: 'Editar item' }),
    ).toBeVisible()
    await page.goto('/inventory/2/item/1')
    await expect(
      page.getByRole('button', { name: 'Editar observações' }),
    ).toBeVisible()
    await expect(page.getByRole('button', { name: 'Editar item' })).toHaveCount(
      0,
    )
  })
})
