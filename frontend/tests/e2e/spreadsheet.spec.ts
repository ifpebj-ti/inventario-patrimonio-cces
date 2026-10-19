import { expect, test, type Page, type Route } from '@playwright/test'

const csv =
  'Código;Descrição;Responsável;Valor;Sala;Observações\n001;Monitor;TI;750,00;Sala 1;Bom\n'
const item = {
  id: 1,
  code: '001',
  description: 'Monitor',
  responsible: 'TI',
  price: 75000,
  locale: 'Sala 1',
  isValid: false,
}
const valid = {
  message: 'O arquivo é válido e está pronto para o processamento.',
}
const fullImport = {
  totalProcessed: 1,
  successes: 1,
  failures: 0,
  successfulItems: [],
  failedItems: [],
}

const respond = (route: Route, status: number, body: unknown) =>
  route.fulfill({
    status,
    headers: {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Headers': 'Authorization, Content-Type',
      'Access-Control-Allow-Methods': 'GET, POST, DELETE, OPTIONS',
    },
    contentType: 'application/json',
    body: status === 204 ? undefined : JSON.stringify(body),
  })

const select = (page: Page, content = csv, name = 'patrimonio.csv') =>
  page
    .getByLabel('Arquivo CSV', { exact: true })
    .setInputFiles({ name, mimeType: 'text/csv', buffer: Buffer.from(content) })

// Itens que a API devolve para o inventário; só os testes de limpeza começam com itens.
let hasItems = false
let finalized = false

test.beforeEach(async ({ page, context }) => {
  hasItems = false
  finalized = false
  await context.addCookies([
    {
      name: 'inventarium.token',
      value: 'test-session',
      domain: '127.0.0.1',
      path: '/',
    },
  ])
  await page.route('http://127.0.0.1:18080/**', (route) => {
    if (route.request().method() === 'OPTIONS') return respond(route, 204, null)
    const pathname = new URL(route.request().url()).pathname
    if (pathname === '/auth/me')
      return respond(route, 200, {
        id: 1,
        name: 'Camille',
        email: 'test@example.com',
        administrator: true,
        sectorIds: [],
        allocations: [],
      })
    if (pathname === '/inventory/user-inventories')
      return respond(route, 200, [
        {
          id: 1,
          name: 'Teste CSV',
          description: 'Teste',
          createdAt: '2026-10-12',
        },
      ])
    if (pathname === '/inventory/1')
      return respond(route, 200, {
        inventory: {
          id: 1,
          name: 'Teste CSV',
          description: 'Teste',
          createdAt: '12/10/2026',
          updatedAt: null,
          plannedEndDate: '2026-10-30',
          completedAt: finalized ? '2026-10-13T14:30:00' : null,
          status: finalized ? 'CONCLUIDO' : 'EM_ANDAMENTO',
          sectorId: 1,
          sectorName: 'Biblioteca',
          itemCount: 0,
        },
        canFinalize: false,
      })
    if (pathname === '/inventory/inventory-items')
      return respond(route, 200, hasItems ? [item] : [])
    if (pathname === '/inventory/validate-sheet')
      return respond(route, 200, valid)
    if (pathname === '/inventory/add-items')
      return respond(route, 201, fullImport)
    return respond(route, 200, [])
  })
  await page.goto('/inventory/1')
  await expect(
    page.getByRole('heading', { name: 'Importação', exact: true }),
  ).toBeVisible()
})

