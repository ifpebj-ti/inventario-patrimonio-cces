'use client'

import { useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import Image from 'next/image'
import { Menu, X, LogOut, ChevronsUpDown } from 'lucide-react'
import { AnimatePresence, motion } from 'framer-motion'
import { useSidebar } from '@/contexts/SidebarContext'
import { useAuth } from '@/hooks/useAuth'
import logoImg from '../../../../public/logo.png'
import { SidebarNav } from './sidebarNav'

export function Sidebar() {
  const { isCollapsed, toggleCollapse, isMobileOpen, closeMobile } =
    useSidebar()
  const { user, signOut } = useAuth()
  const drawerRef = useRef<HTMLDivElement>(null)

  // Estado para controlar a abertura do popover de perfil
  const [isProfileMenuOpen, setIsProfileMenuOpen] = useState(false)
  const profileMenuRef = useRef<HTMLDivElement>(null)

  // Estado para controlar o modal/popup de confirmação de logout
  const [isLogoutModalOpen, setIsLogoutModalOpen] = useState(false)

  // Fecha o menu de perfil ao clicar fora
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (
        profileMenuRef.current &&
        !profileMenuRef.current.contains(e.target as Node)
      ) {
        setIsProfileMenuOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

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

  const initials = getInitials(user?.name, user?.email)
  const displayName =
    user?.name?.trim() || user?.email?.split('@')[0] || 'Usuário'
  const displayEmail = user?.email?.trim() || ''

  const handleOpenLogoutModal = () => {
    setIsProfileMenuOpen(false)
    setIsLogoutModalOpen(true)
  }

  const handleConfirmLogout = () => {
    setIsLogoutModalOpen(false)
    setIsProfileMenuOpen(false)
    closeMobile()
    signOut()
  }

  // Listener para tecla Escape fechar modais ou drawer
  useEffect(() => {
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        if (isLogoutModalOpen) {
          setIsLogoutModalOpen(false)
        } else if (isMobileOpen) {
          closeMobile()
        }
      }
    }
    window.addEventListener('keydown', handleEscape)
    return () => window.removeEventListener('keydown', handleEscape)
  }, [isLogoutModalOpen, isMobileOpen, closeMobile])

  // Focus trap acessível no Mobile Drawer
  useEffect(() => {
    if (!isMobileOpen) return

    document.body.style.overflow = 'hidden'

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Tab' && drawerRef.current) {
        const focusableElements =
          drawerRef.current.querySelectorAll<HTMLElement>(
            'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])',
          )
        if (focusableElements.length === 0) return

        const firstElement = focusableElements[0]
        const lastElement = focusableElements[focusableElements.length - 1]

        if (e.shiftKey && document.activeElement === firstElement) {
          e.preventDefault()
          lastElement.focus()
        } else if (!e.shiftKey && document.activeElement === lastElement) {
          e.preventDefault()
          firstElement.focus()
        }
      }
    }

    window.addEventListener('keydown', handleKeyDown)

    const timer = setTimeout(() => {
      if (drawerRef.current) {
        const firstFocusable =
          drawerRef.current.querySelector<HTMLElement>('button, [href]')
        firstFocusable?.focus()
      }
    }, 50)

    return () => {
      document.body.style.overflow = ''
      window.removeEventListener('keydown', handleKeyDown)
      clearTimeout(timer)
    }
  }, [isMobileOpen, closeMobile])

  return (
    <>
      {/* 1. Versão Desktop (>= 768px): Fixa na lateral esquerda */}
      <aside
        aria-label="Menu lateral de navegação"
        className={`sticky top-0 h-screen hidden md:flex flex-col shrink-0 border-r border-gray-200 bg-white z-40 transition-all duration-300 ease-in-out font-['Linden_Hill',serif] ${
          isCollapsed ? 'w-24' : 'w-64'
        }`}
      >
        {/* Topo da Sidebar: SEM divisórias rígidas */}
        {!isCollapsed ? (
          /* Modo Expandido: Logo + Nome "Inventarium" à esquerda, Hambúrguer à direita */
          <div className="h-16 flex flex-row items-center justify-between w-full px-4 select-none">
            <Link
              href="/dashboard"
              className="flex items-center gap-2.5 min-w-0"
              title="Inventarium - Ir para o Dashboard"
            >
              <Image
                src={logoImg}
                alt="Inventarium"
                width={36}
                height={36}
                className="shrink-0 object-contain rounded-xl"
                priority
              />
              <h1 className="font-['Linden_Hill',serif] text-2xl text-blue-400 tracking-wide truncate cursor-pointer">
                Inventarium
              </h1>
            </Link>

            <button
              onClick={toggleCollapse}
              aria-label="Recolher menu lateral"
              className="p-2 ml-auto rounded-xl text-gray-500 hover:bg-gray-100 hover:text-gray-700 transition-colors cursor-pointer outline-none focus-visible:ring-2 focus-visible:ring-blue-400 shrink-0"
            >
              <Menu className="w-5 h-5" />
            </button>
          </div>
        ) : (
          /* Modo Colapsado: Apenas a logo oficial e o menu hambúrguer visíveis e bem alinhados */
          <div className="h-16 flex flex-row items-center justify-between px-3 w-full select-none">
            <Link
              href="/dashboard"
              className="flex items-center justify-center select-none"
              title="Ir para o Dashboard"
            >
              <Image
                src={logoImg}
                alt="Inventarium"
                width={32}
                height={32}
                className="shrink-0 object-contain rounded-xl"
                priority
              />
            </Link>

            <div className="relative group">
              <button
                onClick={toggleCollapse}
                aria-label="Expandir menu lateral"
                className="p-1.5 rounded-lg text-gray-500 hover:bg-gray-100 hover:text-gray-700 transition-colors cursor-pointer outline-none focus-visible:ring-2 focus-visible:ring-blue-400 shrink-0"
              >
                <Menu className="w-5 h-5" />
              </button>

              <div
                role="tooltip"
                className="hidden md:flex items-center absolute left-full top-1/2 -translate-y-1/2 ml-3 px-3 py-1.5 bg-gray-900/95 backdrop-blur-xs text-white text-xs font-medium rounded-lg whitespace-nowrap opacity-0 group-hover:opacity-100 transition-all duration-200 pointer-events-none shadow-xl border border-gray-800 z-[999] scale-95 group-hover:scale-100"
              >
                <span className="absolute right-full top-1/2 -translate-y-1/2 border-4 border-transparent border-r-gray-900/95" />
                Expandir menu
              </div>
            </div>
          </div>
        )}

        {/* Corpo dos itens de navegação */}
        <div
          className={`flex-1 py-2 ${
            isCollapsed
              ? 'overflow-visible'
              : 'overflow-y-auto overflow-x-hidden'
          }`}
        >
          <SidebarNav />
        </div>

        {/* Rodapé da Sidebar: Card de Perfil com Seta Persistente e Popover Completo */}
        <div
          ref={profileMenuRef}
          className="mt-auto shrink-0 border-t border-gray-100 p-2.5 relative select-none"
        >
          {/* Popover Flutuante para Cima com Dados do Usuário */}
          <AnimatePresence>
            {isProfileMenuOpen && (
              <motion.div
                initial={{ opacity: 0, y: 8, scale: 0.96 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                exit={{ opacity: 0, y: 8, scale: 0.96 }}
                transition={{ duration: 0.15 }}
                className={`absolute bottom-full mb-2 bg-white rounded-2xl shadow-xl border border-gray-100 p-3 z-[999] font-['Linden_Hill',serif] ${
                  isCollapsed ? 'left-1 w-60' : 'left-2 right-2'
                }`}
              >
                {/* Dados completos do usuário: Avatar, Nome Completo e E-mail */}
                <div className="flex items-center gap-3 pb-3 mb-2 border-b border-gray-100">
                  <div className="w-10 h-10 rounded-xl bg-blue-50 text-blue-400 font-semibold flex items-center justify-center text-sm shrink-0 shadow-xs border border-blue-200 select-none">
                    {initials}
                  </div>
                  <div className="flex flex-col min-w-0 flex-1">
                    <p
                      className="text-xs font-bold text-gray-900 truncate"
                      title={displayName}
                    >
                      {displayName}
                    </p>
                    {displayEmail && (
                      <p
                        className="text-[11px] text-gray-500 truncate"
                        title={displayEmail}
                      >
                        {displayEmail}
                      </p>
                    )}
                  </div>
                </div>

                {/* Botão de Logout -> Aciona o modal de confirmação */}
                <button
                  onClick={handleOpenLogoutModal}
                  className="flex items-center gap-2.5 w-full px-3 py-2 text-xs font-medium text-red-600 hover:bg-red-50 rounded-xl transition-colors cursor-pointer outline-none focus-visible:ring-2 focus-visible:ring-red-400"
                >
                  <LogOut className="w-4 h-4" />
                  <span>Sair</span>
                </button>
              </motion.div>
            )}
          </AnimatePresence>

          {/* Botão de Perfil na Barra */}
          {!isCollapsed ? (
            /* Modo Expandido: Avatar + Nome/Email + Seta */
            <button
              onClick={() => setIsProfileMenuOpen((prev) => !prev)}
              aria-expanded={isProfileMenuOpen}
              className="w-full flex items-center gap-2.5 p-2 rounded-xl hover:bg-gray-100 transition-colors cursor-pointer text-left outline-none focus-visible:ring-2 focus-visible:ring-blue-400"
            >
              <div className="w-9 h-9 rounded-xl bg-blue-50 text-blue-400 font-semibold flex items-center justify-center text-xs shrink-0 select-none shadow-xs border border-blue-200">
                {initials}
              </div>
              <div className="flex flex-col min-w-0 max-w-[130px] flex-1">
                <span
                  className="text-xs font-semibold text-gray-800 truncate"
                  title={displayName}
                >
                  {displayName}
                </span>
                {displayEmail && (
                  <span
                    className="text-[11px] text-gray-400 truncate"
                    title={displayEmail}
                  >
                    {displayEmail}
                  </span>
                )}
              </div>
              <ChevronsUpDown className="w-4 h-4 text-gray-400 shrink-0 ml-auto" />
            </button>
          ) : (
            /* Modo Colapsado: Avatar com Seta persistente ao lado */
            <div className="flex justify-center relative group">
              <button
                onClick={() => setIsProfileMenuOpen((prev) => !prev)}
                aria-expanded={isProfileMenuOpen}
                aria-label="Menu do perfil"
                className="flex items-center justify-center gap-1.5 p-1.5 rounded-xl hover:bg-gray-100 transition-colors cursor-pointer outline-none focus-visible:ring-2 focus-visible:ring-blue-400"
              >
                <div className="w-8 h-8 rounded-xl bg-blue-50 text-blue-400 font-semibold flex items-center justify-center text-xs shrink-0 select-none shadow-xs border border-blue-200">
                  {initials}
                </div>
                <ChevronsUpDown className="w-3.5 h-3.5 text-gray-400 shrink-0" />
              </button>

              {/* Tooltip do Perfil ao passar o mouse */}
              {!isProfileMenuOpen && (
                <div
                  role="tooltip"
                  className="hidden md:flex items-center absolute left-full top-1/2 -translate-y-1/2 ml-3 px-3 py-1.5 bg-gray-900/95 backdrop-blur-xs text-white text-xs font-medium rounded-lg whitespace-nowrap opacity-0 group-hover:opacity-100 transition-all duration-200 pointer-events-none shadow-xl border border-gray-800 z-[999] scale-95 group-hover:scale-100"
                >
                  <span className="absolute right-full top-1/2 -translate-y-1/2 border-4 border-transparent border-r-gray-900/95" />
                  Perfil ({displayName})
                </div>
              )}
            </div>
          )}
        </div>
      </aside>

      {/* 2. Versão Mobile (< 768px): Drawer deslizante */}
      <AnimatePresence>
        {isMobileOpen && (
          <div className="md:hidden fixed inset-0 z-[60] flex">
            {/* Backdrop com animação */}
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.2 }}
              onClick={closeMobile}
              aria-hidden="true"
              className="fixed inset-0 bg-black/50 backdrop-blur-xs"
            />

            {/* Conteúdo do Drawer */}
            <motion.div
              ref={drawerRef}
              role="dialog"
              aria-modal="true"
              aria-label="Menu de navegação principal"
              initial={{ x: '-100%' }}
              animate={{ x: 0 }}
              exit={{ x: '-100%' }}
              transition={{ type: 'spring', damping: 25, stiffness: 250 }}
              className="relative flex flex-col w-72 max-w-[80vw] h-full bg-white shadow-2xl z-10 overflow-hidden font-['Linden_Hill',serif]"
            >
              {/* Topo do Drawer */}
              <div className="h-16 px-4 flex flex-row items-center justify-between w-full flex-nowrap select-none">
                <Link
                  href="/dashboard"
                  onClick={closeMobile}
                  className="flex flex-row items-center gap-2.5 whitespace-nowrap overflow-hidden flex-nowrap min-w-0"
                  title="Ir para o Dashboard"
                >
                  <Image
                    src={logoImg}
                    alt="Inventarium"
                    width={34}
                    height={34}
                    className="shrink-0 object-contain rounded-xl"
                    priority
                  />
                  <h1 className="font-['Linden_Hill',serif] text-2xl text-blue-400 tracking-wide truncate cursor-pointer">
                    Inventarium
                  </h1>
                </Link>
                <button
                  onClick={closeMobile}
                  aria-label="Fechar menu de navegação"
                  className="p-2 ml-auto rounded-xl text-gray-400 hover:bg-gray-100 hover:text-gray-600 cursor-pointer outline-none focus-visible:ring-2 focus-visible:ring-blue-400 shrink-0"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              {/* Lista de navegação mobile */}
              <div className="flex-1 overflow-y-auto overflow-x-hidden py-2">
                <SidebarNav />
              </div>

              {/* Rodapé Mobile: Perfil e Logout */}
              <div className="mt-auto border-t border-gray-100 p-3 flex items-center gap-3 bg-gray-50/50">
                <div
                  className="w-9 h-9 rounded-xl bg-blue-50 text-blue-400 font-semibold flex items-center justify-center text-xs shrink-0 shadow-xs border border-blue-200 select-none"
                  aria-hidden="true"
                >
                  {initials}
                </div>

                <div className="flex flex-col min-w-0 max-w-[140px]">
                  <span
                    className="text-xs font-semibold text-gray-800 truncate"
                    title={displayName}
                  >
                    {displayName}
                  </span>
                  {displayEmail && (
                    <span
                      className="text-[11px] text-gray-400 truncate"
                      title={displayEmail}
                    >
                      {displayEmail}
                    </span>
                  )}
                </div>

                <button
                  onClick={handleOpenLogoutModal}
                  aria-label="Sair da conta"
                  title="Sair da conta"
                  className="p-2 ml-auto rounded-xl text-gray-400 hover:text-red-600 hover:bg-red-50 transition-colors cursor-pointer outline-none focus-visible:ring-2 focus-visible:ring-red-400 shrink-0"
                >
                  <LogOut className="w-4 h-4" />
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>

      {/* Diálogo / Modal de Confirmação de Saída com a Identidade Visual do Sistema */}
      <AnimatePresence>
        {isLogoutModalOpen && (
          <div className="fixed inset-0 z-[100] flex items-center justify-center p-4">
            {/* Backdrop com blur leve e animação suave */}
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.15 }}
              onClick={() => setIsLogoutModalOpen(false)}
              aria-hidden="true"
              className="fixed inset-0 bg-black/25 backdrop-blur-xs"
            />

            {/* Painel do Modal com Design System do Inventarium */}
            <motion.div
              role="dialog"
              aria-modal="true"
              aria-labelledby="logout-dialog-title"
              initial={{ opacity: 0, scale: 0.95, y: 8 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: 8 }}
              transition={{ duration: 0.15, ease: 'easeOut' }}
              className="relative w-full max-w-sm bg-white rounded-3xl shadow-xl p-6 sm:p-7 border border-gray-100 z-10 select-none font-['Linden_Hill',serif]"
            >
              {/* Botão de fechar discreto no canto superior */}
              <button
                type="button"
                onClick={() => setIsLogoutModalOpen(false)}
                aria-label="Fechar diálogo de confirmação"
                className="absolute top-4 right-4 p-1.5 rounded-lg text-gray-400 hover:text-gray-600 hover:bg-gray-100 transition-colors cursor-pointer outline-none focus-visible:ring-2 focus-visible:ring-gray-300"
              >
                <X className="w-4 h-4" />
              </button>

              {/* Título com a tipografia do projeto */}
              <h2
                id="logout-dialog-title"
                className="font-['Linden_Hill',serif] text-2xl sm:text-3xl text-blue-400 tracking-wide mb-2 leading-tight pr-6"
              >
                Tem certeza que deseja sair?
              </h2>

              {/* Mensagem explicativa sutil */}
              <p className="text-sm text-gray-500 mb-6 leading-relaxed">
                Sua sessão será encerrada e você precisará entrar novamente para
                acessar o sistema.
              </p>

              {/* Ações do Pop-up: Cancelar (discreto) e Sair (vermelho padrão do sistema) */}
              <div className="flex items-center justify-end gap-3">
                <button
                  type="button"
                  onClick={() => setIsLogoutModalOpen(false)}
                  className="px-4 py-2.5 rounded-xl text-sm font-medium text-gray-500 hover:text-gray-800 hover:bg-gray-100 transition-colors cursor-pointer outline-none focus-visible:ring-2 focus-visible:ring-gray-300"
                >
                  Cancelar
                </button>

                <button
                  type="button"
                  onClick={handleConfirmLogout}
                  className="px-6 py-2.5 rounded-xl text-sm font-semibold text-white bg-red-500 hover:bg-red-600 shadow-xs transition-colors cursor-pointer outline-none focus-visible:ring-2 focus-visible:ring-red-400 focus-visible:ring-offset-2"
                >
                  Sair
                </button>
              </div>
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </>
  )
}
