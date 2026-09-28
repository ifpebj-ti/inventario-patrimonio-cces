'use client'

import React, { useEffect, useRef, useState } from 'react'
import { Html5Qrcode, Html5QrcodeSupportedFormats } from 'html5-qrcode'
import { X, Camera, AlertCircle, RefreshCw } from 'lucide-react'
import { AnimatePresence, motion } from 'framer-motion'

interface BarcodeQrScannerModalProps {
  isOpen: boolean
  onClose: () => void
  onScanSuccess: (decodedText: string) => void
}

// Emite um feedback sonoro elegante via Web Audio API (sem dependências externas de áudio)
const playSuccessSound = () => {
  try {
    const AudioContextClass =
      window.AudioContext ||
      (window as unknown as { webkitAudioContext: typeof AudioContext })
        .webkitAudioContext
    if (!AudioContextClass) return

    const audioCtx = new AudioContextClass()
    const osc = audioCtx.createOscillator()
    const gain = audioCtx.createGain()

    osc.type = 'sine'
    osc.frequency.setValueAtTime(880, audioCtx.currentTime) // Nota Lá 5
    gain.gain.setValueAtTime(0.2, audioCtx.currentTime)
    gain.gain.exponentialRampToValueAtTime(0.001, audioCtx.currentTime + 0.18)

    osc.connect(gain)
    gain.connect(audioCtx.destination)

    osc.start()
    osc.stop(audioCtx.currentTime + 0.18)
  } catch (error) {
    console.warn(
      'AudioContext não permitido ou bloqueado pelo navegador:',
      error,
    )
  }
}