test('só importa após validar e confirmar; remove a aprovação e bloqueia submissões repetidas', async ({
  page,
}) => {
  let validations = 0
  let imports = 0
  await page.route('**/inventory/validate-sheet', async (route) => {
    validations++
    expect(route.request().postData()).toContain('001;Monitor')
    await new Promise((resolve) => setTimeout(resolve, 250))
    await respond(route, 200, valid)
  })
  await page.route('**/inventory/add-items', async (route) => {
    imports++
    expect(route.request().postData()).toContain('name="inventoryId"')
    await new Promise((resolve) => setTimeout(resolve, 250))
    await respond(route, 201, fullImport)
  })
  await select(page)
  const process = page.getByRole('button', { name: 'Processar', exact: true })
  await expect(process).toBeDisabled()
  await expect(process).toBeEnabled()
  expect(validations).toBe(1)
  expect(imports).toBe(0)
  await process.dblclick()
  await expect(page.getByTestId('import-counters')).toHaveText(
    'Total processado: 1 · Importados: 1 · Falhas: 0',
  )
  expect(imports).toBe(1)
  await expect(process).toBeDisabled()
  await page.getByRole('button', { name: 'Remover', exact: true }).click()
  await expect(process).toHaveCount(0)
})

for (const status of [400, 401, 403, 500]) {
  test(`HTTP ${status} nunca habilita Processar, mesmo com mensagem de sucesso`, async ({
    page,
  }) => {
    let imports = 0
    await page.route('**/inventory/validate-sheet', (route) =>
      respond(route, status, valid),
    )
    page.on('request', (request) => {
      if (request.url().endsWith('/inventory/add-items')) imports++
    })
    await select(page)
    await expect(page.locator('[role="alert"]').first()).toBeVisible()
    await expect(
      page.getByRole('button', { name: 'Processar', exact: true }),
    ).toBeDisabled()
    expect(imports).toBe(0)
  })
}

for (const body of [
  {},
  '<html>Falha</html>',
  { hasErrors: true, message: 'Recusado' },
  {
    message: 'Válido',
    errors: [{ code: '001', line: 2, errors: ['Campo inválido'] }],
  },
]) {
  test(`resposta 200 não aprovada bloqueia: ${JSON.stringify(body)}`, async ({
    page,
  }) => {
    await page.route('**/inventory/validate-sheet', (route) =>
      respond(route, 200, body),
    )
    await select(page)
    await expect(
      page.getByRole('button', { name: 'Processar', exact: true }),
    ).toBeDisabled()
    await expect(page.locator('[role="alert"]').first()).toBeVisible()
  })
}

test('falha de conexão e falha de leitura não aprovam o arquivo', async ({
  page,
}) => {
  await page.route('**/inventory/validate-sheet', (route) =>
    route.abort('failed'),
  )
  await select(page)
  await expect(
    page
      .getByText('Não foi possível conectar ao servidor.', { exact: false })
      .first(),
  ).toBeVisible()
  await expect(
    page.getByRole('button', { name: 'Processar', exact: true }),
  ).toBeDisabled()
  await page.evaluate(() => {
    File.prototype.text = async () => {
      throw new Error('read failure')
    }
  })
  await select(page)
  await expect(
    page
      .getByText('Não foi possível ler o arquivo CSV.', { exact: false })
      .first(),
  ).toBeVisible()
  await expect(
    page.getByRole('button', { name: 'Processar', exact: true }),
  ).toBeDisabled()
})

test('relatório de campos do backend mantém linha e código e permite nova validação', async ({
  page,
}) => {
  await page.route('**/inventory/validate-sheet', (route) =>
    respond(route, 400, {
      hasErrors: true,
      errors: [
        { code: '001', line: 2, errors: ['Código recusado no servidor'] },
      ],
    }),
  )
  await select(page)
  await page.getByRole('button', { name: 'Ver erros (1)' }).click()
  const dialog = page.getByRole('dialog', {
    name: 'Erros encontrados na planilha',
  })
  await expect(dialog).toContainText('Código: 001')
  await expect(dialog).toContainText('Linha da planilha: 2')
  await expect(dialog).toContainText('Código recusado no servidor')
  await dialog.getByRole('button', { name: 'Fechar', exact: true }).click()
  await page.route('**/inventory/validate-sheet', (route) =>
    respond(route, 200, valid),
  )
  await page.getByRole('button', { name: 'Validar novamente' }).click()
  await expect(
    page.getByRole('button', { name: 'Processar', exact: true }),
  ).toBeEnabled()
})

