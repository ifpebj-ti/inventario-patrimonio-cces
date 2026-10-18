'use client'

import { Button as UiButton } from '@/components/ui/button'
import { Card as UiCard } from '@/components/ui/card'

import React from 'react'
import { Camera, Barcode, QrCode, Info } from 'lucide-react'

interface ScannerCardProps {
  onOpenScanner: () => void
  disabled?: boolean
  disabledMessage?: string
}

// Componente Card 2: Escaneamento via Câmera (Código de Barras e QR Code)
export const ScannerCard: React.FC<ScannerCardProps> = ({
  onOpenScanner,
  disabled = false,
  disabledMessage,
}) => {
  return (
    <UiCard className="w-full h-full min-h-60 justify-between gap-0 p-5">
      {/* Topo do Card com Título e Tag de Suporte */}
      <div className="flex items-center justify-between border-b pb-3">
        <div className="flex items-center gap-2.5">
          <div className="p-2 rounded-xl bg-primary/10 text-primary shrink-0">
            <Camera className="size-5" />
          </div>
          <div>
            <h2 className="text-xl sm:text-2xl font-bold text-foreground">
              Escaneamento
            </h2>
            {/* <p className="text-[11px] text-muted-foreground">Auditoria via câmera</p> */}
          </div>
        </div>
        {/* <span className="text-[11px] font-semibold px-2 py-0.5 rounded-full bg-primary/10 text-primary border border-primary/60">
          Câmera Web
        </span> */}
      </div>

      {/* Área Central: Visual representativo com suporte tanto a Código de Barras quanto QR Code */}
      <div className="flex flex-col items-center justify-center text-center my-auto py-3 gap-2.5">
        <div className="flex items-center justify-center gap-3 text-primary">
          <div className="p-2.5 rounded-xl bg-primary/10 border border-primary/25 flex items-center gap-1.5">
            <Barcode className="size-6 text-primary" />
            <span className="text-xs font-semibold text-foreground">
              Código de Barras
            </span>
          </div>
          <div className="p-2.5 rounded-xl bg-primary/10 border border-primary/25 flex items-center gap-1.5">
            <QrCode className="size-6 text-primary" />
            <span className="text-xs font-semibold text-foreground">
              QR Code
            </span>
          </div>
        </div>

        <p className="text-muted-foreground text-xs max-w-xs leading-relaxed">
          Aponte para a etiqueta patrimonial para validar.
        </p>
      </div>

      {disabled && disabledMessage && (
        <p
          role="status"
          className="flex items-start gap-1.5 pb-3 text-xs text-muted-foreground"
        >
          <Info className="mt-0.5 size-3.5 shrink-0" />
          {disabledMessage}
        </p>
      )}

      {/* Botão de Ação Padronizado com o Design System */}
      <div className="flex justify-center pt-3 border-t">
        <UiButton
          variant={'default'}
          className="w-full sm:w-64"
          type="button"
          disabled={disabled}
          onClick={onOpenScanner}
        >
          Escanear ou digitar código
        </UiButton>
      </div>
    </UiCard>
  )
}
