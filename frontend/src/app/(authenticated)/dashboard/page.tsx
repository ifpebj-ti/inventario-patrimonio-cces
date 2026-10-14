'use client'

import { SelectField } from '@/components/ui/select-field'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Card as UiCard } from '@/components/ui/card'
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip'

import { Button as UiButton } from '@/components/ui/button'

import { AccessGate } from '@/components/organisms/administration/shared'
import { useInventory } from '@/contexts/InventoryContext'
import type { Sector } from '@/commons/models/administration'
import { listSectors } from '@/services/administration'
import { InventoryResponse } from '@/services/inventory'
import {
  CalendarRange,
  CheckCircle2,
  Clock3,
  Package,
  PieChart,
  TrendingUp,
  TriangleAlert,
} from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useRouter } from 'next/navigation'

const parseDate = (value: string | null) => {
  if (!value) return null
  if (/^\d{2}\/\d{2}\/\d{4}$/.test(value)) {
    const [day, month, year] = value.split('/')
    return new Date(`${year}-${month}-${day}T00:00:00`)
  }
  return new Date(`${value}T00:00:00`)
}

const isOverdue = (inventory: InventoryResponse) => {
  const deadline = parseDate(inventory.plannedEndDate)
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return inventory.status !== 'CONCLUIDO' && !!deadline && deadline < today
}

const buildSmoothPath = (points: { x: number; y: number }[]) => {
  if (!points.length) return ''
  return points.reduce((path, point, index) => {
    if (index === 0) return `M ${point.x} ${point.y}`
    const previous = points[index - 1]
    const midpointX = (previous.x + point.x) / 2
    return `${path} C ${midpointX} ${previous.y}, ${midpointX} ${point.y}, ${point.x} ${point.y}`
  }, '')
}

function EmptyState({ text }: { text: string }) {
  return (
    <p className="py-16 text-center text-sm text-muted-foreground">{text}</p>
  )
}