test('troca e remoção ignoram a resposta tardia do arquivo antigo', async ({
  page,
}) => {
  let release: (() => void) | undefined
  const pending = new Promise<void>((resolve) => {
    release = resolve
  })
  await page.route('**/inventory/validate-sheet', async (route) => {
    if (route.request().postData()?.includes('antigo.csv')) {
      await pending
      await respond(route, 200, valid).catch(() => {})
    } else await respond(route, 400, { message: 'Novo arquivo recusado' })
  })
  const request = page.waitForRequest('**/inventory/validate-sheet')
  await select(page, csv, 'antigo.csv')
  await request
  await select(page, csv, 'novo.csv')
  await expect(page.getByText('Novo arquivo recusado').first()).toBeVisible()
  release?.()
  await expect(
    page.getByRole('button', { name: 'Processar', exact: true }),
  ).toBeDisabled()
  await page.getByRole('button', { name: 'Remover', exact: true }).click()
  await expect(
    page.getByRole('button', { name: 'Processar', exact: true }),
  ).toHaveCount(0)
})

for (const [name, content, message] of [
  ['planilha.xlsx', csv, 'Formato não permitido'],
  ['vazio.csv', '', 'está vazio'],
  [
    'cabecalho.csv',
    'Descrição;Valor\nMonitor;100',
    'Coluna "Código" não encontrada',
  ],
  ['sem-itens.csv', 'Código;Valor\n', 'não contém itens'],
  ['codigo-vazio.csv', 'Código;Valor\n;100', 'Código não pode ser vazio'],
  [
    'codigo-longo.csv',
    `Código;Valor\n${'1'.repeat(31)};100`,
    'mais de 30 caracteres',
  ],
  ['negativo.csv', 'Código;Valor\n001;-100', 'Valor inválido'],
  ['preco.csv', 'Código;Valor\n001;abc', 'Valor inválido'],
  ['preco-ambiguo.csv', 'Código;Valor\n001;1.234', 'Valor inválido'],
  ['aspas.csv', 'Código;Valor\n"001;100', 'Aspas não fechadas'],
  [
    'duplicatas.csv',
    'Código;Valor\n001;100\n\n001;200',
    'primeira ocorrência na linha 2',
  ],
] as const) {
  test(`verificação local bloqueia ${name}`, async ({ page }) => {
    let validations = 0
    page.on('request', (request) => {
      if (request.url().endsWith('/inventory/validate-sheet')) validations++
    })
    await select(page, content, name)
    await expect(
      page.getByRole('button', { name: 'Processar', exact: true }),
    ).toBeDisabled()
    if (
      [
        'codigo-vazio.csv',
        'codigo-longo.csv',
        'negativo.csv',
        'preco.csv',
        'preco-ambiguo.csv',
        'aspas.csv',
        'duplicatas.csv',
      ].includes(name)
    ) {
      await page.getByRole('button', { name: /Ver erros/ }).click()
      await expect(page.getByRole('dialog')).toContainText(message)
      if (name === 'duplicatas.csv')
        await expect(page.getByRole('dialog')).toContainText(
          'Linha da planilha: 4',
        )
    } else
      await expect(page.locator('[role="alert"]').first()).toContainText(
        message,
      )
    expect(validations).toBe(0)
  })
}

test('drag-and-drop aceita BOM, delimitador entre aspas e aspas escapadas; modelo é baixado', async ({
  page,
}) => {
  await page.getByRole('button', { name: 'Colunas esperadas' }).click()
  const downloadPromise = page.waitForEvent('download')
  await page.getByRole('button', { name: /Baixar Modelo de Planilha/ }).click()
  expect((await downloadPromise).suggestedFilename()).toBe(
    'modelo_importacao_patrimonio.csv',
  )
  await page.getByRole('button', { name: 'Fechar modal', exact: true }).click()
  await page
    .getByLabel('Selecionar ou soltar planilha CSV')
    .evaluate((element, content) => {
      const transfer = new DataTransfer()
      transfer.items.add(new File([content], 'teste.CSV', { type: 'text/csv' }))
      element.dispatchEvent(
        new DragEvent('drop', { bubbles: true, dataTransfer: transfer }),
      )
    }, '\uFEFFCódigo;Descrição;Valor\n001;"Monitor; modelo ""novo""";"1.234,56"\n')
  await expect(
    page.getByRole('button', { name: 'Processar', exact: true }),
  ).toBeEnabled()
})

