import React from 'react'
import { ItemsValid } from '@/components/molecules/inventoryItemsStatus/types'
import { BarChart3 } from 'lucide-react'

// Componente Card 1: Estatísticas de Verificação do Inventário
export const InventoryItemsStatus = ({ content = [] }: ItemsValid) => {
  const verifiedCount = content.filter((item) => item.isValid).length
  const unverifiedCount = content.filter((item) => !item.isValid).length

  return (
    <div className="bg-white rounded-2xl shadow-md p-6 border border-slate-200/80 w-full h-full min-h-[290px] flex flex-col justify-between">
      {/* Topo do Card com Título e Indicador do Total */}
      <div className="flex items-center justify-between border-b border-slate-100 pb-3">
        <div className="flex items-center gap-2.5">
          <div className="p-2 rounded-xl bg-blue-50 text-blue-500 shrink-0">
            <BarChart3 className="w-5 h-5" />
          </div>
          <div>
            <h2 className="font-['Linden_Hill',serif] text-xl sm:text-2xl font-bold text-slate-800">
              Estatísticas
            </h2>
            {/* <p className="text-[11px] text-slate-400">
              Progresso da conferência
            </p> */}
          </div>
        </div>
        <span className="text-xs font-semibold px-2.5 py-1 rounded-full bg-slate-100 text-slate-600">
          Total: {content.length}
        </span>
      </div>

      {/* Contadores dinâmicos de Itens verificados (Verde) e não verificados (Vermelho) */}
      <div className="flex justify-evenly items-center gap-4 my-auto py-4">
        <div className="text-center flex-1">
          <div className="text-4xl sm:text-5xl font-light text-emerald-500 mb-1">
            {verifiedCount}
          </div>
          <div className="text-slate-600 text-xs sm:text-sm font-semibold flex items-center justify-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-emerald-500 inline-block shrink-0" />
            Itens verificados
          </div>
        </div>

        <div className="h-12 w-px bg-slate-200" />

        <div className="text-center flex-1">
          <div className="text-4xl sm:text-5xl font-light text-red-500 mb-1">
            {unverifiedCount}
          </div>
          <div className="text-slate-600 text-xs sm:text-sm font-semibold flex items-center justify-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-red-500 inline-block shrink-0" />
            Itens não verificados
          </div>
        </div>
      </div>

      {/* Texto instrutivo atualizado conforme especificação */}
      <div className="text-center pt-3 border-t border-slate-100">
        {/* <p className="text-slate-500 text-xs sm:text-sm">
          Utilize a câmera do seu dispositivo para escanear os itens
        </p> */}
      </div>
    </div>
  )
}
