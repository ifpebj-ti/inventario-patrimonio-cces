import { Badge as UiBadge } from '@/components/ui/badge'
import { Card as UiCard } from '@/components/ui/card'

import React from 'react'
import { ItemsValid } from '@/components/molecules/inventoryItemsStatus/types'
import { BarChart3 } from 'lucide-react'
import { validationStatusOf } from '@/components/ui/status-badge'

// Cores de cada contador (classes literais para o Tailwind enxergá-las).
const counterText: Record<string, string> = {
  success: 'text-success',
  warning: 'text-warning',
  destructive: 'text-destructive',
}
const counterDot: Record<string, string> = {
  success: 'bg-success',
  warning: 'bg-warning',
  destructive: 'bg-destructive',
}

// Componente Card 1: Estatísticas de Verificação do Inventário
export const InventoryItemsStatus = ({ content = [] }: ItemsValid) => {
  const statuses = content.map(validationStatusOf)
  const verifiedCount = statuses.filter((s) => s === 'VALIDADO').length
  const notFoundCount = statuses.filter((s) => s === 'NAO_LOCALIZADO').length
  const pendingCount = content.length - verifiedCount - notFoundCount

  return (
    <UiCard className="w-full h-full min-h-60 justify-between gap-0 p-5">
      {/* Topo do Card com Título e Indicador do Total */}
      <div className="flex items-center justify-between border-b pb-3">
        <div className="flex items-center gap-2.5">
          <div className="p-2 rounded-xl bg-primary/10 text-primary shrink-0">
            <BarChart3 className="size-5" />
          </div>
          <div>
            <h2 className="text-xl sm:text-2xl font-bold text-foreground">
              Estatísticas
            </h2>
            {/* <p className="text-[11px] text-muted-foreground">
              Progresso da conferência
            </p> */}
          </div>
        </div>
        <UiBadge variant="secondary" className="font-semibold">
          Total: {content.length}
        </UiBadge>
      </div>

      {/* Contadores: verificados (verde), não localizados (amarelo) e pendentes (vermelho) */}
      {/* Grade de três colunas que encolhe: com três contadores uma linha única estoura a largura
          de um celular de 320 px, então o rótulo quebra em vez de empurrar o card. */}
      <div className="grid grid-cols-3 divide-x my-auto py-4">
        {[
          { count: verifiedCount, label: 'Itens verificados', tone: 'success' },
          { count: notFoundCount, label: 'Não localizados', tone: 'warning' },
          {
            count: pendingCount,
            label: 'Itens pendentes',
            tone: 'destructive',
          },
        ].map(({ count, label, tone }) => (
          <div key={label} className="min-w-0 px-1 text-center sm:px-3">
            <div
              className={`mb-1 text-3xl font-light sm:text-5xl ${counterText[tone]}`}
            >
              {count}
            </div>
            <div className="flex flex-wrap items-center justify-center gap-x-1.5 text-[11px] leading-tight font-semibold text-muted-foreground sm:text-sm">
              <span
                className={`inline-block size-2 shrink-0 rounded-full ${counterDot[tone]}`}
              />
              {label}
            </div>
          </div>
        ))}
      </div>

      {/* Texto instrutivo atualizado conforme especificação */}
      <div className="text-center pt-3 border-t">
        {/* <p className="text-muted-foreground text-xs sm:text-sm">
          Utilize a câmera do seu dispositivo para escanear os itens
        </p> */}
      </div>
    </UiCard>
  )
}
