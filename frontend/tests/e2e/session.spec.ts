import { expect, test, type Page, type Request } from '@playwright/test'

// Sair da conta encerra a sessão no servidor (POST /auth/logout com o token da sessão) e só depois
// apaga o cookie. O back-end recusa o token encerrado (testes em Java); aqui conferimos o que o
// navegador faz.
const TOKEN = 'token-da-sessao'

const setup = async (page: Page, onLogout: (request: Request) => void) => {
  await page.context().addCookies([
    {
      name: 'inventarium.token',
      value: TOKEN,
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
    if (path === '/auth/logout') {
      onLogout(route.request())
      return route.fulfill({
        status: 204,
        headers: { 'Access-Control-Allow-Origin': '*' },
      })
    }
    if (path === '/auth/me')
      return send(200, {
        id: 7,
        name: 'Pessoa',
        email: 'pessoa@example.com',
        administrator: true,
        sectorIds: [],
        allocations: [],
      })
    return send(200, [])
  })
}

const signOut = async (page: Page) => {
  await page.goto('/dashboard')
  await page.getByRole('button', { name: /Pessoa/ }).click()
  await page.getByRole('menuitem', { name: 'Sair' }).click()
  await page
    .getByRole('dialog')
    .getByRole('button', { name: 'Sair', exact: true })
    .click()
}

const tokenCookie = async (page: Page) =>
  (await page.context().cookies()).find((c) => c.name === 'inventarium.token')

test('sair avisa o servidor com o token da sessão e apaga o cookie', async ({
  page,
}) => {
  const logouts: Request[] = []
  await setup(page, (request) => logouts.push(request))

  await signOut(page)

  await expect(page).toHaveURL(/\/$/)
  await expect.poll(() => logouts.length).toBe(1)
  expect(logouts[0].method()).toBe('POST')
  expect(await logouts[0].headerValue('authorization')).toBe(`Bearer ${TOKEN}`)
  expect(await tokenCookie(page)).toBeUndefined()
})

test('sair funciona mesmo quando o servidor recusa o logout', async ({
  page,
}) => {
  await setup(page, () => undefined)
  await page.route('http://127.0.0.1:18080/auth/logout', (route) =>
    route.fulfill({
      status: 401,
      headers: { 'Access-Control-Allow-Origin': '*' },
    }),
  )

  await signOut(page)

  await expect(page).toHaveURL(/\/$/)
  expect(await tokenCookie(page)).toBeUndefined()
})
