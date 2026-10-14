'use client'

import { Avatar, AvatarFallback } from '@/components/ui/avatar'
import { Button as UiButton } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetTitle,
} from '@/components/ui/sheet'
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip'
import { ConfirmationModal } from '@/components/organisms/modalConfirmation'

import { useState } from 'react'
import { homeRoute } from '@/lib/home-route'
import { roleLabel, roleOf } from '@/lib/access'
import Link from 'next/link'
import Image from 'next/image'
import { Menu, LogOut, ChevronsUpDown } from 'lucide-react'
import { useSidebar } from '@/contexts/SidebarContext'
import { useAuth } from '@/hooks/useAuth'
import logoImg from '../../../../public/logo.png'
import { SidebarNav } from './sidebarNav'

// Extrai as iniciais do nome do usuário (ou do email) para o avatar dinâmico
const getInitials = (name?: string, email?: string) => {
  if (name && name.trim()) {
    const parts = name.trim().split(/\s+/).filter(Boolean)
    if (parts.length === 1) {
      return parts[0].slice(0, 2).toUpperCase()
    }
    if (parts.length >= 2) {
      return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase()
    }
  }
  if (email && email.trim()) {
    const cleanEmail = email.trim().split('@')[0]
    return cleanEmail.slice(0, 2).toUpperCase()
  }
  return 'U'
}

const UserAvatar = ({
  initials,
  className = '',
}: {
  initials: string
  className?: string
}) => (
  <Avatar className={`rounded-lg ${className}`}>
    <AvatarFallback className="rounded-lg bg-primary/10 text-xs font-semibold text-primary">
      {initials}
    </AvatarFallback>
  </Avatar>
)

