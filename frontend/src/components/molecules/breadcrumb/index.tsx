'use client'

import { Button as UiButton } from '@/components/ui/button'
import {
  Breadcrumb as UiBreadcrumb,
  BreadcrumbItem as UiBreadcrumbItem,
  BreadcrumbLink as UiBreadcrumbLink,
  BreadcrumbList as UiBreadcrumbList,
  BreadcrumbPage as UiBreadcrumbPage,
  BreadcrumbSeparator as UiBreadcrumbSeparator,
} from '@/components/ui/breadcrumb'
import { Fragment } from 'react'

import Link from 'next/link'
import { usePathname, useRouter } from 'next/navigation'
import { Home, ArrowLeft } from 'lucide-react'
import { useInventory } from '@/contexts/InventoryContext'
import { useAuth } from '@/hooks/useAuth'

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
  sectors: 'Setores',
  users: 'Usuários',
  profiles: 'Perfis e permissões',
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
  const { user } = useAuth()
  const hasDashboard = !!user?.permissions?.includes('ADMIN')

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
    // Adiciona o nó raiz "Início" apontando para o dashboard, que só administradores têm.
    if (hasDashboard) {
      items.push({
        label: 'Início',
        href: '/dashboard',
        isCurrent: false,
        isHome: true,
      })
    }

    const rawSegments = cleanPath.split('/').filter(Boolean)

    if (rawSegments[0] === 'inventory' || rawSegments[0] === 'inventarios') {
      const isOnlyInventory = rawSegments.length === 1
      items.push({
        label: 'Inventários',
        href: '/inventory',
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
        <UiButton
          variant="ghost"
          size="icon"
          onClick={handleBack}
          type="button"
          aria-label="Voltar para a página anterior"
          title="Voltar para a página anterior"
          accessKey="b"
          className="shrink-0 text-muted-foreground"
        >
          <ArrowLeft className="size-5" aria-hidden="true" />
        </UiButton>
      )}

      {/* Trilha de navegação semântica */}
      <UiBreadcrumb className="min-w-0 flex-1">
        <UiBreadcrumbList className="text-xs sm:text-sm font-medium">
          {items.map((item, index) => (
            <Fragment key={`${item.href}-${item.label}-${index}`}>
              {index > 0 && <UiBreadcrumbSeparator />}
              <UiBreadcrumbItem className="min-w-0">
                {item.isCurrent ? (
                  <UiBreadcrumbPage
                    className="font-semibold inline-flex items-center gap-1.5 max-w-[180px] sm:max-w-xs md:max-w-md"
                    title={item.label}
                  >
                    {item.isHome && (
                      <Home
                        className="size-4 text-primary shrink-0"
                        aria-hidden="true"
                      />
                    )}
                    <span className="truncate">{item.label}</span>
                  </UiBreadcrumbPage>
                ) : (
                  <UiBreadcrumbLink asChild>
                    <Link
                      href={item.href}
                      className="inline-flex items-center gap-1.5 max-w-[140px] sm:max-w-xs"
                      title={item.label}
                    >
                      {item.isHome && (
                        <Home className="size-4 shrink-0" aria-hidden="true" />
                      )}
                      <span className="truncate">{item.label}</span>
                    </Link>
                  </UiBreadcrumbLink>
                )}
              </UiBreadcrumbItem>
            </Fragment>
          ))}
        </UiBreadcrumbList>
      </UiBreadcrumb>
    </div>
  )
}

export default Breadcrumb
