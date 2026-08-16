import type { Metadata } from 'next'
import '../globals.css'
import { Toaster } from 'react-hot-toast'
import AuthenticatedOnlyFeatureWrapper from '@/components/template/authenticated'
import { Header } from '@/components/molecules/header'

export const metadata: Metadata = {
  title: 'Inventarium',
  description:
    'Gerencie os inventários da sua corporação com a melhor aplicação',
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    // Somente pessoas autenticadas podem acessar as páginas dentro de (authenticated)
    <AuthenticatedOnlyFeatureWrapper>
      <div>
        <Header></Header>
        {children}
        <Toaster position="top-right" />
      </div>
    </AuthenticatedOnlyFeatureWrapper>
  )
}
