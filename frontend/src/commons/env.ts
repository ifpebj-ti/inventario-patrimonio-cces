// O Next substitui `process.env.NEXT_PUBLIC_*` pelo literal em tempo de BUILD.
// A expressão precisa aparecer inteira e estática: desestruturar ou indexar por
// variável quebra a substituição e o valor chega undefined no browser.
export const GOOGLE_CLIENT_ID = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID ?? ''

// Usado apenas no texto exibido ao usuário. O filtro real é o
// GOOGLE_ALLOWED_DOMAINS do backend; se aquela lista mudar, mude este rótulo.
export const INSTITUTIONAL_DOMAIN_LABEL = '@ifpe.edu.br'