export const BarcodeQrScannerModal: React.FC<BarcodeQrScannerModalProps> = ({
  isOpen,
  onClose,
  onScanSuccess,
}) => {
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [hasDetected, setHasDetected] = useState(false)
  const [isInitializing, setIsInitializing] = useState(true)
  const scannerRef = useRef<Html5Qrcode | null>(null)
  const isStoppingRef = useRef(false)
  const isDetectedRef = useRef(false)

  const stopScanner = async () => {
    if (
      scannerRef.current &&
      scannerRef.current.isScanning &&
      !isStoppingRef.current
    ) {
      isStoppingRef.current = true
      try {
        await scannerRef.current.stop()
        scannerRef.current.clear()
      } catch (err) {
        console.error('Erro ao parar scanner:', err)
      } finally {
        isStoppingRef.current = false
      }
    }
  }

  useEffect(() => {
    if (!isOpen) {
      stopScanner()
      isDetectedRef.current = false
      setHasDetected(false)
      setErrorMessage(null)
      return
    }

    isDetectedRef.current = false
    setIsInitializing(true)
    setErrorMessage(null)
    setHasDetected(false)

    const scannerElementId = 'barcode-qr-reader-target'

    // Formatos universais para códigos de barras patrimoniais (1D) e QR Codes (2D)
    const formatsToSupport = [
      Html5QrcodeSupportedFormats.QR_CODE,
      Html5QrcodeSupportedFormats.CODE_128,
      Html5QrcodeSupportedFormats.CODE_39,
      Html5QrcodeSupportedFormats.CODE_93,
      Html5QrcodeSupportedFormats.EAN_13,
      Html5QrcodeSupportedFormats.EAN_8,
      Html5QrcodeSupportedFormats.UPC_A,
      Html5QrcodeSupportedFormats.UPC_E,
      Html5QrcodeSupportedFormats.CODABAR,
      Html5QrcodeSupportedFormats.ITF,
      Html5QrcodeSupportedFormats.DATA_MATRIX,
    ]

    const html5QrCode = new Html5Qrcode(scannerElementId, {
      formatsToSupport,
      verbose: false,
      experimentalFeatures: {
        useBarCodeDetectorIfSupported: true,
      },
    })

    scannerRef.current = html5QrCode

    // Inicia a câmera priorizando a lente traseira em dispositivos móveis
    html5QrCode
      .start(
        { facingMode: 'environment' },
        {
          fps: 20,
          qrbox: (viewfinderWidth, viewfinderHeight) => {
            const width = Math.min(Math.floor(viewfinderWidth * 0.88), 350)
            const height = Math.min(Math.floor(viewfinderHeight * 0.65), 230)
            return { width, height }
          },
          aspectRatio: 1.0,
        },
        (decodedText) => {
          if (isDetectedRef.current) return
          isDetectedRef.current = true
          setHasDetected(true)

          // Pausa imediatamente a câmera para não disparar mais leituras de frames subsequentes
          try {
            if (scannerRef.current && scannerRef.current.isScanning) {
              scannerRef.current.pause(true)
            }
          } catch {
            // Silencia falha se pause não for suportado no driver
          }

          // 1. Feedback Sonoro
          playSuccessSound()

          // 2. Feedback Tátil
          if (typeof window !== 'undefined' && navigator.vibrate) {
            navigator.vibrate([40, 30, 40])
          }

          // 3. Notifica o componente pai e fecha sem repetição
          setTimeout(() => {
            onScanSuccess(decodedText.trim())
            onClose()
          }, 250)
        },
        () => {
          // Frame sem leitura
        },
      )
      .then(() => {
        setIsInitializing(false)
      })
      .catch((err) => {
        console.error('Erro na inicialização da câmera:', err)
        setIsInitializing(false)
        setErrorMessage(
          'Não foi possível inicializar a câmera. Verifique se o navegador possui permissão de acesso à câmera e se a conexão é segura (HTTPS ou localhost).',
        )
      })

    return () => {
      stopScanner()
    }
  }, [isOpen])

  if (!isOpen) return null

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-6 select-none font-['Montserrat',sans-serif]">
        {/* Backdrop com desfoque */}
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={onClose}
          aria-hidden="true"
          className="fixed inset-0 bg-black/80 backdrop-blur-sm"
        />

        {/* Modal Card */}
        <motion.div
          role="dialog"
          aria-modal="true"
          aria-label="Scanner de código de barras e QR Code"
          initial={{ opacity: 0, scale: 0.95, y: 12 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.95, y: 12 }}
          transition={{ duration: 0.2 }}
          className="relative w-full max-w-md bg-slate-900 rounded-3xl overflow-hidden shadow-2xl border border-slate-800 z-10 flex flex-col"
        >
          {/* Topo do Scanner */}
          <div className="flex items-center justify-between px-5 py-4 border-b border-slate-800 bg-slate-900/95 text-white">
            <div className="flex items-center gap-2.5">
              <div className="p-2 rounded-xl bg-blue-500/20 text-blue-400">
                <Camera className="w-5 h-5" />
              </div>
              <div>
                <h3 className="font-semibold text-sm">Leitor de Patrimônio</h3>
                <p className="text-[11px] text-slate-400">
                  Aponte para o código ou etiqueta
                </p>
              </div>
            </div>

            <button
              onClick={onClose}
              aria-label="Fechar scanner"
              className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-xl text-slate-400 hover:text-white hover:bg-slate-800 transition-colors cursor-pointer"
            >
              <X className="w-5 h-5" />
            </button>
          </div>

          {/* Área de Visualização da Câmera */}
          <div className="relative w-full aspect-square bg-black flex items-center justify-center overflow-hidden">
            {/* Elemento de renderização do html5-qrcode */}
            <div
              id="barcode-qr-reader-target"
              className="w-full h-full object-cover"
            />

            {/* Spinner de Inicialização */}
            {isInitializing && !errorMessage && (
              <div className="absolute inset-0 flex flex-col items-center justify-center bg-slate-900 gap-3 text-white">
                <RefreshCw className="w-8 h-8 text-blue-400 animate-spin" />
                <span className="text-xs font-medium text-slate-300">
                  Acessando câmera...
                </span>
              </div>
            )}

            {/* Overlay com Retículo de Mira & Scanline */}
            {!isInitializing && !errorMessage && (
              <div className="absolute inset-0 pointer-events-none flex items-center justify-center p-6">
                <div
                  className={`relative w-full max-w-[280px] h-36 rounded-2xl border-2 transition-all duration-300 ${
                    hasDetected
                      ? 'border-emerald-400 shadow-[0_0_30px_rgba(52,211,153,0.8)] bg-emerald-500/20'
                      : 'border-blue-400 shadow-[0_0_15px_rgba(96,165,250,0.4)]'
                  }`}
                >
                  {/* Linha de varredura animada */}
                  {!hasDetected && (
                    <div className="w-full h-0.5 bg-gradient-to-r from-transparent via-blue-400 to-transparent shadow-[0_0_8px_#60a5fa] animate-pulse mt-16" />
                  )}

                  {/* Cantoneiras estilizadas de foco */}
                  <div className="absolute -top-1 -left-1 w-4 h-4 border-t-4 border-l-4 border-white rounded-tl" />
                  <div className="absolute -top-1 -right-1 w-4 h-4 border-t-4 border-r-4 border-white rounded-tr" />
                  <div className="absolute -bottom-1 -left-1 w-4 h-4 border-b-4 border-l-4 border-white rounded-bl" />
                  <div className="absolute -bottom-1 -right-1 w-4 h-4 border-b-4 border-r-4 border-white rounded-br" />
                </div>
              </div>
            )}

            {/* Alerta de Erro de Permissão */}
            {errorMessage && (
              <div className="absolute inset-0 bg-slate-950 flex flex-col items-center justify-center p-6 text-center gap-3">
                <AlertCircle className="w-10 h-10 text-red-400" />
                <p className="text-xs sm:text-sm text-red-200 leading-relaxed font-medium">
                  {errorMessage}
                </p>
                <button
                  type="button"
                  onClick={onClose}
                  className="mt-2 px-4 py-2 bg-slate-800 hover:bg-slate-700 text-white rounded-xl text-xs font-semibold cursor-pointer"
                >
                  Fechar
                </button>
              </div>
            )}
          </div>

          {/* Dica de Rodapé */}
          <div className="p-4 bg-slate-900 border-t border-slate-800 text-center">
            <p className="text-xs text-slate-400">
              Posicione a etiqueta patrimonial dentro da moldura para leitura
              automática.
            </p>
          </div>
        </motion.div>
      </div>
    </AnimatePresence>
  )
}
