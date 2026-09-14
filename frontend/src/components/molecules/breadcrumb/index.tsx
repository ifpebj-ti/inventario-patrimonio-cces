'use client'

import Link from 'next/link'
import { usePathname, useRouter } from 'next/navigation'
import { Home, ChevronRight, ArrowLeft } from 'lucide-react'
import { useInventory } from '@/contexts/InventoryContext'

export interface BreadcrumbItem {
  label: string
  href: string
  isCurrent: boolean
  isHome?: boolean
}

export interface BreadcrumbProps {
  showBackButton?: boolean
  className?: string
  onBack?: () => void
}

const ROUTE_LABELS: Record<string, string> = {
  dashboard: 'Início',
  inicio: 'Início',
  inventory: 'Inventários',
  inventarios: 'Inventários',
  item: 'Item',
  itens: 'Itens',
  novo: 'Novo',
  new: 'Novo',
  edit: 'Editar',
  editar: 'Editar',
}

function formatSegmentLabel(segment: string): string {
  const normalized = segment.toLowerCase()
  if (ROUTE_LABELS[normalized]) {
    return ROUTE_LABELS[normalized]
  }

  // Se for numérico
  if (/^\d+$/.test(segment)) {
    return `#${segment}`
  }

  // Decodifica URI e formata capitalizado
  try {
    const decoded = decodeURIComponent(segment).replace(/[-_]/g, ' ')
    return decoded.charAt(0).toUpperCase() + decoded.slice(1)
  } catch {
    return segment
  }
}

export function Breadcrumb({
  showBackButton = true,
  className = '',
  onBack,
}: BreadcrumbProps) {
  const pathname = usePathname()
  const router = useRouter()
  const { inventories } = useInventory()

  const cleanPath = (pathname || '').replace(/\/+$/, '')
  const isDashboard = cleanPath === '' || cleanPath === '/dashboard'

  const handleBack = () => {
    if (onBack) {
      onBack()
    } else {
      router.back()
    }
  }

  // Monta a trilha dinâmica de navegação (breadcrumbs)
  const items: BreadcrumbItem[] = []

  if (isDashboard) {
    items.push({
      label: 'Início',
      href: '/dashboard',
      isCurrent: true,
      isHome: true,
    })
  } else {
    // Adiciona o nó raiz "Início" apontando para /dashboard
    items.push({
      label: 'Início',
      href: '/dashboard',
      isCurrent: false,
      isHome: true,
    })

    const rawSegments = cleanPath.split('/').filter(Boolean)

    if (rawSegments[0] === 'inventory' || rawSegments[0] === 'inventarios') {
      const isOnlyInventory = rawSegments.length === 1
      items.push({
        label: 'Inventários',
        href: '/dashboard',
        isCurrent: isOnlyInventory,
      })

      if (rawSegments.length >= 2) {
        const inventoryId = rawSegments[1]
        const isInventoryPage = rawSegments.length === 2

        const matchingInventory = inventories.find(
          (inv) => String(inv.id) === inventoryId,
        )
        const inventoryLabel = matchingInventory?.name?.trim()
          ? matchingInventory.name
          : `Inventário #${inventoryId}`

        items.push({
          label: inventoryLabel,
          href: `/inventory/${inventoryId}`,
          isCurrent: isInventoryPage,
        })

        // Se estiver navegando em detalhes do item: /inventory/[id]/item/[itemId]
        if (rawSegments.length >= 4 && rawSegments[2] === 'item') {
          const itemId = rawSegments[3]
          items.push({
            label: `Item #${itemId}`,
            href: `/inventory/${inventoryId}/item/${itemId}`,
            isCurrent: true,
          })
        } else if (rawSegments.length === 3 && rawSegments[2] === 'item') {
          items.push({
            label: 'Itens',
            href: `/inventory/${inventoryId}`,
            isCurrent: true,
          })
        } else if (rawSegments.length > 2) {
          // Outras subrotas arbitrárias em inventory/[id]/*
          for (let i = 2; i < rawSegments.length; i++) {
            const seg = rawSegments[i]
            const isLast = i === rawSegments.length - 1
            const subPath = `/${rawSegments.slice(0, i + 1).join('/')}`
            items.push({
              label: formatSegmentLabel(seg),
              href: subPath,
              isCurrent: isLast,
            })
          }
        }
      }
    } else {
      // Rotas genéricas que possam existir no fluxo autenticado
      let accumulatedPath = ''
      rawSegments.forEach((seg, idx) => {
        if (seg === 'dashboard') return
        accumulatedPath += `/${seg}`
        const isLast = idx === rawSegments.length - 1
        items.push({
          label: formatSegmentLabel(seg),
          href: accumulatedPath,
          isCurrent: isLast,
        })
      })
    }
  }

  return (
    <div
      className={`flex items-center gap-2 sm:gap-3 min-w-0 flex-1 ${className}`}
    >
      {/* Botão de voltar: exibido quando não estiver na raiz do sistema */}
      {showBackButton && !isDashboard && (
        <button
          onClick={handleBack}
          type="button"
          aria-label="Voltar para a página anterior"
          title="Voltar para a página anterior"
          accessKey="b"
          className="p-2 text-gray-500 hover:text-blue-400 hover:bg-blue-50 rounded-xl transition-all duration-200 cursor-pointer outline-none focus-visible:ring-2 focus-visible:ring-blue-400 shrink-0"
        >
          <ArrowLeft className="w-5 h-5 shrink-0" aria-hidden="true" />
        </button>
      )}

      {/* Trilha de navegação semântica */}
      <nav aria-label="breadcrumb" className="min-w-0 flex-1">
        <ol className="flex items-center flex-wrap gap-1.5 sm:gap-2 text-xs sm:text-sm text-gray-600 font-medium">
          {items.map((item, index) => {
            return (
              <li
                key={`${item.href}-${item.label}-${index}`}
                className="inline-flex items-center gap-1.5 sm:gap-2 min-w-0"
              >
                {index > 0 && (
                  <ChevronRight
                    className="w-4 h-4 text-gray-400 shrink-0"
                    aria-hidden="true"
                  />
                )}

                {item.isCurrent ? (
                  <span
                    aria-current="page"
                    className="font-semibold text-gray-900 truncate max-w-[180px] sm:max-w-xs md:max-w-md inline-flex items-center gap-1.5"
                    title={item.label}
                  >
                    {item.isHome && (
                      <Home
                        className="w-4 h-4 text-blue-400 shrink-0"
                        aria-hidden="true"
                      />
                    )}
                    <span className="truncate">{item.label}</span>
                  </span>
                ) : (
                  <Link
                    href={item.href}
                    className="group text-gray-600 hover:text-blue-400 transition-colors duration-150 inline-flex items-center gap-1.5 truncate max-w-[140px] sm:max-w-xs hover:underline decoration-blue-200 underline-offset-4 outline-none focus-visible:ring-2 focus-visible:ring-blue-400 rounded-md"
                    title={item.label}
                  >
                    {item.isHome && (
                      <Home
                        className="w-4 h-4 text-gray-400 group-hover:text-blue-400 transition-colors shrink-0"
                        aria-hidden="true"
                      />
                    )}
                    <span className="truncate">{item.label}</span>
                  </Link>
                )}
              </li>
            )
          })}
        </ol>
      </nav>
    </div>
  )
}

export default Breadcrumb
