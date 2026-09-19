'use client'

import { Menu } from 'lucide-react'
import { useSidebar } from '@/contexts/SidebarContext'
import { Breadcrumb } from '@/components/molecules/breadcrumb'

// Componente para o cabeçalho principal da aplicação autenticada
// Componente para o cabeçalho principal da aplicação autenticada
export const Header = () => {
  const { toggleMobile } = useSidebar()
  const { toggleMobile } = useSidebar()

  return (
    <header className="sticky top-0 z-30 flex items-center min-h-16 py-2 px-4 sm:px-6 bg-white/95 backdrop-blur-xs border-b border-gray-200/80 shadow-xs w-full">
      {/* Botão hambúrguer para abrir a barra lateral no mobile */}
      <button
        onClick={toggleMobile}
        type="button"
        aria-label="Abrir menu de navegação"
        className="md:hidden mr-2 p-2 rounded-xl text-blue-400 hover:bg-blue-50 hover:text-blue-500 transition-all duration-200 cursor-pointer outline-none focus-visible:ring-2 focus-visible:ring-blue-400 shrink-0"
        onClick={toggleMobile}
        type="button"
        aria-label="Abrir menu de navegação"
        className="md:hidden mr-2 p-2 rounded-xl text-blue-400 hover:bg-blue-50 hover:text-blue-500 transition-all duration-200 cursor-pointer outline-none focus-visible:ring-2 focus-visible:ring-blue-400 shrink-0"
      >
        <Menu className="w-5 h-5 shrink-0" />
        <Menu className="w-5 h-5 shrink-0" />
      </button>

      {/* Trilha de navegação dinâmica por breadcrumbs e botão de retorno */}
      <Breadcrumb />

      {/* Trilha de navegação dinâmica por breadcrumbs e botão de retorno */}
      <Breadcrumb />
    </header>
  )
}

