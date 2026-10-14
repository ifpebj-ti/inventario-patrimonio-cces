'use client'

import { badgeVariant } from '@/lib/tone'
import { Badge as UiBadge } from '@/components/ui/badge'
import { Card as UiCard } from '@/components/ui/card'

import { Button as UiButton } from '@/components/ui/button'
import { Input as UiInput } from '@/components/ui/input'

import type { SpreadsheetState } from '@/commons/models/spreadsheet'
import { ModalErrorsSheet } from '@/components/organisms/modalErrors'
import { CsvTemplateModal } from '@/components/organisms/csvTemplateModal'
import { useRef, useState } from 'react'
import { cn } from '@/lib/utils'
import { FileUp, Info, X } from 'lucide-react'

type FileUploadComponentProps = {
  state: SpreadsheetState
  onSelect: (file: File) => Promise<void>
  onRemove: () => void
  onProcess: () => Promise<void>
  disabled?: boolean
  blockedMessage?: string
}

export const FileUploadComponent = ({
  state,
  onSelect,
  onRemove,
  onProcess,
  disabled = false,
  blockedMessage,
}: FileUploadComponentProps) => {
  const [isDragOver, setIsDragOver] = useState(false)
  const [showErrorModal, setShowErrorModal] = useState(false)
  const [showTemplateModal, setShowTemplateModal] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const importing = state.status === 'importing'
  const locked = disabled || importing
  const canProcess = state.status === 'valid' && !!state.file && !locked

  const remove = () => {
    if (locked) return
    onRemove()
    setShowErrorModal(false)
    if (fileInputRef.current) fileInputRef.current.value = ''
  }

  return (
    <UiCard className="w-full h-full min-h-60 justify-between gap-3 p-5">
      <div className="flex items-center justify-between border-b pb-3">
        <div className="flex items-center gap-2.5">
          <div className="p-2 rounded-xl bg-primary/10 text-primary">
            <FileUp className="size-5" />
          </div>
          <h2 className="text-xl sm:text-2xl font-bold text-foreground">
            Importação
          </h2>
        </div>
        <UiBadge
          variant={badgeVariant.success}
          className="rounded-md font-semibold"
        >
          Apenas CSV
        </UiBadge>
      </div>

      <UiButton
        variant="outline"
        type="button"
        disabled={locked}
        aria-label="Selecionar ou soltar planilha CSV"
        className={cn(
          'h-auto min-h-20 w-full flex-col whitespace-normal border-2 border-dashed p-4 text-center',
          isDragOver
            ? 'border-primary bg-primary/10'
            : 'bg-muted hover:border-primary/25 hover:bg-primary/10',
        )}
        onClick={() => fileInputRef.current?.click()}
        onDragOver={(event) => {
          event.preventDefault()
          if (!locked) setIsDragOver(true)
        }}
        onDragLeave={() => setIsDragOver(false)}
        onDrop={(event) => {
          event.preventDefault()
          setIsDragOver(false)
          if (!locked && event.dataTransfer.files[0]) {
            setShowErrorModal(false)
            onSelect(event.dataTransfer.files[0])
          }
        }}
      >
        <FileUp className="size-8 text-muted-foreground" />
        <span className="text-xs font-normal text-muted-foreground">
          Arraste a planilha .csv ou clique para selecionar.
        </span>
      </UiButton>

      <UiButton
        variant="ghost"
        type="button"
        onClick={() => setShowTemplateModal(true)}
        className="text-xs text-primary hover:text-primary"
      >
        <Info /> Colunas esperadas
      </UiButton>

      {state.file && (
        <div className="p-3 rounded-lg bg-muted border flex flex-col gap-3">
          <p
            className="text-xs font-semibold text-foreground break-all"
            title={state.file.name}
          >
            {state.file.name}
          </p>
          <p
            role={state.status === 'invalid' ? 'alert' : 'status'}
            className={`text-xs ${state.status === 'invalid' ? 'text-destructive' : 'text-muted-foreground'}`}
          >
            {state.message}
          </p>
          {state.report && (
            <p
              className="text-xs text-muted-foreground"
              data-testid="import-counters"
            >
              Total processado: {state.report.totalProcessed} · Importados:{' '}
              {state.report.successes} · Falhas: {state.report.failures}
            </p>
          )}
          <div className="flex flex-wrap gap-2 justify-center">
            <UiButton
              variant="outline"
              size="sm"
              type="button"
              disabled={locked}
              onClick={remove}
            >
              <X /> Remover
            </UiButton>
            {state.errors.length > 0 && (
              <UiButton
                variant="destructive"
                size="sm"
                type="button"
                onClick={() => setShowErrorModal(true)}
              >
                Ver erros ({state.errors.length})
              </UiButton>
            )}
            {state.status === 'invalid' && (
              <UiButton
                variant="outline"
                size="sm"
                type="button"
                disabled={locked}
                onClick={() => state.file && onSelect(state.file)}
              >
                Validar novamente
              </UiButton>
            )}
            <UiButton
              size="sm"
              type="button"
              disabled={!canProcess}
              onClick={() => canProcess && onProcess()}
            >
              {importing ? 'Processando...' : 'Processar'}
            </UiButton>
          </div>
        </div>
      )}

      {blockedMessage && (
        <p
          role="status"
          className="flex items-start gap-1.5 text-xs text-muted-foreground"
        >
          <Info className="mt-0.5 size-3.5 shrink-0" />
          {blockedMessage}
        </p>
      )}

      <div className="flex justify-center pt-3 border-t">
        <UiButton
          variant={'default'}
          className="w-full sm:w-64"
          type="button"
          disabled={locked}
          onClick={() => fileInputRef.current?.click()}
        >
          Importar Planilha CSV
        </UiButton>
      </div>
      <UiInput
        ref={fileInputRef}
        type="file"
        aria-label="Arquivo CSV"
        className="hidden"
        disabled={locked}
        accept=".csv"
        onChange={(event) => {
          const file = event.target.files?.[0]
          event.target.value = ''
          if (file && !locked) {
            setShowErrorModal(false)
            onSelect(file)
          }
        }}
      />
      {showErrorModal && (
        <ModalErrorsSheet
          onClose={() => setShowErrorModal(false)}
          errors={state.errors}
          title={
            state.report
              ? 'Falhas da importação'
              : 'Erros encontrados na planilha'
          }
        />
      )}
      <CsvTemplateModal
        isOpen={showTemplateModal}
        onClose={() => setShowTemplateModal(false)}
      />
    </UiCard>
  )
}