for (const legacy of [false, true]) {
  test(`sucesso parcial apresenta contadores e relatório (${legacy ? 'legado' : 'atual'})`, async ({
    page,
  }) => {
    const report: Record<string, unknown> = {
      totalProcessed: 2,
      successes: 1,
      failures: 1,
      failedItems: [
        {
          code: '002',
          line: 3,
          success: false,
          message: 'Item already exists in inventory',
        },
      ],
    }
    const body = legacy
      ? Object.fromEntries(
          Object.entries(report).map(([key, value]) => [`${key}\n`, value]),
        )
      : report
    await page.route('**/inventory/add-items', (route) =>
      respond(route, 206, body),
    )
    await select(page, csv + '002;Teclado;TI;100;Sala 1;Bom\n')
    await page.getByRole('button', { name: 'Processar', exact: true }).click()
    await expect(page.getByTestId('import-counters')).toContainText(
      'Importados: 1 · Falhas: 1',
    )
    await page.getByRole('button', { name: 'Ver erros (1)' }).click()
    await expect(
      page.getByRole('dialog', { name: 'Falhas da importação' }),
    ).toContainText('Item already exists in inventory')
  })
}

test('importação totalmente recusada preserva relatório e exige nova validação', async ({
  page,
}) => {
  await page.route('**/inventory/add-items', (route) =>
    respond(route, 400, {
      totalProcessed: 1,
      successes: 0,
      failures: 1,
      failedItems: [
        {
          code: '001',
          line: 2,
          success: false,
          message: 'Item already exists in inventory',
        },
      ],
    }),
  )
  await select(page)
  await page.getByRole('button', { name: 'Processar', exact: true }).click()
  await expect(page.getByTestId('import-counters')).toContainText(
    'Importados: 0 · Falhas: 1',
  )
  await expect(
    page.getByRole('button', { name: 'Processar', exact: true }),
  ).toBeDisabled()
  await page.getByRole('button', { name: 'Ver erros (1)' }).click()
  await expect(page.getByRole('dialog')).toContainText(
    'Item already exists in inventory',
  )
})

for (const status of [401, 403, 500]) {
  test(`falha ${status} na importação exige revalidar`, async ({ page }) => {
    await page.route('**/inventory/add-items', (route) =>
      respond(route, status, { message: 'Falha' }),
    )
    await select(page)
    await page.getByRole('button', { name: 'Processar', exact: true }).click()
    await expect(
      page.getByRole('button', { name: 'Validar novamente' }),
    ).toBeVisible()
    await expect(
      page.getByRole('button', { name: 'Processar', exact: true }),
    ).toBeDisabled()
  })
}

test('resposta de importação sem contadores não vira sucesso', async ({
  page,
}) => {
  await page.route('**/inventory/add-items', (route) => respond(route, 201, {}))
  await select(page)
  await page.getByRole('button', { name: 'Processar', exact: true }).click()
  await expect(
    page
      .getByText('Resposta de importação inválida.', { exact: false })
      .first(),
  ).toBeVisible()
  await expect(
    page.getByRole('button', { name: 'Processar', exact: true }),
  ).toBeDisabled()
})

