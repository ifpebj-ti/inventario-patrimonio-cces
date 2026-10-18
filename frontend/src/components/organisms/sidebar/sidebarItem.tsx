'use client'

import { buttonVariants } from '@/components/ui/button'
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'
import Link from 'next/link'
import { LucideIcon } from 'lucide-react'
import { useSidebar } from '@/contexts/SidebarContext'

interface SidebarItemProps {
  label: string
  href: string
  icon: LucideIcon
  isActive?: boolean
  disabled?: boolean
  disabledTooltip?: string
}

export function SidebarItem({
  label,
  href,
  icon: Icon,
  isActive = false,
  disabled = false,
  disabledTooltip,
}: SidebarItemProps) {
  const { isCollapsed, closeMobile } = useSidebar()

  const className = cn(
    buttonVariants({ variant: 'ghost' }),
    'h-auto w-full gap-3 px-3 py-3 text-sm font-medium [&_svg:not([class*=size-])]:size-5',
    isCollapsed ? 'justify-center' : 'justify-start',
    disabled
      ? 'pointer-events-none opacity-50'
      : isActive
        ? 'bg-primary/10 text-primary font-semibold hover:bg-primary/10'
        : 'text-muted-foreground hover:text-foreground',
  )

  const content = (
    <>
      <Icon className="shrink-0" />
      {!isCollapsed && <span className="truncate">{label}</span>}
    </>
  )

  const tooltipText = disabled ? disabledTooltip || label : label

  const item = disabled ? (
    <div aria-disabled="true" aria-label={tooltipText} className={className}>
      {content}
    </div>
  ) : (
    <Link
      href={href}
      onClick={closeMobile}
      aria-label={label}
      aria-current={isActive ? 'page' : undefined}
      className={className}
    >
      {content}
    </Link>
  )

  if (!isCollapsed && !disabled) return item

  // Tooltip exibida no hover quando o menu está recolhido ou o item desabilitado
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        {disabled ? <span className="block w-full">{item}</span> : item}
      </TooltipTrigger>
      <TooltipContent side="right">{tooltipText}</TooltipContent>
    </Tooltip>
  )
}
