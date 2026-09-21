'use client'

import React from 'react'
import { Camera, Barcode, QrCode } from 'lucide-react'
import { Button } from '@/components/atoms/button'

interface ScannerCardProps {
  onOpenScanner: () => void
}

// Componente Card 2: Escaneamento via Câmera (Código de Barras e QR Code)
export const ScannerCard: React.FC<ScannerCardProps> = ({ onOpenScanner }) => {
  return (
    <div className="bg-white rounded-2xl shadow-md p-6 border border-slate-200/80 w-full h-full min-h-[290px] flex flex-col justify-between">
      {/* Topo do Card com Título e Tag de Suporte */}
      <div className="flex items-center justify-between border-b border-slate-100 pb-3">
        <div className="flex items-center gap-2.5">
          <div className="p-2 rounded-xl bg-blue-50 text-blue-500 shrink-0">
            <Camera className="w-5 h-5" />
          </div>
          <div>
            <h2 className="font-['Linden_Hill',serif] text-xl sm:text-2xl font-bold text-slate-800">
              Escaneamento
            </h2>
            <p className="text-[11px] text-slate-400">Auditoria via câmera</p>
          </div>
        </div>
        <span className="text-[11px] font-semibold px-2 py-0.5 rounded-full bg-blue-50 text-blue-600 border border-blue-200/60">
          Câmera Web
        </span>
      </div>

      {/* Área Central: Visual representativo com suporte tanto a Código de Barras quanto QR Code */}
      <div className="flex flex-col items-center justify-center text-center my-auto py-3 gap-2.5">
        <div className="flex items-center justify-center gap-3 text-blue-400">
          <div className="p-2.5 rounded-2xl bg-blue-50/80 border border-blue-100 flex items-center gap-1.5">
            <Barcode className="w-6 h-6 text-blue-500" />
            <span className="text-xs font-semibold text-slate-700">
              Código de Barras
            </span>
          </div>
          <div className="p-2.5 rounded-2xl bg-blue-50/80 border border-blue-100 flex items-center gap-1.5">
            <QrCode className="w-6 h-6 text-blue-500" />
            <span className="text-xs font-semibold text-slate-700">
              QR Code
            </span>
          </div>
        </div>

        <p className="text-slate-500 text-xs max-w-xs leading-relaxed">
          Aponte para a etiqueta patrimonial para validar o bem instantaneamente
          e travar a carga.
        </p>
      </div>

      {/* Botão de Ação Padronizado com o Design System */}
      <div className="flex justify-center pt-3 border-t border-slate-100">
        <Button
          text="Escanear Código / Câmera"
          variant={3}
          type="button"
          onClick={onOpenScanner}
          width="w-full sm:w-64"
        />
      </div>
    </div>
  )
}
