import { Badge as UiBadge } from '@/components/ui/badge'
import { Card as UiCard } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'

import React from 'react'
import { ItemsValid } from '@/components/molecules/inventoryItemsStatus/types'
import { BarChart3 } from 'lucide-react'

// Componente Card 1: Estatísticas de Verificação do Inventário
export const InventoryItemsStatus = ({ content = [] }: ItemsValid) => {
  const verifiedCount = content.filter((item) => Boolean(item.isValid)).length
  const unverifiedCount = content.length - verifiedCount

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

      {/* Contadores dinâmicos de Itens verificados (Verde) e não verificados (Vermelho) */}
      <div className="flex justify-evenly items-center gap-4 my-auto py-4">
        <div className="text-center flex-1">
          <div className="text-4xl sm:text-5xl font-light text-success mb-1">
            {verifiedCount}
          </div>
          <div className="text-muted-foreground text-xs sm:text-sm font-semibold flex items-center justify-center gap-1.5">
            <span className="size-2 rounded-full bg-success inline-block shrink-0" />
            Itens verificados
          </div>
        </div>

        <Separator orientation="vertical" className="h-12!" />

        <div className="text-center flex-1">
          <div className="text-4xl sm:text-5xl font-light text-destructive mb-1">
            {unverifiedCount}
          </div>
          <div className="text-muted-foreground text-xs sm:text-sm font-semibold flex items-center justify-center gap-1.5">
            <span className="size-2 rounded-full bg-destructive inline-block shrink-0" />
            Itens não verificados
          </div>
        </div>
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
