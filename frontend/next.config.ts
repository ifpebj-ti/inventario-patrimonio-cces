import type { NextConfig } from 'next'
import { PHASE_PRODUCTION_BUILD } from 'next/constants'

const nextConfig: NextConfig = {
  typescript: {
    ignoreBuildErrors: true,
  },
  eslint: {
    ignoreDuringBuilds: true,
  },
}

export default (phase: string): NextConfig => {
  // O Next inlina NEXT_PUBLIC_* no bundle durante o build; definir a variável
  // só no runtime não surte efeito. Falhamos aqui em vez de publicar uma imagem
  // com a tela de login sem botão do Google.
  //
  // A checagem é restrita à fase de build: o `next start` relê este arquivo em
  // um contêiner onde a variável já não existe, e isso é esperado.
  if (
    phase === PHASE_PRODUCTION_BUILD &&
    !process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID
  ) {
    throw new Error(
      'NEXT_PUBLIC_GOOGLE_CLIENT_ID não definido. Passe como build arg:\n' +
        '  docker build --build-arg NEXT_PUBLIC_GOOGLE_CLIENT_ID=... ./frontend\n' +
        'ou defina em frontend/.env.local para desenvolvimento.',
    )
  }

  return nextConfig
}
