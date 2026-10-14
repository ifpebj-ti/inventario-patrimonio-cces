'use client'

import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog'
import { Button as UiButton } from '@/components/ui/button'
import { Spinner } from '@/components/ui/spinner'
import { Label as UiLabel } from '@/components/ui/label'
import { Input as UiInput } from '@/components/ui/input'

import React, { useEffect, useRef, useState } from 'react'
import { Html5Qrcode, Html5QrcodeSupportedFormats } from 'html5-qrcode'
import { X, Camera, AlertCircle } from 'lucide-react'

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
  const [manualCode, setManualCode] = useState('')
  const [isSubmittingCode, setIsSubmittingCode] = useState(false)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [hasDetected, setHasDetected] = useState(false)
  const [isInitializing, setIsInitializing] = useState(true)
  const [targetEl, setTargetEl] = useState<HTMLDivElement | null>(null)
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
    setManualCode('')
    setIsSubmittingCode(false)
    setIsInitializing(true)
    setErrorMessage(null)
    setHasDetected(false)
  }, [isOpen])

  // A câmera só inicia quando o elemento alvo já existe no DOM: o conteúdo do
  // Dialog é renderizado em um portal, depois do primeiro commit.
  useEffect(() => {
    if (!isOpen || !targetEl) return

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
          'Não foi possível inicializar a câmera. Você pode digitar o código patrimonial no campo abaixo para buscar o item.',
        )
      })

    return () => {
      stopScanner()
    }
  }, [isOpen, targetEl])

  if (!isOpen) return null

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && !false) onClose()
      }}
    >
      <DialogContent
        showCloseButton={false}
        className="flex max-h-[90vh] flex-col overflow-y-auto rounded-xl gap-0 overflow-hidden p-0 sm:max-w-md"
      >
        <DialogTitle className="sr-only">
          Scanner de código de barras e QR Code
        </DialogTitle>
        {/* Topo do Scanner */}
        <div className="flex items-center justify-between px-5 py-4 border-b text-foreground">
          <div className="flex items-center gap-2.5">
            <div className="p-2 rounded-xl bg-primary/10 text-primary">
              <Camera className="size-5" />
            </div>
            <div>
              <h3 className="font-semibold text-sm">Leitor de Patrimônio</h3>
              <p className="text-[11px] text-muted-foreground">
                Aponte para a etiqueta patrimonial para validar.
              </p>
            </div>
          </div>

          <UiButton
            variant="ghost"
            size="icon-lg"
            onClick={onClose}
            aria-label="Fechar scanner"
            className="text-muted-foreground"
          >
            <X className="size-5" />
          </UiButton>
        </div>

        {/* Área de Visualização da Câmera */}
        <div className="relative w-full aspect-[4/3] bg-muted flex items-center justify-center overflow-hidden">
          {/* Elemento de renderização do html5-qrcode */}
          <div
            ref={setTargetEl}
            id="barcode-qr-reader-target"
            className="w-full h-full object-cover"
          />

          {/* Spinner de Inicialização */}
          {isInitializing && !errorMessage && (
            <div className="absolute inset-0 flex flex-col items-center justify-center bg-muted gap-3 text-foreground">
              <Spinner className="size-8 text-primary" />
              <span className="text-xs font-medium text-muted-foreground">
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
                    ? 'border-success shadow-lg shadow-success/40 bg-success/20'
                    : 'border-primary shadow-lg shadow-primary/40'
                }`}
              >
                {/* Linha de varredura animada */}
                {!hasDetected && (
                  <div className="w-full h-0.5 bg-gradient-to-r from-transparent via-primary to-transparent shadow-sm shadow-primary animate-pulse mt-16" />
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
            <div className="absolute inset-0 bg-destructive/10 flex flex-col items-center justify-center p-6 text-center gap-3">
              <AlertCircle className="size-10 text-destructive" />
              <p className="text-xs sm:text-sm text-destructive leading-relaxed font-medium">
                {errorMessage}
              </p>
              <UiButton
                variant="outline"
                size="sm"
                type="button"
                onClick={onClose}
                className="mt-2"
              >
                Fechar
              </UiButton>
            </div>
          )}
        </div>

        <div className="p-5 border-t">
          <p className="text-xs text-muted-foreground mb-4">
            Ou informe o código patrimonial abaixo e clique em Buscar item.
          </p>
          <form
            onSubmit={async (event) => {
              event.preventDefault()
              const code = manualCode.trim()
              if (!code || isSubmittingCode || isDetectedRef.current) return
              isDetectedRef.current = true
              setIsSubmittingCode(true)
              await stopScanner()
              onScanSuccess(code)
              onClose()
            }}
            className="space-y-3"
          >
            <UiLabel
              htmlFor="manual-patrimony-code"
              className="text-sm font-medium text-foreground"
            >
              Código patrimonial
            </UiLabel>
            <UiInput
              id="manual-patrimony-code"
              value={manualCode}
              onChange={(event) => setManualCode(event.target.value)}
              placeholder="Digite o código da etiqueta"
              autoComplete="off"
              disabled={isSubmittingCode || hasDetected}
            />
            <UiButton
              variant="default"
              className="w-full"
              type="submit"
              disabled={!manualCode.trim() || isSubmittingCode || hasDetected}
            >
              {isSubmittingCode ? 'Buscando...' : 'Buscar item'}
            </UiButton>
          </form>
        </div>
      </DialogContent>
    </Dialog>
  )
}