test('limpeza requer confirmação; cancelamento e erro preservam itens; sucesso limpa seleção', async ({
  page,
}) => {
  let clears = 0
  hasItems = true
  await page.reload()
  await page.route('**/inventory/clear-items?*', async (route) => {
    clears++
    if (clears === 1) await respond(route, 403, { message: 'Recusado' })
    else await respond(route, 200, null)
  })
  await page
    .getByRole('button', { name: 'Limpar todos os itens do inventário' })
    .click()
  await page.getByRole('button', { name: 'Cancelar', exact: true }).click()
  expect(clears).toBe(0)
  await page
    .getByRole('button', { name: 'Limpar todos os itens do inventário' })
    .click()
  await page.getByRole('button', { name: 'Sim, limpar inventário' }).click()
  await expect(
    page
      .getByText('Você não tem permissão para realizar esta operação.')
      .first(),
  ).toBeVisible()
  await expect(
    page.getByRole('dialog', { name: 'Limpar Inventário' }),
  ).toBeVisible()
  await page.getByRole('button', { name: 'Sim, limpar inventário' }).click()
  await expect(
    page.getByRole('dialog', { name: 'Limpar Inventário' }),
  ).toHaveCount(0)
  await expect(
    page.getByRole('button', { name: 'Inventário já está vazio' }),
  ).toBeVisible()
  expect(clears).toBe(2)
})

test('remover durante a validação impede aprovação tardia', async ({
  page,
}) => {
  let release: (() => void) | undefined
  const pending = new Promise<void>((resolve) => {
    release = resolve
  })
  await page.route('**/inventory/validate-sheet', async (route) => {
    await pending
    await respond(route, 200, valid).catch(() => {})
  })
  const request = page.waitForRequest('**/inventory/validate-sheet')
  await select(page)
  await request
  await page.getByRole('button', { name: 'Remover', exact: true }).click()
  release?.()
  await expect(
    page.getByRole('button', { name: 'Processar', exact: true }),
  ).toHaveCount(0)
  await expect(
    page.getByText('Planilha validada.', { exact: false }),
  ).toHaveCount(0)
})

test('nova seleção perde a aprovação anterior enquanto valida', async ({
  page,
}) => {
  await select(page)
  await expect(
    page.getByRole('button', { name: 'Processar', exact: true }),
  ).toBeEnabled()
  let release: (() => void) | undefined
  const pending = new Promise<void>((resolve) => {
    release = resolve
  })
  await page.route('**/inventory/validate-sheet', async (route) => {
    await pending
    await respond(route, 400, { message: 'Arquivo recusado' })
  })
  await select(page, csv, 'outro.csv')
  await expect(
    page.getByRole('button', { name: 'Processar', exact: true }),
  ).toBeDisabled()
  release?.()
  await expect(
    page.getByRole('button', { name: 'Validar novamente' }),
  ).toBeVisible()
  await expect(
    page.getByRole('button', { name: 'Processar', exact: true }),
  ).toBeDisabled()
})

test('importação em andamento bloqueia troca de arquivo e limpeza', async ({
  page,
}) => {
  let release: (() => void) | undefined
  const pending = new Promise<void>((resolve) => {
    release = resolve
  })
  await page.route('**/inventory/add-items', async (route) => {
    await pending
    await respond(route, 201, fullImport)
  })
  await select(page)
  await page.getByRole('button', { name: 'Processar', exact: true }).click()
  await expect(
    page.getByRole('button', { name: 'Processando...', exact: true }),
  ).toBeDisabled()
  await expect(page.getByLabel('Arquivo CSV', { exact: true })).toBeDisabled()
  await expect(
    page.getByRole('button', { name: 'Inventário já está vazio' }),
  ).toBeDisabled()
  release?.()
  await expect(page.getByTestId('import-counters')).toBeVisible()
})

test('falha de conexão na importação preserva o arquivo e impede repetição automática', async ({
  page,
}) => {
  let imports = 0
  await page.route('**/inventory/add-items', (route) => {
    imports++
    return route.abort('failed')
  })
  await select(page)
  await page.getByRole('button', { name: 'Processar', exact: true }).click()
  await expect(
    page.getByRole('button', { name: 'Validar novamente' }),
  ).toBeVisible()
  await expect(
    page.getByRole('button', { name: 'Processar', exact: true }),
  ).toBeDisabled()
  await expect(page.getByText('patrimonio.csv', { exact: true })).toBeVisible()
  expect(imports).toBe(1)
})

