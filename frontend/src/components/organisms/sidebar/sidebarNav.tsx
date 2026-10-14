'use client'

import { Button as UiButton, buttonVariants } from '@/components/ui/button'
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from '@/components/ui/collapsible'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'

import { useState, useEffect, type ReactNode } from 'react'
import Link from 'next/link'
import { usePathname, useRouter } from 'next/navigation'
import {
  LayoutDashboard,
  Boxes,
  ChevronRight,
  Folder,
  Building2,
  Users,
  ShieldCheck,
  type LucideIcon,
} from 'lucide-react'
import { useSidebar } from '@/contexts/SidebarContext'
import { useInventory } from '@/contexts/InventoryContext'
import { useAdministrationAccess } from '@/contexts/AdministrationAccess'

// Classes de um item do menu, baseadas na variante ghost do Button do shadcn/ui.
const navItemClass = (active: boolean, collapsed: boolean) =>
  cn(
    buttonVariants({ variant: 'ghost' }),
    'h-auto w-full gap-3 px-3 py-2.5 text-sm font-medium [&_svg:not([class*=size-])]:size-5',
    collapsed ? 'justify-center' : 'justify-start',
    active
      ? 'bg-primary/10 text-primary font-semibold hover:bg-primary/10'
      : 'text-muted-foreground hover:text-foreground',
  )

const subItemClass = (active: boolean) =>
  cn(
    buttonVariants({ variant: 'ghost', size: 'sm' }),
    'h-auto w-full justify-start gap-2 px-2.5 py-1.5 text-xs font-normal',
    active
      ? 'bg-primary/10 text-primary font-semibold hover:bg-primary/10'
      : 'text-muted-foreground hover:text-foreground',
  )

// Exibe a tooltip lateral apenas quando o menu está recolhido.
const CollapsedTooltip = ({
  label,
  enabled,
  children,
}: {
  label: string
  enabled: boolean
  children: ReactNode
}) =>
  enabled ? (
    <Tooltip>
      <TooltipTrigger asChild>{children}</TooltipTrigger>
      <TooltipContent side="right" className="hidden md:block">
        {label}
      </TooltipContent>
    </Tooltip>
  ) : (
    <>{children}</>
  )

