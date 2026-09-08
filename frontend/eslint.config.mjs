import { dirname } from 'path'
import { fileURLToPath } from 'url'
import { FlatCompat } from '@eslint/eslintrc'
import nextConfig from 'eslint-config-next'
import nextCoreWebVitals from 'eslint-config-next/core-web-vitals'
import nextTypescript from 'eslint-config-next/typescript'

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)

const compat = new FlatCompat({
  baseDirectory: __dirname,
})

// A partir da v16, eslint-config-next exporta flat config nativo (array),
// não mais um shareable config no formato antigo do .eslintrc — por isso
// entra direto no array, sem passar pelo FlatCompat.extends(). O rocketseat
// continua no formato antigo, então esse aqui ainda precisa do compat. A
// variante certa para conviver com o Next.js é a /next (documentada no
// próprio README do pacote), não a /react.
const nextEntries = [...nextConfig, ...nextCoreWebVitals, ...nextTypescript]
const rocketseatEntries = compat.extends('@rocketseat/eslint-config/next')

// eslint-config-next e @rocketseat/eslint-config/next bundlam cópias próprias
// de alguns plugins (ex.: jsx-a11y, import, @typescript-eslint). O ESLint 9
// recusa dois registros do mesmo nome de plugin no flat config, de propósito
// — para pegar mistura acidental de versões.
//
// Em vez de descartar um dos dois (o que perderia as regras daquele lado),
// renomeia o registro do rocketseat para um nome próprio (ex.: "jsx-a11y" ->
// "jsx-a11y-rocketseat") só onde há conflito real, e ajusta as regras dele
// para o nome novo. As duas cópias do plugin continuam carregadas e ativas,
// cada preset com a sua.
const nextPluginNames = new Set(nextEntries.flatMap((e) => Object.keys(e.plugins ?? {})))

const renamedRocketseatEntries = rocketseatEntries.map((entry) => {
  const conflicting = Object.keys(entry.plugins ?? {}).filter((name) =>
    nextPluginNames.has(name),
  )
  if (conflicting.length === 0) return entry

  const rename = (name) => (conflicting.includes(name) ? `${name}-rocketseat` : name)

  return {
    ...entry,
    plugins: Object.fromEntries(
      Object.entries(entry.plugins).map(([name, plugin]) => [rename(name), plugin]),
    ),
    rules: Object.fromEntries(
      Object.entries(entry.rules ?? {}).map(([ruleId, value]) => {
        const [pluginName, ...rest] = ruleId.split('/')
        const newRuleId = conflicting.includes(pluginName)
          ? [rename(pluginName), ...rest].join('/')
          : ruleId
        return [newRuleId, value]
      }),
    ),
  }
})

const eslintConfig = [
  ...nextEntries,
  ...renamedRocketseatEntries,
  {
    rules: {
      '@typescript-eslint/no-empty-function': 'off',
      'react/no-unescaped-entities': 'off',
      '@next/next/no-page-custom-font': 'off',
      'react-hooks/exhaustive-deps': 'off',
    },
  },
]

export default eslintConfig