test('interface mobile mantém validação e relatório acessíveis', async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await select(page, 'Código;Valor\n001;100\n001;200')
  await page.getByRole('button', { name: 'Ver erros (1)' }).click()
  await expect(page.getByRole('dialog')).toContainText('Código repetido')
  await page.getByRole('button', { name: 'Fechar', exact: true }).click()
  await expect(
    page.getByRole('button', { name: 'Processar', exact: true }),
  ).toBeDisabled()
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true)
})

test('limpeza em andamento bloqueia cliques repetidos e só remove itens após sucesso', async ({
  page,
}) => {
  let clears = 0
  hasItems = true
  await page.reload()
  let release: (() => void) | undefined
  const pending = new Promise<void>((resolve) => {
    release = resolve
  })
  await page.route('**/inventory/clear-items?*', async (route) => {
    clears++
    await pending
    await respond(route, 200, null)
  })
  await page
    .getByRole('button', { name: 'Limpar todos os itens do inventário' })
    .click()
  await page.getByRole('button', { name: 'Sim, limpar inventário' }).dblclick()
  await expect(
    page.getByRole('button', { name: 'Aguarde...', exact: true }),
  ).toBeDisabled()
  await expect(
    page.getByRole('button', { name: 'Cancelar', exact: true }),
  ).toBeDisabled()
  expect(clears).toBe(1)
  release?.()
  await expect(
    page.getByRole('button', { name: 'Inventário já está vazio' }),
  ).toBeDisabled()
})

for (const [name, content] of [
  ['virgula.csv', 'Código,Descrição,Valor\n001,Monitor,"750,00"'],
  ['tab.csv', 'Código\tDescrição\tValor\n001\tMonitor\t750.00'],
] as const) {
  test(`aceita separador ${name}`, async ({ page }) => {
    await select(page, content, name)
    await expect(
      page.getByRole('button', { name: 'Processar', exact: true }),
    ).toBeEnabled()
  })
}

test('contadores inconsistentes não viram sucesso', async ({ page }) => {
  await page.route('**/inventory/add-items', (route) =>
    respond(route, 201, { ...fullImport, totalProcessed: 3 }),
  )
  await select(page)
  await page.getByRole('button', { name: 'Processar', exact: true }).click()
  await expect(
    page.getByRole('button', { name: 'Validar novamente' }),
  ).toBeVisible()
  await expect(page.getByTestId('import-counters')).toHaveCount(0)
})

test('inventário com itens importados bloqueia nova importação até limpar os itens', async ({
  page,
}) => {
  hasItems = true
  await page.reload()
  await expect(page.getByLabel('Arquivo CSV', { exact: true })).toBeDisabled()
  await expect(
    page.getByRole('button', { name: 'Importar Planilha CSV' }),
  ).toBeDisabled()
  await expect(
    page.getByText(/já tem itens importados.*exclua primeiro todos os itens/),
  ).toBeVisible()
})

test('inventário finalizado bloqueia importar e escanear', async ({ page }) => {
  finalized = true
  await page.reload()
  await expect(
    page.getByText(
      'Inventário finalizado: não é possível importar novas planilhas.',
    ),
  ).toBeVisible()
  await expect(
    page.getByText('Inventário finalizado: não é possível escanear itens.'),
  ).toBeVisible()
  await expect(
    page.getByRole('button', { name: 'Importar Planilha CSV' }),
  ).toBeDisabled()
  await expect(
    page.getByRole('button', { name: 'Escanear ou digitar código' }),
  ).toBeDisabled()
})

test('inventário vazio e em andamento permite importar e escanear', async ({
  page,
}) => {
  await expect(page.getByLabel('Arquivo CSV', { exact: true })).toBeEnabled()
  await expect(
    page.getByRole('button', { name: 'Escanear ou digitar código' }),
  ).toBeEnabled()
})
