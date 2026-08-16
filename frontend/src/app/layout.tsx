// Importa o tipo 'Metadata' do Next.js para definir os metadados da página.
import type { Metadata } from 'next'
// Importa o arquivo de estilos globais da aplicação.
import './globals.css'
// Importa o provedor de contexto de autenticação.
import { AuthProvider } from '@/contexts/Auth'
// Importa o componente que renderiza as notificações (toasts).
import { Toaster } from 'react-hot-toast'
// Importa o wrapper que controla as animações de transição de página.
import MotionWrapper from '@/components/template/motion'

// Objeto de metadados estáticos para SEO e para o navegador.
// Define o título e a descrição padrão para todas as páginas da aplicação.
export const metadata: Metadata = {
  title: 'Inventarium',
  description:
    'Gerencie os inventários da sua corporação com a melhor aplicação',
}

// Este é o Layout Raiz (RootLayout). Ele envolve TODAS as páginas da sua aplicação.
// É o local ideal para colocar provedores de contexto e componentes globais.
export default function RootLayout({
  children, // 'children' representa o conteúdo da página atual que será renderizada.
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html lang="pt-br">
      {/* As classes no body são aplicadas a toda a aplicação. */}
      <body className="antialiased min-h-screen">
        {/* AuthProvider: Fornece o contexto de autenticação (usuário, status de login, etc.)
            para todos os componentes filhos. Deve ser um dos wrappers mais externos. */}
        <AuthProvider>
          {/* MotionWrapper: Componente responsável por aplicar as animações de transição
              quando o usuário navega entre as páginas. */}
          <MotionWrapper>
            <div>
              {/* '{children}' é onde o conteúdo da página específica (ex: Dashboard, Login) será inserido. */}
              {children}
              {/* Toaster: Componente para exibir notificações (toasts) em toda a aplicação.
                  Colocá-lo aqui garante que ele esteja sempre presente e pronto para ser usado. */}
              <Toaster position="top-right" />
            </div>
          </MotionWrapper>
        </AuthProvider>
      </body>
    </html>
  )
}