export function SidebarNav() {
  const pathname = usePathname()
  const router = useRouter()
  const { isCollapsed, closeMobile } = useSidebar()
  const { inventories, refreshInventories } = useInventory()
  const access = useAdministrationAccess()
  const [isAccordionOpen, setIsAccordionOpen] = useState(false)

  // Recarrega inventários ao mudar de rota para manter tudo sincronizado
  useEffect(() => {
    refreshInventories()
  }, [pathname, refreshInventories])

  const menuInventories = inventories.slice(0, 5)
  const hasInventories = inventories.length > 0
  const isDashboardActive = pathname === '/dashboard'
  const isInventoryActive =
    pathname === '/inventory' || pathname.startsWith('/inventory/')

  const navLink = (
    href: string,
    label: string,
    Icon: LucideIcon,
    active: boolean,
  ) => (
    <CollapsedTooltip key={href} label={label} enabled={isCollapsed}>
      <Link
        href={href}
        onClick={closeMobile}
        aria-label={label}
        aria-current={active ? 'page' : undefined}
        className={navItemClass(active, isCollapsed)}
      >
        <Icon className="shrink-0" />
        {!isCollapsed && <span className="truncate">{label}</span>}
      </Link>
    </CollapsedTooltip>
  )

  return (
    <nav
      className="flex w-full flex-col gap-1.5 px-3 py-1"
      aria-label="Menu principal"
    >
      {/* 1. Item Dashboard: exclusivo de administradores */}
      {!access.loading &&
        access.isAdmin &&
        navLink('/dashboard', 'Dashboard', LayoutDashboard, isDashboardActive)}

      {/* 2. Item Inventários */}
      {!hasInventories ? (
        navLink('/inventory', 'Inventários', Boxes, isInventoryActive)
      ) : !isCollapsed ? (
        /* Modo Expandido: link + acordeão com os inventários cadastrados */
        <Collapsible open={isAccordionOpen} onOpenChange={setIsAccordionOpen}>
          <div
            className={cn(
              'flex items-center rounded-md',
              isInventoryActive && 'bg-primary/10 text-primary',
            )}
          >
            <Link
              href="/inventory"
              onClick={closeMobile}
              aria-label="Abrir lista de inventários"
              aria-current={pathname === '/inventory' ? 'page' : undefined}
              className={cn(
                navItemClass(false, false),
                'min-w-0 flex-1',
                isInventoryActive && 'text-primary hover:text-primary',
              )}
            >
              <Boxes className="shrink-0" />
              <span className="truncate">Inventários</span>
            </Link>
            <CollapsibleTrigger asChild>
              <UiButton
                variant="ghost"
                size="icon"
                aria-label={
                  isAccordionOpen
                    ? 'Recolher inventários do menu'
                    : 'Expandir inventários do menu'
                }
                className={cn(isInventoryActive && 'text-primary')}
              >
                <ChevronRight
                  className={cn(
                    'transition-transform',
                    isAccordionOpen && 'rotate-90',
                  )}
                />
              </UiButton>
            </CollapsibleTrigger>
          </div>

          <CollapsibleContent className="mt-1 ml-5 flex flex-col gap-1 border-l-2 py-0.5 pr-1 pl-6">
            <Link
              href="/inventory"
              onClick={closeMobile}
              className={subItemClass(pathname === '/inventory')}
              title="Todos os inventários"
            >
              <span className="size-1.5 shrink-0 rounded-full bg-primary" />
              <span className="truncate">Todos os inventários</span>
            </Link>

            {menuInventories.map((inv) => {
              const isInvActive =
                pathname === `/inventory/${inv.id}` ||
                pathname.startsWith(`/inventory/${inv.id}/`)
              return (
                <Link
                  key={inv.id}
                  href={`/inventory/${inv.id}`}
                  onClick={closeMobile}
                  className={subItemClass(isInvActive)}
                  title={inv.name}
                >
                  <Folder className="size-3.5 shrink-0" />
                  <span className="truncate">{inv.name}</span>
                </Link>
              )
            })}
          </CollapsibleContent>
        </Collapsible>
      ) : (
        /* Modo Colapsado: ícone que abre um menu com os inventários */
        <DropdownMenu>
          <CollapsedTooltip label="Inventários" enabled>
            <DropdownMenuTrigger asChild>
              <UiButton
                variant="ghost"
                aria-label="Inventários"
                className={navItemClass(isInventoryActive, true)}
              >
                <Boxes className="shrink-0" />
              </UiButton>
            </DropdownMenuTrigger>
          </CollapsedTooltip>
          <DropdownMenuContent side="right" align="start" className="min-w-50">
            <DropdownMenuLabel className="text-xs">
              Inventários ({inventories.length})
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem
              className="text-xs"
              onSelect={() => {
                closeMobile()
                router.push('/inventory')
              }}
            >
              <span className="size-1.5 shrink-0 rounded-full bg-primary" />
              <span className="truncate">Todos os inventários</span>
            </DropdownMenuItem>
            {menuInventories.map((inv) => (
              <DropdownMenuItem
                key={inv.id}
                className="text-xs"
                title={inv.name}
                onSelect={() => {
                  closeMobile()
                  router.push(`/inventory/${inv.id}`)
                }}
              >
                <Folder className="size-3.5 shrink-0" />
                <span className="truncate">{inv.name}</span>
              </DropdownMenuItem>
            ))}
          </DropdownMenuContent>
        </DropdownMenu>
      )}

      {!access.loading &&
        !access.error &&
        [
          {
            href: '/sectors',
            label: 'Setores',
            icon: Building2,
            allowed: access.canReadSectors,
          },
          {
            href: '/users',
            label: 'Usuários',
            icon: Users,
            allowed: access.canManageUsers,
          },
          {
            href: '/profiles',
            label: 'Perfis e permissões',
            icon: ShieldCheck,
            allowed: access.isAdmin,
          },
        ]
          .filter((item) => item.allowed)
          .map((item) =>
            navLink(item.href, item.label, item.icon, pathname === item.href),
          )}
    </nav>
  )
}
