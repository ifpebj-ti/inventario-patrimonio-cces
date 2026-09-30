'use client'

import { errorSheet } from '@/app/(authenticated)/inventory/[id]/page'
import { Button } from '@/components/atoms/button'
import { ModalErrorsSheet } from '@/components/organisms/modalErrors'
import { CsvTemplateModal } from '@/components/organisms/csvTemplateModal'
import { truncateFileName } from '@/commons/utils/truncateFileName'
import React, { useRef, useState } from 'react'
import { TbFileUpload, TbInfoCircle, TbAlertCircle, TbX } from 'react-icons/tb'
import toast from 'react-hot-toast'

// Define as propriedades que o componente de upload de arquivo espera receber.
type FileUploadComponentProps = {
  onFileSelect: (file: File) => void // Função chamada para o processamento final do arquivo.
  onValidateFile?: (file: File) => void // Função opcional para validar o arquivo antes do processamento.
  validationErrors: errorSheet[] | null // Array de erros de validação recebido do componente pai.
}

interface RejectionErrorState {
  title: string
  message: string
  fullFileName?: string
}

// Componente Card 3: Importação de Planilha via Drag-and-Drop ou Seleção
export const FileUploadComponent = ({
  onFileSelect,
  onValidateFile,
  validationErrors,
}: FileUploadComponentProps) => {
  const [isDragOver, setIsDragOver] = useState(false)
  const [uploadedFile, setUploadedFile] = useState<File | null>(null)
  const [rejectionError, setRejectionError] =
    useState<RejectionErrorState | null>(null)
  const [showErrorModal, setShowErrorModal] = useState(false)
  const [showTemplateModal, setShowTemplateModal] = useState(false)

  const openModal = () => setShowErrorModal(true)
  const closeModal = () => setShowErrorModal(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  const handleDragOver = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault()
    setIsDragOver(true)
  }

  const handleDragLeave = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault()
    setIsDragOver(false)
  }

  // Validação estrita: apenas arquivos .csv são aceitos
  const validateAndProcessFile = async (file: File) => {
    const isCsv = file.name.toLowerCase().endsWith('.csv')
    if (!isCsv) {
      const shortName = truncateFileName(file.name, 24)
      const msg = `O arquivo "${shortName}" foi bloqueado. Envie apenas arquivos .csv.`
      setRejectionError({
        title: 'Formato não permitido',
        message: msg,
        fullFileName: file.name,
      })
      setUploadedFile(null)
      if (fileInputRef.current) {
        fileInputRef.current.value = ''
      }
      toast.error(msg, { duration: 5000, id: 'csv-validation-error' })
      return
    }

    // Validação estrita do cabeçalho do arquivo CSV antes de permitir o upload
    try {
      const textChunk = await file.slice(0, 4096).text()
      const lines = textChunk.split(/\r\n|\n|\r/)
      const firstLine = lines[0] ? lines[0].replace(/^\uFEFF/, '').trim() : ''

      if (!firstLine) {
        const msg = 'O arquivo CSV enviado está vazio. Baixe o modelo oficial.'
        setRejectionError({
          title: 'Arquivo vazio',
          message: msg,
          fullFileName: file.name,
        })
        setUploadedFile(null)
        if (fileInputRef.current) fileInputRef.current.value = ''
        toast.error(msg, { duration: 5000, id: 'csv-header-error' })
        return
      }

      const delimiter = firstLine.includes(';')
        ? ';'
        : firstLine.includes('\t')
          ? '\t'
          : ','
      const rawHeaders = firstLine.split(delimiter).map((h) =>
        h
          .replace(/^["']|["']$/g, '')
          .trim()
          .normalize('NFD')
          .replace(/[\u0300-\u036f]/g, '')
          .toLowerCase(),
      )

      const hasCodeColumn = rawHeaders.some(
        (h) =>
          h === 'codigo' ||
          h === 'cod' ||
          h.includes('codigo') ||
          h.includes('patrimonio') ||
          h.includes('tombamento') ||
          h.includes('etiqueta'),
      )

      if (!hasCodeColumn) {
        const msg =
          'Coluna "Código" não encontrada no cabeçalho. Baixe o modelo oficial.'
        setRejectionError({
          title: 'Estrutura inválida',
          message: msg,
          fullFileName: file.name,
        })
        setUploadedFile(null)
        if (fileInputRef.current) fileInputRef.current.value = ''
        toast.error(msg, { duration: 5000, id: 'csv-header-error' })
        return
      }
    } catch (err) {
      console.error('Erro ao verificar cabeçalho do CSV:', err)
    }

    setRejectionError(null)
    setUploadedFile(file)
    if (file) {
      onValidateFile?.(file)
    }
  }

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault()
    setIsDragOver(false)

    const files = e.dataTransfer.files
    if (files.length > 0) {
      validateAndProcessFile(files[0])
    }
  }

  const handleButtonClick = () => {
    fileInputRef.current?.click()
  }

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) {
      validateAndProcessFile(file)
    }
  }

  const resetUpload = () => {
    setUploadedFile(null)
    setRejectionError(null)
    if (fileInputRef.current) {
      fileInputRef.current.value = ''
    }
  }

  return (
    <div className="bg-white rounded-2xl shadow-md p-6 border border-slate-200/80 w-full h-full min-h-[290px] flex flex-col justify-between">
      {/* Topo do Card com Título e Tag de Formatos */}
      <div className="flex items-center justify-between border-b border-slate-100 pb-3">
        <div className="flex items-center gap-2.5">
          <div className="p-2 rounded-xl bg-blue-50 text-blue-500 shrink-0">
            <TbFileUpload className="w-5 h-5" />
          </div>
          <div>
            <h2 className="font-['Linden_Hill',serif] text-xl sm:text-2xl font-bold text-slate-800">
              Importação
            </h2>
          </div>
        </div>

        <span className="text-[11px] font-bold px-2.5 py-1 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200/80">
          Apenas CSV
        </span>
      </div>

      {/* Alerta Visual de Rejeição de Formato (caso tente enviar .xlsx ou outros) */}
      {rejectionError && (
        <div className="my-2 p-3 bg-red-50 border border-red-200 rounded-xl flex items-start gap-2.5 text-red-800 text-xs animate-in fade-in duration-200">
          <TbAlertCircle className="w-4 h-4 text-red-600 shrink-0 mt-0.5" />
          <div className="flex-1 min-w-0 leading-snug">
            <strong className="font-bold block text-red-900 mb-0.5">
              {rejectionError.title}
            </strong>
            <p
              className="break-words text-red-700"
              title={rejectionError.fullFileName || undefined}
            >
              {rejectionError.message}
            </p>
          </div>
          <button
            type="button"
            onClick={() => setRejectionError(null)}
            className="text-red-400 hover:text-red-700 p-0.5 cursor-pointer shrink-0 transition-colors"
            title="Fechar aviso"
          >
            <TbX className="w-4 h-4" />
          </button>
        </div>
      )}

      {/* Renderização condicional da área central: Dropzone ou Confirmação do Arquivo */}
      {!uploadedFile ? (
        <div
          className={`my-auto p-4 text-center rounded-xl border-2 border-dashed transition-all duration-200 cursor-pointer flex flex-col items-center justify-center gap-2 ${
            isDragOver
              ? 'border-blue-400 bg-blue-50'
              : 'border-slate-200 bg-slate-50/50 hover:border-slate-300'
          }`}
          onDragOver={handleDragOver}
          onDragLeave={handleDragLeave}
          onDrop={handleDrop}
          onClick={handleButtonClick}
        >
          <TbFileUpload className="w-8 h-8 sm:w-10 sm:h-10 text-slate-400" />
          <p className="text-slate-600 text-xs max-w-xs leading-relaxed font-medium">
            Arraste aqui a planilha{' '}
            <span className="font-semibold text-slate-800">.csv</span> ou clique
            para selecionar.
          </p>

          {/* Única opção de acesso para abrir o modal de regras/colunas */}
          <div className="flex items-center justify-center pt-1 border-t border-slate-200/60 text-[11px] text-slate-500 mt-1 w-full">
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation()
                setShowTemplateModal(true)
              }}
              className="text-blue-600 hover:text-blue-800 hover:underline flex items-center gap-1 font-medium cursor-pointer py-0.5 px-2 rounded-lg hover:bg-blue-50 transition-colors"
            >
              <TbInfoCircle className="w-3.5 h-3.5" />
              Colunas esperadas
            </button>
          </div>
        </div>
      ) : (
        <div className="relative my-auto p-4 text-center rounded-xl bg-slate-50 border border-slate-200 flex flex-col items-center justify-center gap-2">
          {/* Botão 'X' para remover o arquivo selecionado e escolher outro */}
          <button
            type="button"
            onClick={resetUpload}
            className="absolute top-2.5 right-2.5 p-1 text-slate-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors cursor-pointer"
            title="Remover arquivo selecionado"
            aria-label="Remover arquivo selecionado"
          >
            <TbX className="w-4 h-4" />
          </button>

          <TbFileUpload
            className={`w-8 h-8 ${
              validationErrors && validationErrors.length > 0
                ? 'text-red-500'
                : 'text-emerald-500'
            }`}
          />
          <h3 className="text-xs font-semibold text-slate-900 truncate max-w-[220px]">
            {uploadedFile.name}
          </h3>

          {/* Status da validação */}
          {validationErrors && validationErrors.length > 0 ? (
            <span className="text-[11px] font-bold text-red-600 bg-red-50 px-2 py-0.5 rounded-full border border-red-200">
              {validationErrors.length}{' '}
              {validationErrors.length === 1
                ? 'erro encontrado'
                : 'erros encontrados'}
            </span>
          ) : (
            <span className="text-[11px] font-medium text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded-full border border-emerald-200">
              Arquivo .csv pronto para envio
            </span>
          )}

          <div className="flex gap-2 justify-center mt-1">
            <button
              type="button"
              onClick={resetUpload}
              className="flex items-center gap-1 px-3 py-1 bg-slate-200 hover:bg-slate-300 text-slate-700 rounded-lg text-xs font-medium transition-colors cursor-pointer"
              title="Remover este arquivo e selecionar outro"
            >
              <TbX className="w-3.5 h-3.5" />
              Remover
            </button>
            <button
              type="button"
              className={`px-3 py-1 rounded-lg text-xs font-medium transition-colors cursor-pointer ${
                validationErrors && validationErrors.length > 0
                  ? 'bg-red-100 hover:bg-red-200 text-red-700'
                  : 'bg-blue-500 hover:bg-blue-600 text-white'
              }`}
              onClick={() => {
                if (validationErrors && validationErrors.length > 0) {
                  openModal()
                } else {
                  onFileSelect(uploadedFile)
                  setUploadedFile(null)
                }
              }}
            >
              {validationErrors && validationErrors.length > 0
                ? 'Ver erros'
                : 'Processar'}
            </button>
          </div>
        </div>
      )}

      {/* Botão de Ação Padronizado */}
      <div className="flex justify-center pt-3 border-t border-slate-100">
        <Button
          text="Importar Planilha CSV"
          variant={3}
          type="button"
          onClick={handleButtonClick}
          width="w-full sm:w-64"
        />
      </div>

      {/* Input oculto acionado programaticamente - Aceita estritamente CSV */}
      <input
        ref={fileInputRef}
        type="file"
        className="hidden"
        onChange={handleFileSelect}
        accept=".csv"
      />

      {/* Modal de Erros de Validação da Planilha */}
      {showErrorModal && (
        <ModalErrorsSheet
          onClose={closeModal}
          errors={validationErrors}
          title={'Erros encontrados na planilha'}
        />
      )}

      {/* Modal Explicativo de Colunas e Modelo CSV */}
      <CsvTemplateModal
        isOpen={showTemplateModal}
        onClose={() => setShowTemplateModal(false)}
      />
    </div>
  )
}