function DashboardContent() {
  const { inventories, isLoading, error, refreshInventories } = useInventory()
  const router = useRouter()
  const [selectedYear, setSelectedYear] = useState('ALL')
  const [selectedSector, setSelectedSector] = useState('ALL')
  const [registeredSectors, setRegisteredSectors] = useState<Sector[]>([])

  useEffect(() => {
    const controller = new AbortController()
    listSectors(controller.signal)
      .then(setRegisteredSectors)
      .catch(() => setRegisteredSectors([]))
    return () => controller.abort()
  }, [])

  const years = useMemo(
    () =>
      Array.from(
        new Set(
          inventories
            .map((inventory) => parseDate(inventory.createdAt)?.getFullYear())
            .filter((year): year is number => Boolean(year)),
        ),
      ).sort((left, right) => right - left),
    [inventories],
  )

  const sectors = useMemo(
    () =>
      Array.from(
        new Map([
          ...registeredSectors.map(
            (sector) => [sector.id, sector.name] as const,
          ),
          ...inventories
            .filter((inventory) => inventory.sectorId && inventory.sectorName)
            .map(
              (inventory) =>
                [inventory.sectorId!, inventory.sectorName!] as const,
            ),
        ]),
      ).map(([id, name]) => ({ id, name })),
    [inventories, registeredSectors],
  )

  const periodInventories = useMemo(() => {
    return inventories.filter((inventory) => {
      const createdAt = parseDate(inventory.createdAt)
      return (
        (selectedYear === 'ALL' ||
          createdAt?.getFullYear() === Number(selectedYear)) &&
        (selectedSector === 'ALL' ||
          inventory.sectorId === Number(selectedSector))
      )
    })
  }, [inventories, selectedSector, selectedYear])

  const metrics = useMemo(() => {
    const concluded = periodInventories.filter(
      (inventory) => inventory.status === 'CONCLUIDO',
    ).length
    const inProgress = periodInventories.filter(
      (inventory) => inventory.status !== 'CONCLUIDO',
    ).length
    const overdue = periodInventories.filter(isOverdue).length
    return { total: periodInventories.length, concluded, inProgress, overdue }
  }, [periodInventories])

  const monthlyData = useMemo(() => {
    const months = new Map<string, { label: string; total: number }>()
    const year =
      selectedYear === 'ALL' ? new Date().getFullYear() : Number(selectedYear)
    for (let month = 0; month < 12; month += 1) {
      const cursor = new Date(year, month, 1)
      months.set(`${selectedYear}-${String(month + 1).padStart(2, '0')}`, {
        label: cursor.toLocaleDateString('pt-BR', { month: 'short' }),
        total: 0,
      })
    }
    periodInventories.forEach((inventory) => {
      const date = parseDate(inventory.createdAt)
      if (!date || Number.isNaN(date.getTime())) return
      const key = `${selectedYear}-${String(date.getMonth() + 1).padStart(2, '0')}`
      const current = months.get(key) || { label: key, total: 0 }
      current.total += 1
      months.set(key, current)
    })
    return Array.from(months.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([, value]) => value)
  }, [periodInventories, selectedYear])

  const statusChart = [
    { label: 'Concluídos', value: metrics.concluded, color: 'var(--success)' },
    {
      label: 'Em andamento no prazo',
      value: Math.max(metrics.inProgress - metrics.overdue, 0),
      color: 'var(--primary)',
    },
    { label: 'Atrasados', value: metrics.overdue, color: 'var(--destructive)' },
  ]
  const largestMonthTotal = Math.max(
    ...monthlyData.map((month) => month.total),
    1,
  )
  const chartMaximum = Math.max(10, Math.ceil(largestMonthTotal / 10) * 10)
  const donutTotal = statusChart.reduce((sum, item) => sum + item.value, 0)
  const linePoints = monthlyData.map((month, index) => ({
    x: 44 + (index * 632) / Math.max(monthlyData.length - 1, 1),
    y: 28 + (1 - month.total / chartMaximum) * 162,
  }))
  const linePath = buildSmoothPath(linePoints)
  const areaPath = linePoints.length
    ? `${linePath} L ${linePoints.at(-1)?.x} 206 L ${linePoints[0].x} 206 Z`
    : ''
  const gradient = statusChart.reduce((result, item, index) => {
    const previous = statusChart
      .slice(0, index)
      .reduce((sum, chartItem) => sum + chartItem.value, 0)
    const start = donutTotal ? (previous / donutTotal) * 100 : 0
    const end = donutTotal ? ((previous + item.value) / donutTotal) * 100 : 0
    return `${result}${item.color} ${start}% ${end}%, `
  }, '')

  const openList = (status?: 'CONCLUIDO' | 'OPEN' | 'VENCIDO') =>
    router.push(`/inventory${status ? `?status=${status}` : ''}`)

  return (
    <div className="page-shell flex min-h-screen flex-col gap-6">
      <header className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <h1 className="page-heading">Dashboard</h1>
          <p className="page-description">
            Acompanhe o volume e o andamento dos inventários por ano e setor.
          </p>
        </div>
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
          <div className="relative">
            <CalendarRange className="pointer-events-none absolute top-1/2 left-4 z-10 size-5 -translate-y-1/2 text-muted-foreground" />
            <SelectField
              aria-label="Ano do dashboard"
              className="h-12 min-w-52 bg-card pl-12 font-normal"
              options={[
                { value: 'ALL', label: 'Todos os anos' },
                ...years.map((year) => ({
                  value: String(year),
                  label: String(year),
                })),
              ]}
              value={selectedYear}
              onValueChange={setSelectedYear}
            />
          </div>
          <div className="relative">
            <SelectField
              aria-label="Setor do dashboard"
              className="h-12 min-w-52 bg-card"
              options={[
                { value: 'ALL', label: 'Todos os setores' },
                ...sectors.map((sector) => ({
                  value: String(sector.id),
                  label: sector.name,
                })),
              ]}
              value={selectedSector}
              onValueChange={setSelectedSector}
              placeholder="Todos os setores"
            />
          </div>
        </div>
      </header>

      {isLoading ? (
        <UiCard className="p-10 text-center text-sm text-muted-foreground">
          Carregando os indicadores do dashboard…
        </UiCard>
      ) : error ? (
        <Alert
          variant="destructive"
          className="border-destructive/25 bg-destructive/10 p-6"
        >
          <AlertDescription className="items-center text-center">
            <p className="font-medium">{error}</p>
            <UiButton
              variant="default"
              className="mt-4 w-full sm:w-52"
              type="button"
              onClick={() => refreshInventories()}
            >
              Tentar novamente
            </UiButton>
          </AlertDescription>
        </Alert>
      ) : (
        <>
          <section
            className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4"
            aria-label="Indicadores principais"
          >
            {[
              {
                label: 'Total de inventários',
                value: metrics.total,
                icon: Package,
                accent: 'bg-primary/10 text-primary',
                help: 'Todos os inventários criados no ano e no setor selecionados, finalizados ou não.',
              },
              {
                label: 'Concluídos',
                value: metrics.concluded,
                icon: CheckCircle2,
                accent: 'bg-success/10 text-success',
                status: 'CONCLUIDO' as const,
                help: 'Inventários que foram finalizados, mesmo que nem todos os itens tenham sido verificados.',
              },
              {
                label: 'Em andamento',
                value: metrics.inProgress,
                icon: Clock3,
                accent: 'bg-primary/10 text-primary',
                status: 'OPEN' as const,
                help: 'Inventários que ainda não foram finalizados, incluindo os que já passaram do prazo previsto.',
              },
              {
                label: 'Atrasados',
                value: metrics.overdue,
                icon: TriangleAlert,
                accent: 'bg-destructive/10 text-destructive',
                status: 'VENCIDO' as const,
                help: 'Inventários ainda não finalizados cuja data prevista de finalização já passou.',
              },
            ].map(({ label, value, icon: Icon, accent, status, help }) => (
              <Tooltip key={label}>
                <TooltipTrigger asChild>
                  <UiButton
                    variant="outline"
                    type="button"
                    onClick={() => openList(status)}
                    aria-describedby={`kpi-${status || 'total'}`}
                    className="h-auto flex-col items-stretch gap-0 rounded-xl bg-card p-5 text-left font-normal whitespace-normal shadow-sm transition-all hover:-translate-y-0.5 hover:border-primary/25 hover:bg-card hover:text-foreground hover:shadow-md"
                  >
                    <div className="flex items-start justify-between gap-3">
                      <div>
                        <p className="text-base font-medium text-muted-foreground">
                          {label}
                        </p>
                        <p className="mt-5 text-4xl font-bold tracking-tight text-foreground">
                          {value}
                        </p>
                      </div>
                      <span
                        className={`flex size-12 items-center justify-center rounded-full ${accent}`}
                      >
                        <Icon className="size-6" />
                      </span>
                    </div>
                    <span id={`kpi-${status || 'total'}`} className="sr-only">
                      {help}
                    </span>
                  </UiButton>
                </TooltipTrigger>
                <TooltipContent side="bottom" className="max-w-64">
                  {help}
                </TooltipContent>
              </Tooltip>
            ))}
          </section>
          <section
            className="grid gap-5 lg:grid-cols-2"
            aria-label="Gráficos do período"
          >
            <UiCard className="gap-0 p-6">
              <ChartTitle
                icon={<TrendingUp className="size-5" />}
                title="Evolução dos inventários"
                description="Registros criados por mês no período."
              />
              {monthlyData.length ? (
                <div className="mt-6 overflow-x-auto">
                  <svg
                    viewBox="0 0 720 248"
                    className="h-auto min-w-[620px] w-full"
                    role="img"
                    aria-label="Evolução mensal dos inventários"
                  >
                    <defs>
                      <linearGradient
                        id="inventory-area"
                        x1="0"
                        x2="0"
                        y1="0"
                        y2="1"
                      >
                        <stop
                          offset="0%"
                          stopColor="var(--primary)"
                          stopOpacity="0.28"
                        />
                        <stop
                          offset="100%"
                          stopColor="var(--primary)"
                          stopOpacity="0.02"
                        />
                      </linearGradient>
                    </defs>
                    {[0, 1, 2, 3, 4].map((line) => (
                      <g key={line}>
                        <line
                          x1="44"
                          y1={28 + line * 40.5}
                          x2="676"
                          y2={28 + line * 40.5}
                          stroke="var(--border)"
                          strokeWidth="1"
                        />
                        <text
                          x="30"
                          y={32 + line * 40.5}
                          textAnchor="end"
                          className="fill-muted-foreground text-[11px]"
                        >
                          {Math.round(chartMaximum - (chartMaximum * line) / 4)}
                        </text>
                      </g>
                    ))}
                    <path d={areaPath} fill="url(#inventory-area)" />
                    <path
                      d={linePath}
                      fill="none"
                      stroke="var(--primary)"
                      strokeWidth="3"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    />
                    {linePoints.map((point, index) => (
                      <g key={monthlyData[index].label}>
                        <circle
                          cx={point.x}
                          cy={point.y}
                          r="4.5"
                          fill="var(--primary)"
                          stroke="var(--card)"
                          strokeWidth="2"
                        />
                        <text
                          x={point.x}
                          y={Math.max(point.y - 11, 16)}
                          textAnchor="middle"
                          className="fill-foreground text-[11px] font-semibold"
                        >
                          {monthlyData[index].total}
                        </text>
                        <text
                          x={point.x}
                          y="228"
                          textAnchor="middle"
                          className="fill-muted-foreground text-[11px]"
                        >
                          {monthlyData[index].label}
                        </text>
                      </g>
                    ))}
                  </svg>
                </div>
              ) : (
                <EmptyState text="Não há inventários criados no período. Altere o período ou crie um novo inventário." />
              )}
            </UiCard>
            <UiCard className="gap-0 p-6">
              <ChartTitle
                icon={<PieChart className="size-5" />}
                title="Status dos inventários"
                description="Distribuição exclusiva para visualização."
              />
              {donutTotal ? (
                <div className="mt-6 flex flex-col items-center gap-6 sm:flex-row sm:justify-center">
                  <div
                    className="relative h-40 w-40 rounded-full"
                    style={{
                      background: `conic-gradient(${gradient.slice(0, -2)})`,
                    }}
                  >
                    <div className="absolute inset-7 flex items-center justify-center rounded-full bg-card text-center text-sm font-bold text-foreground">
                      {donutTotal}
                      <br />
                      <span className="text-xs font-medium text-muted-foreground">
                        total
                      </span>
                    </div>
                  </div>
                  <div className="space-y-3">
                    {statusChart.map((item) => (
                      <div
                        key={item.label}
                        className="flex items-center justify-between gap-8 text-sm text-foreground"
                      >
                        <span className="flex items-center gap-2">
                          <span
                            className="size-3 rounded-full"
                            style={{ backgroundColor: item.color }}
                          />
                          {item.label}
                        </span>
                        <span className="flex items-center gap-3">
                          <strong>{item.value}</strong>
                          <span className="w-9 text-right text-xs text-muted-foreground">
                            {donutTotal
                              ? Math.round((item.value / donutTotal) * 100)
                              : 0}
                            %
                          </span>
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              ) : (
                <EmptyState text="Não há dados de status no período. Ajuste o período para consultar outro intervalo." />
              )}
            </UiCard>
          </section>
        </>
      )}
    </div>
  )
}

function ChartTitle({
  icon,
  title,
  description,
}: {
  icon: React.ReactNode
  title: string
  description: string
}) {
  return (
    <div className="flex items-center gap-2.5 border-b pb-3">
      <div className="rounded-lg bg-primary/10 p-2 text-primary">{icon}</div>
      <div>
        <h2 className="text-xl font-bold text-foreground">{title}</h2>
        <p className="text-sm text-muted-foreground">{description}</p>
      </div>
    </div>
  )
}

export default function Dashboard() {
  return (
    <AccessGate admin redirectToHome>
      <DashboardContent />
    </AccessGate>
  )
}
