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

// Componente reutilizável para upload de arquivos, com suporte a arrastar e soltar (drag-and-drop).
export const FileUploadComponent = ({
  onFileSelect,
  onValidateFile,
  validationErrors,
}: FileUploadComponentProps) => {
  // Estado para controlar o estilo visual quando um arquivo está sendo arrastado sobre a área.
  const [isDragOver, setIsDragOver] = useState(false)
  // Estado para armazenar o arquivo que o usuário selecionou.
  const [uploadedFile, setUploadedFile] = useState<File | null>(null)
  // Estado para controlar a visibilidade do modal de erros.
  const [showErrorModal, setShowErrorModal] = useState(false)

  // Funções para abrir e fechar o modal de erros.
  const openModal = () => setShowErrorModal(true)
  const closeModal = () => setShowErrorModal(false)
  // 'useRef' para criar uma referência ao input de arquivo oculto, permitindo acioná-lo via botão.
  const fileInputRef = useRef<HTMLInputElement>(null)

  // Manipulador para o evento 'onDragOver'. Previne o comportamento padrão do navegador.
  const handleDragOver = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault()
    setIsDragOver(true)
  }

  // Manipulador para o evento 'onDragLeave'. Reseta o estado visual.
  const handleDragLeave = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault()
    setIsDragOver(false)
  }

  // Manipulador para o evento 'onDrop'. Lida com o arquivo que foi solto na área.
  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault()
    setIsDragOver(false)

    const files = e.dataTransfer.files
    if (files.length > 0) {
      handleFileUpload(files[0])
    }
  }

  // Função central que lida com o arquivo selecionado, seja por clique ou por arrastar.
  const handleFileUpload = (file: File) => {
    setUploadedFile(file)
    console.log('Arquivo selecionado:', file)

    // Se uma função de validação foi fornecida, ela é chamada.
    if (file) {
      onValidateFile?.(file)
    }
  }

  // Aciona o clique no input de arquivo oculto quando o botão "Importar" é clicado.
  const handleButtonClick = () => {
    fileInputRef.current?.click()
  }

  // Lida com a seleção de arquivo através da janela de diálogo do sistema.
  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) {
      handleFileUpload(file)
    }
  }

  // Reseta o estado do componente para permitir um novo upload.
  const resetUpload = () => {
    setUploadedFile(null)
    if (fileInputRef.current) {
      fileInputRef.current.value = ''
    }
  }

  return (
    <div className="flex items-center justify-center h-[80%]">
      <div className="w-full max-w-md h-full">
        {/* Renderização condicional: mostra a área de upload ou a tela de confirmação. */}
        {!uploadedFile ? (
          // Estado inicial: área para arrastar ou clicar para selecionar um arquivo.
          <div
            className={`
                relative rounded-lg shadow-md p-4 text-center transition-all duration-200 h-full
                ${
                  isDragOver
                    ? 'border-blue-400 bg-blue-50'
                    : 'border-gray-200 bg-white hover:border-gray-400'
                }
              `}
            onDragOver={handleDragOver}
            onDragLeave={handleDragLeave}
            onDrop={handleDrop}
          >
            <div className="mb-4 flex justify-center">
              <div className="w-16 h-20 rounded-lg relative">
                <TbFileUpload className="w-24 h-24 text-gray-400 absolute top-1/2 left-1/2 transform -translate-x-1/2 -translate-y-1/2" />
              </div>
            </div>

            <p className="text-gray-500 mb-6 w-2/3 mx-auto">
              Arraste aqui a planilha ou insira através do botão
            </p>

            <div className="flex justify-center">
              <Button
                text="Importar Planilha"
                variant={3}
                type="button"
                onClick={handleButtonClick}
              />
            </div>

            {/* O input de arquivo real, que fica oculto mas é acionado programaticamente. */}
            <input
              ref={fileInputRef}
              type="file"
              className="hidden"
              onChange={handleFileSelect}
              accept=".xlsx,.xls,.csv"
            />
          </div>
        ) : (
          // Estado após o upload: mostra o status do arquivo selecionado.
          <div className="bg-white rounded-lg p-6 text-center h-full shadow-md">
            <div className="mb-4">
              <TbFileUpload
                className={`w-12 h-12 mx-auto
                ${validationErrors && validationErrors.length > 0 ? 'text-red-500' : 'text-green-500'} `}
              />
            </div>

            <h3 className="text-lg font-medium text-gray-900 mb-2">
              {validationErrors && validationErrors.length > 0
                ? 'Arquivo contém dados inválidos'
                : 'Arquivo carregado com sucesso!'}
            </h3>

            <p className="text-gray-600 mb-4">{uploadedFile.name}</p>
            <div className="flex gap-3 justify-center">
              <button
                onClick={resetUpload}
                className="px-4 py-2 bg-gray-200 hover:bg-gray-300 text-gray-700 rounded-lg transition-colors duration-200 cursor-pointer"
              >
                Escolher outro arquivo
              </button>
              {/* Botão principal, que muda de função dependendo se há erros de validação. */}
              <button
                className={`px-4 py-2 rounded-lg transition-colors duration-200 cursor-pointer ${
                  validationErrors && validationErrors.length > 0
                    ? 'bg-red-100 hover:bg-red-200 text-red-700'
                    : 'bg-blue-500 hover:bg-blue-600 text-white'
                }`}
                onClick={() => {
                  if (validationErrors && validationErrors.length > 0) {
                    // Se houver erros, abre o modal de erros.
                    openModal()
                  } else {
                    // Se não houver erros, chama a função para processar o arquivo.
                    onFileSelect(uploadedFile)
                    setUploadedFile(null)
                  }
                }}
              >
                {validationErrors && validationErrors.length > 0
                  ? 'Erros encontrados'
                  : 'Processar arquivo'}
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Renderiza o modal de erros apenas se 'showErrorModal' for verdadeiro. */}
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
