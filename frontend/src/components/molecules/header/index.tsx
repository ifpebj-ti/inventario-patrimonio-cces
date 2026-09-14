'use client'

import { useAuth } from '@/hooks/useAuth'
import { usePathname, useRouter } from 'next/navigation'
import { PiSignOutBold } from 'react-icons/pi'
import { FaArrowLeft } from 'react-icons/fa'
import { Menu } from 'lucide-react'
import { useSidebar } from '@/contexts/SidebarContext'

// Componente reutilizável para o cabeçalho principal da aplicação.
export const Header = () => {
  // Obtém a função de logout do contexto de autenticação global.
  const { signOut } = useAuth()
  const { toggleMobile } = useSidebar()
  // Hook do Next.js para controlar a navegação entre as páginas.
  const router = useRouter()
  const pathname = usePathname()
  // O dashboard é a página inicial de quem está logado: não há para onde
  // "voltar" dentro do fluxo autenticado, então a seta não aparece ali.
  const isDashboard = pathname === '/dashboard'

  return (
    // 'sticky' e 'top-0' mantêm o cabeçalho fixo no topo da página durante a rolagem.
    <header className="sticky flex flex-row justify-between md:justify-center items-center shadow p-4 top-0 z-40 bg-white">
      {/* Botão hambúrguer para abrir a barra lateral no mobile */}
      <button
        onClick={toggleMobile}
        aria-label="Abrir menu de navegação"
        className="md:hidden p-2 rounded-lg text-blue-400 hover:bg-blue-50 cursor-pointer outline-none focus-visible:ring-2 focus-visible:ring-blue-400"
      >
        <Menu className="w-7 h-7" />
      </button>

      {/* Ícone de seta para voltar para a página anterior no histórico do navegador. */}
      {!isDashboard && (
        <button
          onClick={() => router.back()}
          aria-label="Voltar para a página anterior"
          title="Voltar para a página anterior"
          className="absolute left-16 md:left-10"
          accessKey="b" // atalho intuitivo para back/Voltar
        >
          <FaArrowLeft className="text-2xl text-blue-400 cursor-pointer sm:text-4xl" />
        </button>
      )}
      {/* Título/logo principal da aplicação, que também é um link para o dashboard. */}
      <h1
        className="text-3xl sm:text-6xl text-blue-400 cursor-pointer"
        onClick={() => router.push('/dashboard')}
      >
        Inventarium
      </h1>
      {/* Ícone para executar a função de logout (sair) do sistema. */}
      <button
        onClick={signOut}
        aria-label="Sair da conta"
        title="Sair da conta"
        className="p-2 md:absolute md:right-10"
      >
        <PiSignOutBold className="text-2xl text-blue-400 cursor-pointer sm:text-4xl" />
      </button>
    </header>
  )
}
