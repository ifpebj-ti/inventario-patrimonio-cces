'use client'

import { Button as UiButton } from '@/components/ui/button'

import { Menu } from 'lucide-react'
import { useSidebar } from '@/contexts/SidebarContext'
import { Breadcrumb } from '@/components/molecules/breadcrumb'

// Componente para o cabeçalho principal da aplicação autenticada
export const Header = () => {
  const { toggleMobile } = useSidebar()

  return (
    <header className="sticky top-0 z-30 w-full border-b bg-card/95 shadow-xs backdrop-blur-xs">
      <div className="mx-auto flex min-h-16 w-full max-w-[90rem] items-center px-4 py-2 sm:px-6">
        {/* Botão hambúrguer para abrir a barra lateral no mobile */}
        <UiButton
          variant="ghost"
          size="icon"
          onClick={toggleMobile}
          type="button"
          aria-label="Abrir menu de navegação"
          className="md:hidden mr-2 shrink-0 text-primary"
        >
          <Menu className="size-5" />
        </UiButton>

        {/* Trilha de navegação dinâmica por breadcrumbs e botão de retorno */}
        <Breadcrumb />
      </div>
    </header>
  )
}
