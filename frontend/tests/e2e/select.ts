import type { Locator, Page } from '@playwright/test'

// Escolhe uma opção em um Select ou Combobox do shadcn/ui:
// abre o gatilho e clica no item pelo texto visível.
export async function pickOption(trigger: Locator, option: string) {
  await trigger.click()
  await trigger
    .page()
    .getByRole('option', { name: option, exact: true })
    .click()
}

// Expande o painel de filtros recolhível (setores e usuários), se existir na tela.
export async function expandFilters(page: Page) {
  await page
    .getByRole('button', { name: 'Expandir filtros' })
    .click({ timeout: 5000 })
    .catch(() => undefined)
}

// Lê os textos das opções de um Select/Combobox abrindo e fechando a lista.
export async function optionTexts(trigger: Locator) {
  await trigger.click()
  const texts = await trigger.page().getByRole('option').allTextContents()
  await trigger.page().keyboard.press('Escape')
  return texts.map((text) => text.trim())
}