export function Sidebar() {
  const { isCollapsed, toggleCollapse, isMobileOpen, closeMobile } =
    useSidebar()
  const { user, signOut } = useAuth()

  // Estado para controlar o modal de confirmação de logout
  const [isLogoutModalOpen, setIsLogoutModalOpen] = useState(false)

  const initials = getInitials(user?.name, user?.email)
  const displayName =
    user?.name?.trim() || user?.email?.split('@')[0] || 'Usuário'
  const displayEmail = user?.email?.trim() || ''

  const handleConfirmLogout = () => {
    setIsLogoutModalOpen(false)
    closeMobile()
    signOut()
  }

  const userIdentity = (
    <div className="flex min-w-0 flex-1 flex-col text-left">
      <span
        className="truncate text-xs font-semibold text-foreground"
        title={displayName}
      >
        {displayName}
      </span>
      {displayEmail && (
        <span
          className="truncate text-[11px] font-normal text-muted-foreground"
          title={displayEmail}
        >
          {displayEmail}
        </span>
      )}
      {user && (
        <span className="truncate text-[11px] font-medium text-primary">
          {roleLabel[roleOf(user.permissions)]}
        </span>
      )}
    </div>
  )

  const brand = (size: number, onClick?: () => void) => (
    <Link
      href={homeRoute(user?.permissions)}
      onClick={onClick}
      className="flex min-w-0 items-center gap-2.5"
      title="Inventarium - Ir para a tela inicial"
    >
      <Image
        src={logoImg}
        alt="Inventarium"
        width={size}
        height={size}
        className="shrink-0 rounded-xl object-contain"
        priority
      />
      <h1 className="truncate font-brand text-2xl tracking-wide text-primary">
        Inventarium
      </h1>
    </Link>
  )

  return (
    <>
      {/* 1. Versão Desktop (>= 768px): Fixa na lateral esquerda */}
      <aside
        aria-label="Menu lateral de navegação"
        className={`sticky top-0 z-40 hidden h-screen shrink-0 flex-col border-r bg-card transition-all duration-300 ease-in-out md:flex ${
          isCollapsed ? 'w-24' : 'w-64'
        }`}
      >
        {!isCollapsed ? (
          /* Modo Expandido: Logo + Nome "Inventarium" à esquerda, Hambúrguer à direita */
          <div className="flex h-16 w-full flex-row items-center justify-between px-4 select-none">
            {brand(36)}
            <UiButton
              variant="ghost"
              size="icon"
              onClick={toggleCollapse}
              aria-label="Recolher menu lateral"
              className="ml-auto shrink-0 text-muted-foreground"
            >
              <Menu className="size-5" />
            </UiButton>
          </div>
        ) : (
          /* Modo Colapsado: Apenas a logo e o menu hambúrguer */
          <div className="flex h-16 w-full flex-row items-center justify-between px-3 select-none">
            <Link
              href={homeRoute(user?.permissions)}
              className="flex items-center justify-center"
              title="Ir para a tela inicial"
            >
              <Image
                src={logoImg}
                alt="Inventarium"
                width={32}
                height={32}
                className="shrink-0 rounded-xl object-contain"
                priority
              />
            </Link>

            <Tooltip>
              <TooltipTrigger asChild>
                <UiButton
                  variant="ghost"
                  size="icon-sm"
                  onClick={toggleCollapse}
                  aria-label="Expandir menu lateral"
                  className="shrink-0 text-muted-foreground"
                >
                  <Menu className="size-5" />
                </UiButton>
              </TooltipTrigger>
              <TooltipContent side="right">Expandir menu</TooltipContent>
            </Tooltip>
          </div>
        )}

        {/* Corpo dos itens de navegação */}
        <div
          className={`flex-1 py-2 ${
            isCollapsed
              ? 'overflow-visible'
              : 'overflow-x-hidden overflow-y-auto'
          }`}
        >
          <SidebarNav />
        </div>

        {/* Rodapé da Sidebar: menu do perfil */}
        <div className="mt-auto shrink-0 border-t p-2.5 select-none">
          <DropdownMenu>
            {!isCollapsed ? (
              <DropdownMenuTrigger asChild>
                <UiButton
                  variant="ghost"
                  className="h-auto w-full justify-start gap-2.5 p-2"
                >
                  <UserAvatar initials={initials} className="size-9" />
                  {userIdentity}
                  <ChevronsUpDown className="ml-auto text-muted-foreground" />
                </UiButton>
              </DropdownMenuTrigger>
            ) : (
              <div className="flex justify-center">
                <Tooltip>
                  <TooltipTrigger asChild>
                    <DropdownMenuTrigger asChild>
                      <UiButton
                        variant="ghost"
                        aria-label="Menu do perfil"
                        className="h-auto gap-1.5 p-1.5"
                      >
                        <UserAvatar initials={initials} />
                        <ChevronsUpDown className="size-3.5 text-muted-foreground" />
                      </UiButton>
                    </DropdownMenuTrigger>
                  </TooltipTrigger>
                  <TooltipContent side="right">
                    Perfil ({displayName})
                  </TooltipContent>
                </Tooltip>
              </div>
            )}
            <DropdownMenuContent
              side={isCollapsed ? 'right' : 'top'}
              align={isCollapsed ? 'end' : 'center'}
              className="w-60 rounded-xl"
            >
              <DropdownMenuLabel className="flex items-center gap-3 font-normal">
                <UserAvatar initials={initials} className="size-10" />
                {userIdentity}
              </DropdownMenuLabel>
              <DropdownMenuSeparator />
              <DropdownMenuItem
                variant="destructive"
                onSelect={() => setIsLogoutModalOpen(true)}
              >
                <LogOut />
                Sair
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </aside>

      {/* 2. Versão Mobile (< 768px): Drawer deslizante */}
      <Sheet
        open={isMobileOpen}
        onOpenChange={(open) => {
          if (!open) closeMobile()
        }}
      >
        <SheetContent
          side="left"
          aria-describedby={undefined}
          className="w-72 max-w-[80vw] gap-0 md:hidden"
        >
          <SheetTitle className="sr-only">
            Menu de navegação principal
          </SheetTitle>
          <SheetDescription className="sr-only">
            Links de navegação do Inventarium
          </SheetDescription>

          {/* Topo do Drawer (o botão de fechar é do próprio Sheet) */}
          <div className="flex h-16 w-full items-center px-4 pr-12 select-none">
            {brand(34, closeMobile)}
          </div>

          {/* Lista de navegação mobile */}
          <div className="flex-1 overflow-x-hidden overflow-y-auto py-2">
            <SidebarNav />
          </div>

          {/* Rodapé Mobile: Perfil e Logout */}
          <div className="mt-auto flex items-center gap-3 border-t bg-muted/50 p-3">
            <UserAvatar initials={initials} className="size-9" />
            {userIdentity}
            <UiButton
              variant="ghost"
              size="icon"
              onClick={() => setIsLogoutModalOpen(true)}
              aria-label="Sair da conta"
              title="Sair da conta"
              className="ml-auto shrink-0 text-muted-foreground hover:bg-destructive/10 hover:text-destructive"
            >
              <LogOut />
            </UiButton>
          </div>
        </SheetContent>
      </Sheet>

      {/* Confirmação de saída */}
      <ConfirmationModal
        isOpen={isLogoutModalOpen}
        onClose={() => setIsLogoutModalOpen(false)}
        onConfirm={handleConfirmLogout}
        title="Tem certeza que deseja sair?"
        message="Sua sessão será encerrada e você precisará entrar novamente para acessar o sistema."
        confirmButtonText="Sair"
      />
    </>
  )
}
