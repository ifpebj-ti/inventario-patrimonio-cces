'use client'

import React from 'react'
import { QrCode, Barcode, Camera } from 'lucide-react'

interface ScannerTriggerButtonProps {
  onClick: () => void
  variant?: 'primary' | 'card'
  className?: string
}

export const ScannerTriggerButton: React.FC<ScannerTriggerButtonProps> = ({
  onClick,
  variant = 'primary',
  className = '',
}) => {
  if (variant === 'card') {
    return (
      <button
        type="button"
        onClick={onClick}
        aria-label="Abrir leitor de código de barras ou QR Code"
        className={`group flex flex-col items-center justify-center gap-2 p-5 rounded-2xl border-2 border-dashed border-blue-300 bg-blue-50/50 hover:bg-blue-50 hover:border-blue-400 transition-all duration-200 cursor-pointer text-blue-500 w-full active:scale-[0.99] outline-none focus-visible:ring-2 focus-visible:ring-blue-400 ${className}`}
      >
        <div className="w-12 h-12 rounded-xl bg-blue-400 text-white flex items-center justify-center shadow-xs group-hover:scale-110 transition-transform">
          <Camera className="w-6 h-6" />
        </div>
        <div className="text-center">
          <span className="font-semibold text-sm block text-slate-800">
            Escanear Patrimônio
          </span>
          <span className="text-xs text-slate-500">
            Ler QR Code ou Código de Barras via Câmera
          </span>
        </div>
      </button>
    )
  }

  return (
    <button
      type="button"
      onClick={onClick}
      aria-label="Abrir leitor de código de barras ou QR Code"
      className={`min-h-[44px] px-4 py-2.5 rounded-xl bg-blue-400 hover:bg-blue-500 active:bg-blue-600 text-white font-medium text-xs sm:text-sm shadow-xs hover:shadow-md transition-all duration-200 flex items-center justify-center gap-2 cursor-pointer outline-none focus-visible:ring-2 focus-visible:ring-blue-400 focus-visible:ring-offset-2 shrink-0 active:scale-95 ${className}`}
    >
      <div className="flex items-center gap-1">
        <Barcode className="w-4 h-4 sm:w-5 sm:h-5 shrink-0" />
        <QrCode className="w-4 h-4 sm:w-5 sm:h-5 shrink-0" />
      </div>
      <span className="font-semibold whitespace-nowrap">Escanear Código</span>
    </button>
  )
}
