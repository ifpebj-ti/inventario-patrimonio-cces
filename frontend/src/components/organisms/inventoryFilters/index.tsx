'use client'

import { SelectField } from '@/components/ui/select-field'
import { cn } from '@/lib/utils'
import { chipToneClass } from '@/lib/tone'
import { Badge as UiBadge } from '@/components/ui/badge'
import { Card as UiCard } from '@/components/ui/card'
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from '@/components/ui/collapsible'

import { Button as UiButton } from '@/components/ui/button'
import { Label as UiLabel } from '@/components/ui/label'
import { Input as UiInput } from '@/components/ui/input'

import {
  Search,
  SlidersHorizontal,
  X,
  ChevronUp,
  ChevronDown,
} from 'lucide-react'
import { useState } from 'react'

export type StatusFilter = 'TODOS' | 'EM_ANDAMENTO' | 'CONCLUIDO' | 'VENCIDO'
export type SortField = 'PLANNED_END' | 'CREATED' | 'COMPLETED'
const statusLabels: Record<StatusFilter, string> = {
  TODOS: 'Todos os status',
  EM_ANDAMENTO: 'Em andamento',
  CONCLUIDO: 'Finalizados',
  VENCIDO: 'Atrasados',
}

type Props = {
  search: string
  onSearch: (value: string) => void
  status: StatusFilter
  onStatus: (value: StatusFilter) => void
  sector: string
  onSector: (value: string) => void
  sectors: { id: number; name: string }[]
  year: string
  onYear: (value: string) => void
  years: number[]
  onClear: () => void
}

export const InventoryFilters = (props: Props) => {
  const [collapsed, setCollapsed] = useState(true)
  const active = [
    ...(props.search.trim()
      ? [
          {
            label: `Nome: ${props.search.trim()}`,
            clear: () => props.onSearch(''),
          },
        ]
      : []),
    ...(props.status !== 'TODOS'
      ? [
          {
            label: statusLabels[props.status],
            clear: () => props.onStatus('TODOS'),
          },
        ]
      : []),
    ...(props.sector !== 'TODOS'
      ? [
          {
            label:
              props.sectors.find((s) => String(s.id) === props.sector)?.name ||
              'Setor',
            clear: () => props.onSector('TODOS'),
          },
        ]
      : []),
    ...(props.year !== 'TODOS'
      ? [{ label: 'Ano: ' + props.year, clear: () => props.onYear('TODOS') }]
      : []),
  ]
  return (
    <UiCard
      role="region"
      aria-label="Filtros dos inventários"
      className="inventory-filter-card gap-4 p-5"
    >
      <Collapsible
        open={!collapsed}
        onOpenChange={(open) => setCollapsed(!open)}
        className="space-y-4"
      >
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2 text-sm font-semibold text-foreground">
            <SlidersHorizontal className="size-4 text-primary" />
            Filtros dos inventários
          </div>
          <CollapsibleTrigger asChild>
            <UiButton
              variant="secondary"
              size="icon-sm"
              type="button"
              aria-label={collapsed ? 'Expandir filtros' : 'Recolher filtros'}
              aria-controls="inventory-filter-fields"
              className="text-primary"
            >
              {collapsed ? <ChevronDown /> : <ChevronUp />}
            </UiButton>
          </CollapsibleTrigger>
        </div>
        <CollapsibleContent id="inventory-filter-fields" className="space-y-4">
          <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-[1.4fr_1fr_1fr_0.7fr]">
            <UiLabel className="flex-col items-stretch gap-1.5 text-xs font-medium text-muted-foreground sm:col-span-2 lg:col-span-1">
              Nome do inventário
              <div className="relative">
                <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
                <UiInput
                  type="search"
                  aria-label="Buscar inventários"
                  placeholder="Buscar por nome…"
                  value={props.search}
                  onChange={(e) => props.onSearch(e.target.value)}
                  className="pl-10"
                />
              </div>
            </UiLabel>
            <UiLabel className="flex-col items-stretch gap-1.5 text-xs font-medium text-muted-foreground">
              Status
              <SelectField
                aria-label="Filtrar por status"
                options={Object.entries(statusLabels).map(([value, label]) => ({
                  value,
                  label,
                }))}
                value={props.status}
                onValueChange={(value) => props.onStatus(value as StatusFilter)}
              />
            </UiLabel>
            <UiLabel className="flex-col items-stretch gap-1.5 text-xs font-medium text-muted-foreground">
              Setor
              <SelectField
                aria-label="Filtrar por setor"
                options={[
                  { value: 'TODOS', label: 'Todos os setores' },
                  ...props.sectors.map((s) => ({
                    value: String(s.id),
                    label: s.name,
                  })),
                ]}
                value={props.sector}
                onValueChange={props.onSector}
                placeholder="Todos os setores"
              />
            </UiLabel>
            <UiLabel className="flex-col items-stretch gap-1.5 text-xs font-medium text-muted-foreground">
              Ano
              <SelectField
                aria-label="Filtrar por ano"
                options={[
                  { value: 'TODOS', label: 'Todos os anos' },
                  ...props.years.map((year) => ({
                    value: String(year),
                    label: String(year),
                  })),
                ]}
                value={props.year}
                onValueChange={props.onYear}
              />
            </UiLabel>
          </div>
        </CollapsibleContent>
      </Collapsible>
      {active.length > 0 && (
        <div className="flex flex-wrap items-center gap-2 pt-1">
          {active.map((item) => (
            <UiBadge
              variant="outline"
              className={cn('gap-1.5', chipToneClass.primary)}
              key={item.label}
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
