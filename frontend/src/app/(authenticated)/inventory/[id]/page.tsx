'use client'
import { useParams, useRouter } from 'next/navigation'
import { Table } from '@/components/organisms/table'
import { useCallback, useEffect, useState } from 'react'
import {
  addItemsBySheet,
  getInventoryItemsRequest,
  validateSpreadsheetData,
  InventoryResponse,
  deleteItemFromInventory,
} from '@/services/inventory'
import { FileUploadComponent } from '@/components/molecules/fileUploadComponet'
import { InventoryItemsStatus } from '@/components/molecules/inventoryItemsStatus'
import { Item } from '@/commons/models/item'
import {
  generateItemsSheet,
  generateQRCodeAllLabelsPdf,
  generateQRCodeLabelsPdf,
  SendEmailSheetRequest,
  sendSheetByEmail,
} from '@/services/item'
import { toast } from 'react-hot-toast'
import { SendEmailSheetModal } from '@/components/organisms/sendEmailSheetModal'
import { ConfirmationModal } from '@/components/organisms/modalConfirmation'

export type errorSheet = {
  code: string
  line: string
  errors: string[]
}

export default function Inventory() {
  const { id } = useParams()
  const router = useRouter() // useRouter é usado para navegar entre páginas
  const [itemData, setItemData] = useState<Item[]>([])
  const [isEmailModalOpen, setIsEmailModalOpen] = useState(false)
  const [isDeleteItemModalOpen, setIsDeleteItemModalOpen] = useState(false)
  const [itemToDelete, setItemToDelete] = useState<Item | null>(null)

  // Estado para gerenciar linhas selecionadas
  const [selectedRows, setSelectedRows] = useState<number[]>([])
  const [isUploading, setIsUploading] = useState(false)
  const [validationErrors, setValidationErrors] = useState<errorSheet[] | null>(
    null,
  )

  const fetchItemsByInventory = useCallback(async () => {
    try {
      const inventoryId = Number(id)
      const page = 0
      const pageSize = 2000

      setItemData([])
      const response = await getInventoryItemsRequest(
        inventoryId,
        page,
        pageSize,
      )
      setItemData(response)
    } catch (error) {
      console.error('Erro ao carregar itens do inventário:', error)
    }
  }, [id]) // useCallback é usado para memorizar a função e evitar recriações desnecessárias

  const handleCloseEmailModal = () => {
    setIsEmailModalOpen(false)
  }

  const handleOpenEmailModal = () => {
    setIsEmailModalOpen(true)
  }

  const handleSendEmail = async (formData: SendEmailSheetRequest) => {
    const inventoryId = Number(id)

    try {
      await sendSheetByEmail(inventoryId, formData)
      toast.success('Email enviado com sucesso!')
      setIsEmailModalOpen(false)
    } catch (error) {
      toast.error('Erro ao enviar o email.')
      console.error(error)
    }
  }

  const handleSpreadsheetValidation = async (file: File) => {
    try {
      setIsUploading(true)
      setValidationErrors(null)

      const response = await validateSpreadsheetData(file)

      if (response.errors && response.errors.length > 0) {
        setValidationErrors(response.errors)
      } else {
        console.log('Arquivo válido')
      }
    } catch (error) {
      console.error('Erro ao enviar arquivo:', error)
    } finally {
      setIsUploading(false) // encerra o loading
    }
  }

  const handleFileUpload = async (file: File) => {
    try {
      setIsUploading(true) // começa o loading

      const formData = new FormData()
      formData.append('file', file)
      formData.append('inventoryId', String(Number(id)))

      const response = await addItemsBySheet(file, Number(id))

      console.log('Arquivo enviado com sucesso:', response)

      await fetchItemsByInventory()
    } catch (error) {
      console.error('Erro ao enviar arquivo:', error)
    } finally {
      setIsUploading(false) // encerra o loading
    }
  }

  useEffect(() => {
    fetchItemsByInventory()
  }, [fetchItemsByInventory])

  const handleRowDoubleClick = useCallback(
    (itemClicked: Item | InventoryResponse) => {
      if (
        'id' in itemClicked &&
        itemClicked.id !== undefined &&
        itemClicked.id !== null
      ) {
        router.push(`/inventory/${id}/item/${itemClicked.id}`)
        // fetchItemsByInventory()
      } else {
        console.error(
          'Item clicado não possui um ID válido para navegação:',
          itemClicked,
        )
      }
    },
    [id, router],
  ) // 'id' e 'router' são dependências para garantir que a função seja atualizada corretamente

  const handleExportSheet = async () => {
    const inventoryId = Number(id)

    try {
      const sheetBlob = await generateItemsSheet(inventoryId)

      const url = window.URL.createObjectURL(sheetBlob)
      const link = document.createElement('a')
      link.href = url
      link.setAttribute('download', 'patrimonio.xlsx')
      document.body.appendChild(link)
      link.click() // ⬅️ inicia o download
      link.remove()
      window.URL.revokeObjectURL(url)
    } catch (error) {
      console.error('Erro ao gerar planilha do patrimonio:', error)
    }
  }

  const handleExportSelected = async () => {
    if (!selectedRows.length) {
      console.warn('Nenhuma linha selecionada.')
      toast.error('Nenhum item selecionado.')
      return
    }

    const selectedItems = itemData.filter((item) =>
      selectedRows.includes(item.id),
    )

    const selectedItemsId = selectedItems.map((item) => item.id)

    console.log('Códigos selecionados:', selectedItemsId)

    try {
      const pdfBlob = await generateQRCodeLabelsPdf(selectedItemsId)

      const url = window.URL.createObjectURL(pdfBlob)
      const link = document.createElement('a')
      link.href = url
      link.setAttribute('download', 'etiquetas.pdf')
      document.body.appendChild(link)
      link.click() // ⬅️ inicia o download
      link.remove()
      window.URL.revokeObjectURL(url)
    } catch (error) {
      console.error('Erro ao gerar PDF:', error)
    }
  }

  const handleExportAll = async () => {
    const inventoryId = Number(id)

    try {
      const pdfBlob = await generateQRCodeAllLabelsPdf(inventoryId)

      const url = window.URL.createObjectURL(pdfBlob)
      const link = document.createElement('a')
      link.href = url
      link.setAttribute('download', 'etiquetas.pdf')
      document.body.appendChild(link)
      link.click() // ⬅️ inicia o download
      link.remove()
      window.URL.revokeObjectURL(url)
    } catch (error) {
      console.error('Erro ao gerar PDF:', error)
    }
  }

  const handleOpenDeleteItemModal = (item: Item) => {
    setItemToDelete(item)
    setIsDeleteItemModalOpen(true)
  }

  const handleCloseDeleteItemModal = () => {
    setIsDeleteItemModalOpen(false)
    setItemToDelete(null)
  }

  const handleConfirmDelete = async () => {
    if (!itemToDelete) return

    try {
      await deleteItemFromInventory(itemToDelete.id, Number(id))
      setItemData((currentItems) =>
        currentItems.filter((item) => item.id !== itemToDelete.id),
      )
      toast.success(`Item "${itemToDelete.code}" deletado com sucesso!`)
    } catch (error) {
      toast.error('Falha ao deletar o item.')
      console.error(error)
    } finally {
      handleCloseDeleteItemModal()
    }
  }

  return (
    <div className="overflow-x-hidden flex flex-col justify-center items-center">
      <div className="flex flex-col items-center min-h-screen pb-4">
        <div className="flex items-center gap-4 mt-4 h-80">
          <InventoryItemsStatus content={itemData}></InventoryItemsStatus>

          <FileUploadComponent
            onFileSelect={handleFileUpload}
            onValidateFile={handleSpreadsheetValidation}
            validationErrors={validationErrors}
          ></FileUploadComponent>
        </div>
        <Table
          header={[
            { key: 'code', headerText: 'Código' },
            { key: 'description', headerText: 'Descrição' },
            { key: 'responsible', headerText: 'Carga' },
            { key: 'price', headerText: 'Valor' },
            { key: 'locale', headerText: 'Sala' },
          ]}
          content={itemData}
          selectable={true}
          selectedRows={selectedRows}
          onRowSelect={setSelectedRows}
          onRowDoubleClick={handleRowDoubleClick}
          showExportButtons={true}
          onExportSelected={handleExportSelected}
          onExportAll={handleExportAll}
          onExportSheet={handleExportSheet}
          onSendEmailSheet={handleOpenEmailModal}
          onDeleteItem={handleOpenDeleteItemModal}
        ></Table>
      </div>

      {isUploading && (
        <div className="fixed inset-0 bg-opacity-20 backdrop-blur-sm flex flex-col items-center justify-center z-50">
          <div className="animate-spin rounded-full h-12 w-12 border-t-4 border-gray-500 border-solid mb-4"></div>
          <p className="text-gray-500 text-lg">Carregando planilha...</p>
        </div>
      )}

      {/* newInventoryModal - só renderiza quando isModalOpen for true */}
      {isEmailModalOpen && (
        <SendEmailSheetModal
          onClose={handleCloseEmailModal}
          onSendEmail={handleSendEmail}
        />
      )}

      {isDeleteItemModalOpen && (
        <ConfirmationModal
          isOpen={isDeleteItemModalOpen}
          onClose={handleCloseDeleteItemModal}
          onConfirm={handleConfirmDelete}
          title="Confirmar Deleção"
          message={`Você tem certeza que deseja deletar o item "${itemToDelete?.code}"? Esta ação não pode ser desfeita.`}
          confirmButtonText="Sim, deletar"
        />
      )}
    </div>
  )
}
