'use client'

import { useState, useEffect, useRef } from 'react'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { LayoutDashboard, Boxes, ChevronRight, Folder } from 'lucide-react'
import { useSidebar } from '@/contexts/SidebarContext'
import { useInventory } from '@/contexts/InventoryContext'

export function SidebarNav() {
  const pathname = usePathname()
  const { isCollapsed, closeMobile } = useSidebar()
  const { inventories, refreshInventories } = useInventory()
  const [isAccordionOpen, setIsAccordionOpen] = useState(false)
  const [isFlyoutOpen, setIsFlyoutOpen] = useState(false)
  const flyoutRef = useRef<HTMLDivElement>(null)

  // Abre automaticamente o acordeão se estiver navegando dentro de um inventário específico
  useEffect(() => {
    if (pathname.startsWith('/inventory/')) {
      setIsAccordionOpen(true)
    }
  }, [pathname])

  // Recarrega inventários ao mudar de rota para manter tudo sincronizado
  useEffect(() => {
    refreshInventories()
  }, [pathname, refreshInventories])

  // Fecha o flyout colapsado ao clicar fora
  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (flyoutRef.current && !flyoutRef.current.contains(e.target as Node)) {
        setIsFlyoutOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  const hasInventories = inventories.length > 0
  const isDashboardActive = pathname === '/dashboard'
  const isInventoryActive = pathname.startsWith('/inventory/')

  return (
    <nav
      className="flex flex-col gap-1.5 px-3 py-1 w-full font-['Linden_Hill',serif]"
      aria-label="Menu principal"
    >
      {/* 1. Item Dashboard */}
      <div className="relative group w-full group-hover:z-50">
        <Link
          href="/dashboard"
          onClick={closeMobile}
          aria-label="Dashboard"
          aria-current={isDashboardActive ? 'page' : undefined}
          className={`flex items-center gap-3 px-3 py-2.5 rounded-xl transition-all duration-200 outline-none focus-visible:ring-2 focus-visible:ring-blue-400 ${
            isCollapsed ? 'justify-center' : 'justify-start'
          } ${
            isDashboardActive
              ? 'bg-blue-50 text-blue-400 font-semibold shadow-xs'
              : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900'
          }`}
        >
          <LayoutDashboard
            className={`w-5 h-5 shrink-0 ${
              isDashboardActive
                ? 'text-blue-400'
                : 'text-gray-500 group-hover:text-gray-700'
            }`}
          />
          {!isCollapsed && (
            <span className="truncate text-sm font-medium">Dashboard</span>
          )}
        </Link>

        {isCollapsed && (
          <div
            role="tooltip"
            className="hidden md:flex items-center absolute left-full top-1/2 -translate-y-1/2 ml-3 px-3 py-1.5 bg-gray-900/95 backdrop-blur-xs text-white text-xs font-medium rounded-lg whitespace-nowrap opacity-0 group-hover:opacity-100 transition-all duration-200 pointer-events-none shadow-xl border border-gray-800 z-[999] scale-95 group-hover:scale-100"
          >
            <span className="absolute right-full top-1/2 -translate-y-1/2 border-4 border-transparent border-r-gray-900/95" />
            Dashboard
          </div>
        )}
      </div>

      {/* 2. Item Inventários: Condicional */}
      {!hasInventories ? (
        /* Caso 1: Usuário NÃO possui inventários cadastrados -> Link direto para /dashboard (sem chevron) */
        <div className="relative group w-full group-hover:z-50">
          <Link
            href="/dashboard"
            onClick={closeMobile}
            aria-label="Inventários"
            className={`flex items-center gap-3 px-3 py-2.5 rounded-xl transition-all duration-200 outline-none focus-visible:ring-2 focus-visible:ring-blue-400 ${
              isCollapsed ? 'justify-center' : 'justify-start'
            } ${
              isInventoryActive
                ? 'bg-blue-50 text-blue-400 font-semibold shadow-xs'
                : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900'
            }`}
          >
            <Boxes
              className={`w-5 h-5 shrink-0 ${
                isInventoryActive
                  ? 'text-blue-400'
                  : 'text-gray-500 group-hover:text-gray-700'
              }`}
            />
            {!isCollapsed && (
              <span className="truncate text-sm font-medium">Inventários</span>
            )}
          </Link>

          {isCollapsed && (
            <div
              role="tooltip"
              className="hidden md:flex items-center absolute left-full top-1/2 -translate-y-1/2 ml-3 px-3 py-1.5 bg-gray-900/95 backdrop-blur-xs text-white text-xs font-medium rounded-lg whitespace-nowrap opacity-0 group-hover:opacity-100 transition-all duration-200 pointer-events-none shadow-xl border border-gray-800 z-[999] scale-95 group-hover:scale-100"
            >
              <span className="absolute right-full top-1/2 -translate-y-1/2 border-4 border-transparent border-r-gray-900/95" />
              Inventários
            </div>
          )}
        </div>
      ) : (
        /* Caso 2: Usuário POSSUI inventários cadastrados -> Acordeão expansível com chevron indicativo '>' */
        <div ref={flyoutRef} className="relative w-full">
          {!isCollapsed ? (
            /* Modo Expandido: Botão com Chevron indicativo e lista acordeão */
            <div className="flex flex-col w-full">
              <button
                type="button"
                onClick={() => setIsAccordionOpen((prev) => !prev)}
                aria-expanded={isAccordionOpen}
                aria-label={
                  isAccordionOpen
                    ? 'Recolher lista de inventários'
                    : 'Expandir lista de inventários'
                }
                className={`flex items-center justify-between w-full px-3 py-2.5 rounded-xl transition-all duration-200 cursor-pointer outline-none focus-visible:ring-2 focus-visible:ring-blue-400 select-none ${
                  isInventoryActive
                    ? 'bg-blue-50 text-blue-400 font-semibold'
                    : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900'
                }`}
              >
                <div className="flex items-center gap-3 min-w-0">
                  <Boxes
                    className={`w-5 h-5 shrink-0 ${
                      isInventoryActive ? 'text-blue-400' : 'text-gray-500'
                    }`}
                  />
                  <span className="truncate text-sm font-medium">
                    Inventários
                  </span>
                </div>
                <ChevronRight
                  className={`w-4 h-4 text-gray-400 transition-transform duration-200 shrink-0 ${
                    isAccordionOpen ? 'rotate-90 text-blue-400' : ''
                  }`}
                />
              </button>

              {/* Submenu Acordeão com os inventários cadastrados */}
              {isAccordionOpen && (
                <div className="flex flex-col gap-1 pl-6 pr-1 mt-1 border-l-2 border-gray-100 ml-5 py-0.5 animate-in fade-in duration-150">
                  {/* Opção para visão geral / listagem geral */}
                  <Link
                    href="/dashboard"
                    onClick={closeMobile}
                    className={`flex items-center gap-2 px-2.5 py-1.5 rounded-lg text-xs transition-colors truncate ${
                      pathname === '/dashboard'
                        ? 'bg-blue-50 text-blue-400 font-semibold'
                        : 'text-gray-500 hover:bg-gray-100 hover:text-gray-800'
                    }`}
                    title="Todos os inventários"
                  >
                    <span className="w-1.5 h-1.5 rounded-full bg-blue-400 shrink-0" />
                    <span className="truncate">Todos os inventários</span>
                  </Link>

                  {/* Lista com os inventários reais vindos da API */}
                  {inventories.map((inv) => {
                    const isInvActive =
                      pathname === `/inventory/${inv.id}` ||
                      pathname.startsWith(`/inventory/${inv.id}/`)
                    return (
                      <Link
                        key={inv.id}
                        href={`/inventory/${inv.id}`}
                        onClick={closeMobile}
                        className={`flex items-center gap-2 px-2.5 py-1.5 rounded-lg text-xs transition-colors truncate ${
                          isInvActive
                            ? 'bg-blue-50 text-blue-400 font-semibold'
                            : 'text-gray-500 hover:bg-gray-100 hover:text-gray-800'
                        }`}
                        title={inv.name}
                      >
                        <Folder
                          className={`w-3.5 h-3.5 shrink-0 ${
                            isInvActive ? 'text-blue-400' : 'opacity-70'
                          }`}
                        />
                        <span className="truncate">{inv.name}</span>
                      </Link>
                    )
                  })}
                </div>
              )}
            </div>
          ) : (
            /* Modo Colapsado: Ícone que abre flyout com os inventários cadastrados */
            <div className="relative group w-full group-hover:z-50">
              <button
                type="button"
                onClick={() => setIsFlyoutOpen((prev) => !prev)}
                aria-label="Inventários"
                className={`flex items-center justify-center p-2.5 w-full rounded-xl transition-all duration-200 cursor-pointer outline-none focus-visible:ring-2 focus-visible:ring-blue-400 ${
                  isInventoryActive
                    ? 'bg-blue-50 text-blue-400 font-semibold shadow-xs'
                    : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900'
                }`}
              >
                <Boxes
                  className={`w-5 h-5 shrink-0 ${
                    isInventoryActive ? 'text-blue-400' : 'text-gray-500'
                  }`}
                />
              </button>

              {/* Tooltip do item Inventários no hover quando recolhido */}
              {!isFlyoutOpen && (
                <div
                  role="tooltip"
                  className="hidden md:flex items-center absolute left-full top-1/2 -translate-y-1/2 ml-3 px-3 py-1.5 bg-gray-900/95 backdrop-blur-xs text-white text-xs font-medium rounded-lg whitespace-nowrap opacity-0 group-hover:opacity-100 transition-all duration-200 pointer-events-none shadow-xl border border-gray-800 z-[999] scale-95 group-hover:scale-100"
                >
                  <span className="absolute right-full top-1/2 -translate-y-1/2 border-4 border-transparent border-r-gray-900/95" />
                  Inventários
                </div>
              )}

              {/* Flyout flutuante ao clicar */}
              {isFlyoutOpen && (
                <div className="absolute left-full top-0 ml-2.5 bg-white rounded-2xl shadow-xl border border-gray-100 p-2 min-w-[200px] z-[999] font-['Linden_Hill',serif]">
                  <p className="text-xs font-bold text-gray-700 px-2.5 py-1.5 border-b border-gray-50 mb-1">
                    Inventários ({inventories.length})
                  </p>
                  <div className="flex flex-col gap-1 max-h-60 overflow-y-auto">
                    <Link
                      href="/dashboard"
                      onClick={() => {
                        setIsFlyoutOpen(false)
                        closeMobile()
                      }}
                      className="flex items-center gap-2 px-2.5 py-1.5 rounded-lg text-xs text-gray-600 hover:bg-gray-100 hover:text-gray-900 transition-colors truncate"
                      title="Todos os inventários"
                    >
                      <span className="w-1.5 h-1.5 rounded-full bg-blue-400 shrink-0" />
                      <span className="truncate">Todos os inventários</span>
                    </Link>

                    {inventories.map((inv) => (
                      <Link
                        key={inv.id}
                        href={`/inventory/${inv.id}`}
                        onClick={() => {
                          setIsFlyoutOpen(false)
                          closeMobile()
                        }}
                        className="flex items-center gap-2 px-2.5 py-1.5 rounded-lg text-xs text-gray-600 hover:bg-gray-100 hover:text-gray-900 transition-colors truncate"
                        title={inv.name}
                      >
                        <Folder className="w-3.5 h-3.5 shrink-0 text-gray-400" />
                        <span className="truncate">{inv.name}</span>
                      </Link>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </nav>
  )
}
