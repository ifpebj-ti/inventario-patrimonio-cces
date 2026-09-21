'use client'

import { errorSheet } from '@/app/(authenticated)/inventory/[id]/page'
import { Button } from '@/components/atoms/button'
import { ModalErrorsSheet } from '@/components/organisms/modalErrors'
import React, { useRef, useState } from 'react'
import { TbFileUpload } from 'react-icons/tb'

// Define as propriedades que o componente de upload de arquivo espera receber.
type FileUploadComponentProps = {
  onFileSelect: (file: File) => void // Função chamada para o processamento final do arquivo.
  onValidateFile?: (file: File) => void // Função opcional para validar o arquivo antes do processamento.
  validationErrors: errorSheet[] | null // Array de erros de validação recebido do componente pai.
}

// Componente Card 3: Importação de Planilha via Drag-and-Drop ou Seleção
export const FileUploadComponent = ({
  onFileSelect,
  onValidateFile,
  validationErrors,
}: FileUploadComponentProps) => {
  const [isDragOver, setIsDragOver] = useState(false)
  const [uploadedFile, setUploadedFile] = useState<File | null>(null)
  const [showErrorModal, setShowErrorModal] = useState(false)

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

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault()
    setIsDragOver(false)

    const files = e.dataTransfer.files
    if (files.length > 0) {
      handleFileUpload(files[0])
    }
  }

  const handleFileUpload = (file: File) => {
    setUploadedFile(file)
    if (file) {
      onValidateFile?.(file)
    }
  }

  const handleButtonClick = () => {
    fileInputRef.current?.click()
  }

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) {
      handleFileUpload(file)
    }
  }

  const resetUpload = () => {
    setUploadedFile(null)
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
            <p className="text-[11px] text-slate-400">
              Planilhas .xlsx, .xls ou .csv
            </p>
          </div>
        </div>
        <span className="text-[11px] font-semibold px-2 py-0.5 rounded-full bg-slate-100 text-slate-600">
          Excel / CSV
        </span>
      </div>

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
          <p className="text-slate-500 text-xs max-w-xs leading-relaxed">
            Arraste aqui a planilha ou clique para selecionar do computador
          </p>
        </div>
      ) : (
        <div className="my-auto p-3 text-center rounded-xl bg-slate-50 border border-slate-200 flex flex-col items-center justify-center gap-2">
          <TbFileUpload
            className={`w-8 h-8 ${
              validationErrors && validationErrors.length > 0
                ? 'text-red-500'
                : 'text-emerald-500'
            }`}
          />
          <h3 className="text-xs font-semibold text-slate-900 truncate max-w-[240px]">
            {uploadedFile.name}
          </h3>
          <div className="flex gap-2 justify-center">
            <button
              type="button"
              onClick={resetUpload}
              className="px-3 py-1 bg-slate-200 hover:bg-slate-300 text-slate-700 rounded-lg text-xs font-medium transition-colors cursor-pointer"
            >
              Trocar
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
          text="Importar Planilha"
          variant={3}
          type="button"
          onClick={handleButtonClick}
          width="w-full sm:w-64"
        />
      </div>

      {/* Input oculto acionado programaticamente */}
      <input
        ref={fileInputRef}
        type="file"
        className="hidden"
        onChange={handleFileSelect}
        accept=".xlsx,.xls,.csv"
      />

      {/* Modal de Erros */}
      {showErrorModal && (
        <ModalErrorsSheet
          onClose={closeModal}
          errors={validationErrors}
          title={'Erros encontrados na planilha'}
        />
      )}
    </div>
  )
}
