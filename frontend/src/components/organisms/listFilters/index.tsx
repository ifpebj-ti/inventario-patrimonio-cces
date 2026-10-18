'use client'

import { useState, type ReactNode } from 'react'
import { ChevronDown, ChevronUp, SlidersHorizontal, X } from 'lucide-react'
import { Card as UiCard } from '@/components/ui/card'
import { Badge as UiBadge } from '@/components/ui/badge'
import { Button as UiButton } from '@/components/ui/button'
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from '@/components/ui/collapsible'

// Filtros recolhíveis no mesmo padrão dos filtros de inventários:
// cabeçalho com título e botão de expandir, campos em grade e chips dos filtros ativos.
export type ActiveFilter = { label: string; clear: () => void }

type Props = {
  title: string
  ariaLabel: string
  active: ActiveFilter[]
  children: ReactNode
}

export function ListFilters({ title, ariaLabel, active, children }: Props) {
  const [collapsed, setCollapsed] = useState(true)
  const fieldsId = `${ariaLabel.replace(/\s+/g, '-').toLowerCase()}-fields`

  return (
    <UiCard role="region" aria-label={ariaLabel} className="gap-4 p-5">
      <Collapsible
        open={!collapsed}
        onOpenChange={(open) => setCollapsed(!open)}
        className="space-y-4"
      >
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
            <SlidersHorizontal className="size-4 text-primary" />
            {title}
          </div>
          <CollapsibleTrigger asChild>
            <UiButton
              variant="secondary"
              size="icon-sm"
              type="button"
              aria-label={collapsed ? 'Expandir filtros' : 'Recolher filtros'}
              aria-controls={fieldsId}
              className="text-primary"
            >
              {collapsed ? <ChevronDown /> : <ChevronUp />}
            </UiButton>
          </CollapsibleTrigger>
        </div>
        <CollapsibleContent id={fieldsId} className="space-y-4">
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
            {children}
          </div>
        </CollapsibleContent>
      </Collapsible>
      {active.length > 0 && (
        <div className="flex flex-wrap items-center gap-2 pt-1">
          {active.map((item) => (
            <UiBadge
              key={item.label}
              variant="outline"
              className="gap-1.5 rounded-md border-primary/25 bg-primary/10 text-primary"
            >
              {item.label}
              <UiButton
                variant="ghost"
                size="icon-xs"
                type="button"
                onClick={item.clear}
                aria-label={`Remover filtro ${item.label}`}
                className="size-4 rounded-full text-primary hover:bg-primary/10 hover:text-primary"
              >
                <X />
              </UiButton>
            </UiBadge>
          ))}
        </div>
      )}
    </UiCard>
  )
}
