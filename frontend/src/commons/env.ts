// O Next substitui `process.env.NEXT_PUBLIC_*` pelo literal em tempo de BUILD.
// A expressão precisa aparecer inteira e estática: desestruturar ou indexar por
// variável quebra a substituição e o valor chega undefined no browser.
export const GOOGLE_CLIENT_ID = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID ?? ''
export const API_URL =
  process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080'
