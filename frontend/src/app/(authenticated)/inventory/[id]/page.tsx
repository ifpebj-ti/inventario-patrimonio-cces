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
  updateItemRequest,
} from '@/services/item'
import { toast } from 'react-hot-toast'
import { SendEmailSheetModal } from '@/components/organisms/sendEmailSheetModal'
import { ConfirmationModal } from '@/components/organisms/modalConfirmation'
import { ScannerCard } from '@/components/molecules/scannerCard'
import { BarcodeQrScannerModal } from '@/components/organisms/barcodeQrScannerModal'
import { ItemDetailDrawer } from '@/components/organisms/itemDetailDrawer'

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

  // Estados para controle do Scanner via Câmera e Drawer de Edição de Item
  const [isScannerOpen, setIsScannerOpen] = useState(false)
  const [isDrawerOpen, setIsDrawerOpen] = useState(false)
  const [scannedItem, setScannedItem] = useState<Item | null>(null)

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

  const handleOpenDeleteItemModal = (item: Item | InventoryResponse) => {
    setItemToDelete(item as Item)
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

  // Callback acionado quando a câmera lê com sucesso um código de barras ou QR Code
  const handleScanSuccess = async (decodedCode: string) => {
    const cleanCode = decodedCode.trim().toLowerCase()

    // 1. Busca o item correspondente na lista do inventário atual
    const foundItem = itemData.find(
      (item) =>
        item.code?.trim().toLowerCase() === cleanCode ||
        String(item.id).toLowerCase() === cleanCode,
    )

    if (foundItem) {
      // 2. Muda o status do item instantaneamente para verificado (código verde)
      const verifiedItem: Item = {
        ...foundItem,
        isValid: true,
      }

      // 3. Atualiza os contadores em tempo real e a lista (cor do código fica verde)
      setItemData((currentItems) =>
        currentItems.map((item) =>
          item.id === foundItem.id ? verifiedItem : item,
        ),
      )

      // 4. Persiste o status verificado no backend
      try {
        await updateItemRequest({
          ...verifiedItem,
          price: Number(verifiedItem.price) || 0,
        })
      } catch (err) {
        console.error('Erro ao persistir status verificado do item:', err)
      }

      // 5. Abre a Drawer com os dados e trava o campo de carga (somente leitura)
      setScannedItem(verifiedItem)
      setIsDrawerOpen(true)
      toast.success(`Patrimônio "${foundItem.code}" localizado e verificado!`)
    } else {
      toast.error(`O código "${decodedCode}" não pertence a este inventário.`, {
        duration: 4000,
      })
    }
  }

  // Atualiza imediatamente o item modificado na lista local
  const handleItemUpdated = (updatedItem: Item) => {
    setItemData((currentItems) =>
      currentItems.map((item) =>
        item.id === updatedItem.id ? updatedItem : item,
      ),
    )
  }

  return (
    <div className="w-full max-w-[90rem] mx-auto px-4 sm:px-6 lg:px-8 py-4 sm:py-6 flex flex-col gap-6 min-h-screen">
      {/* Topo da Página: Três Cards com mesma altura e padrão visual (Design System do Inventarium) */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5 w-full items-stretch">
        {/* Card 1: Estatísticas (Contadores dinâmicos + texto de orientação) */}
        <div className="h-full">
          <InventoryItemsStatus content={itemData} />
        </div>

        {/* Card 2: Escaneamento (Código de Barras e QR Code via Câmera) */}
        <div className="h-full">
          <ScannerCard onOpenScanner={() => setIsScannerOpen(true)} />
        </div>

        {/* Card 3: Importação de Planilha (Upload e Drag-and-drop) */}
        <div className="h-full">
          <FileUploadComponent
            onFileSelect={handleFileUpload}
            onValidateFile={handleSpreadsheetValidation}
            validationErrors={validationErrors}
          />
        </div>
      </div>

      {/* Tabela Híbrida (Cards no mobile / Tabela no desktop) */}
      <div className="w-full">
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
        />
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

      {/* Modal do Scanner de Câmera Web */}
      <BarcodeQrScannerModal
        isOpen={isScannerOpen}
        onClose={() => setIsScannerOpen(false)}
        onScanSuccess={handleScanSuccess}
      />

      {/* Drawer Lateral de Auditoria e Edição do Item */}
      <ItemDetailDrawer
        isOpen={isDrawerOpen}
        onClose={() => setIsDrawerOpen(false)}
        item={scannedItem}
        onItemUpdated={handleItemUpdated}
        isResponsibleLocked={true}
        onScanNext={() => {
          setIsDrawerOpen(false)
          setIsScannerOpen(true)
        }}
      />
    </div>
  )
}
