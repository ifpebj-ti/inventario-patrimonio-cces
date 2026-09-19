import type { Metadata } from 'next'
import '../globals.css'
import { Toaster } from 'react-hot-toast'
import AuthenticatedOnlyFeatureWrapper from '@/components/template/authenticated'
import { Header } from '@/components/molecules/header'
import { SidebarProvider } from '@/contexts/SidebarContext'
import { InventoryProvider } from '@/contexts/InventoryContext'
import { Sidebar } from '@/components/organisms/sidebar'

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
      <InventoryProvider>
        <SidebarProvider>
          <div className="flex min-h-screen w-full bg-gray-50">
            <Sidebar />
            <div className="flex-1 min-w-0 flex flex-col w-full">
              <Header />
              <main className="flex-1 w-full">{children}</main>
            </div>
            <Toaster position="top-right" />
          </div>
        </SidebarProvider>
      </InventoryProvider>
    </AuthenticatedOnlyFeatureWrapper>
  )
}
