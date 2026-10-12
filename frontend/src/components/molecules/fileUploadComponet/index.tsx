'use client'

import type { SpreadsheetState } from '@/commons/models/spreadsheet'
import { Button } from '@/components/atoms/button'
import { ModalErrorsSheet } from '@/components/organisms/modalErrors'
import { CsvTemplateModal } from '@/components/organisms/csvTemplateModal'
import { useRef, useState } from 'react'
import { TbFileUpload, TbInfoCircle, TbX } from 'react-icons/tb'

type FileUploadComponentProps = {
  state: SpreadsheetState
  onSelect: (file: File) => Promise<void>
  onRemove: () => void
  onProcess: () => Promise<void>
  disabled?: boolean
}

export const FileUploadComponent = ({
  state,
  onSelect,
  onRemove,
  onProcess,
  disabled = false,
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
    <div className="bg-white rounded-2xl shadow-md p-6 border border-slate-200/80 w-full h-full min-h-[290px] flex flex-col justify-between gap-3">
      <div className="flex items-center justify-between border-b border-slate-100 pb-3">
        <div className="flex items-center gap-2.5">
          <div className="p-2 rounded-xl bg-blue-50 text-blue-500">
            <TbFileUpload className="w-5 h-5" />
          </div>
          <h2 className="text-xl sm:text-2xl font-bold text-slate-800">
            Importação
          </h2>
        </div>
        <span className="text-xs font-bold px-2.5 py-1 rounded-full bg-emerald-50 text-emerald-700">
          Apenas CSV
        </span>
      </div>

      <button
        type="button"
        disabled={locked}
        aria-label="Selecionar ou soltar planilha CSV"
        className={`p-4 text-center rounded-xl border-2 border-dashed flex flex-col items-center gap-2 disabled:opacity-50 ${isDragOver ? 'border-blue-400 bg-blue-50' : 'border-slate-200 bg-slate-50'}`}
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
        <TbFileUpload className="w-8 h-8 text-slate-400" />
        <span className="text-xs text-slate-600">
          Arraste a planilha .csv ou clique para selecionar.
        </span>
      </button>

      <button
        type="button"
        onClick={() => setShowTemplateModal(true)}
        className="text-blue-600 text-xs flex items-center justify-center gap-1"
      >
        <TbInfoCircle /> Colunas esperadas
      </button>

      {state.file && (
        <div className="p-3 rounded-xl bg-slate-50 border border-slate-200 flex flex-col gap-3">
          <p
            className="text-xs font-semibold text-slate-900 break-all"
            title={state.file.name}
          >
            {state.file.name}
          </p>
          <p
            role={state.status === 'invalid' ? 'alert' : 'status'}
            className={`text-xs ${state.status === 'invalid' ? 'text-red-700' : 'text-slate-700'}`}
          >
            {state.message}
          </p>
          {state.report && (
            <p className="text-xs text-slate-700" data-testid="import-counters">
              Total processado: {state.report.totalProcessed} · Importados:{' '}
              {state.report.successes} · Falhas: {state.report.failures}
            </p>
          )}
          <div className="flex flex-wrap gap-2 justify-center">
            <button
              type="button"
              disabled={locked}
              onClick={remove}
              className="flex items-center gap-1 px-3 py-1 bg-slate-200 rounded-lg text-xs disabled:opacity-50"
            >
              <TbX /> Remover
            </button>
            {state.errors.length > 0 && (
              <button
                type="button"
                onClick={() => setShowErrorModal(true)}
                className="px-3 py-1 bg-red-100 text-red-700 rounded-lg text-xs"
              >
                Ver erros ({state.errors.length})
              </button>
            )}
            {state.status === 'invalid' && (
              <button
                type="button"
                disabled={locked}
                onClick={() => state.file && onSelect(state.file)}
                className="px-3 py-1 bg-slate-200 rounded-lg text-xs disabled:opacity-50"
              >
                Validar novamente
              </button>
            )}
            <button
              type="button"
              disabled={!canProcess}
              onClick={() => canProcess && onProcess()}
              className="px-3 py-1 bg-blue-500 text-white rounded-lg text-xs disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {importing ? 'Processando...' : 'Processar'}
            </button>
          </div>
        </div>
      )}

      <div className="flex justify-center pt-3 border-t border-slate-100">
        <Button
          text="Importar Planilha CSV"
          variant={3}
          type="button"
          disabled={locked}
          onClick={() => fileInputRef.current?.click()}
          width="w-full sm:w-64"
        />
      </div>
      <input
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
    </div>
  )
}
