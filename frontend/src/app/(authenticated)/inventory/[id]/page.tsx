'use client'
import { useParams, useRouter } from 'next/navigation'
import { Table } from '@/components/organisms/table'
import { useCallback, useEffect, useRef, useState } from 'react'
import {
  getInventoryRequest,
  getInventoryItemsRequest,
  InventoryResponse,
  deleteItemFromInventory,
  clearInventoryItemsRequest,
} from '@/services/inventory'
import { FileUploadComponent } from '@/components/molecules/fileUploadComponet'
import { InventoryItemsStatus } from '@/components/molecules/inventoryItemsStatus'
import { InventoryCompletion } from '@/components/organisms/inventoryCompletion'
import { Item } from '@/commons/models/item'
import {
  generateItemsSheet,
  SendEmailSheetRequest,
  sendSheetByEmail,
  updateItemRequest,
} from '@/services/item'
import { toast } from 'sonner'
import { Spinner } from '@/components/ui/spinner'
import { SendEmailSheetModal } from '@/components/organisms/sendEmailSheetModal'
import { ConfirmationModal } from '@/components/organisms/modalConfirmation'
import { ScannerCard } from '@/components/molecules/scannerCard'
import { BarcodeQrScannerModal } from '@/components/organisms/barcodeQrScannerModal'
import { ItemDetailDrawer } from '@/components/organisms/itemDetailDrawer'
import { useSpreadsheetImport } from '@/hooks/useSpreadsheetImport'
import { useAdministrationAccess } from '@/contexts/AdministrationAccess'
import { getSpreadsheetFailure } from '@/commons/utils/spreadsheet'

export default function Inventory() {
  const { id } = useParams()
  const router = useRouter() // useRouter é usado para navegar entre páginas
  const [itemData, setItemData] = useState<Item[]>([])
  // Gestor/admin gerenciam o inventário; operador também escaneia e valida; consulta só lê.
  const { canManageInventories, canOperateItems } = useAdministrationAccess()
  // Só libera a importação depois de saber se o inventário já tem itens.
  const [itemsLoaded, setItemsLoaded] = useState(false)
  // Inventário finalizado não aceita novos escaneamentos nem importações.
  const [finalized, setFinalized] = useState(false)
  const [isEmailModalOpen, setIsEmailModalOpen] = useState(false)
  const [isDeleteItemModalOpen, setIsDeleteItemModalOpen] = useState(false)
  const [isClearAllModalOpen, setIsClearAllModalOpen] = useState(false)
  const [itemToDelete, setItemToDelete] = useState<Item | null>(null)

  // Estado para gerenciar linhas selecionadas
  const [selectedRows, setSelectedRows] = useState<number[]>([])
  const [isUploading, setIsUploading] = useState(false)
  const clearing = useRef(false)

  // Estados para controle do Scanner via Câmera e Drawer de Edição de Item
  const [isScannerOpen, setIsScannerOpen] = useState(false)
  const [isDrawerOpen, setIsDrawerOpen] = useState(false)
  const [scannedItem, setScannedItem] = useState<Item | null>(null)

  const fetchItemsByInventory = useCallback(async () => {
    try {
      const inventoryId = Number(id)
      const page = 0
      const pageSize = 2000

      setItemsLoaded(false)
      setItemData([])
      const response = await getInventoryItemsRequest(
        inventoryId,
        page,
        pageSize,
      )
      setItemData(response)
    } catch (error) {
      console.error('Erro ao carregar itens do inventário:', error)
    } finally {
      setItemsLoaded(true)
    }
  }, [id]) // useCallback é usado para memorizar a função e evitar recriações desnecessárias

  const handleCloseEmailModal = () => {
    setIsEmailModalOpen(false)
  }

  const handleOpenEmailModal = () => {
    setIsEmailModalOpen(true)
  }

  const handleSendEmail = async (formData: SendEmailSheetRequest) => {
    await sendSheetByEmail(Number(id), formData)
    toast.success('Solicitação de envio recebida pelo Inventarium.')
  }

  const spreadsheet = useSpreadsheetImport(Number(id), fetchItemsByInventory)

  // Regras de bloqueio: finalizado não escaneia nem importa; e só existe uma
  // importação por vez (para importar outra planilha, limpe os itens antes).
  const hasItems = itemData.length > 0
  const importBlocked =
    !canManageInventories || finalized || hasItems || !itemsLoaded
  const importBlockedMessage = !canManageInventories
    ? 'Seu perfil não permite importar planilhas.'
    : finalized
      ? 'Inventário finalizado: não é possível importar novas planilhas.'
      : hasItems
        ? 'Este inventário já tem itens importados. Para importar outra planilha, exclua primeiro todos os itens do inventário.'
        : undefined
  const scanBlocked = finalized || !canOperateItems
  const scanBlockedMessage = !canOperateItems
    ? 'Seu perfil é somente leitura: não é possível escanear itens.'
    : finalized
      ? 'Inventário finalizado: não é possível escanear itens.'
      : undefined

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
      const { inventory } = await getInventoryRequest(inventoryId)
      const sheetBlob = await generateItemsSheet(inventoryId)

      const url = window.URL.createObjectURL(sheetBlob)
      const link = document.createElement('a')
      link.href = url
      const filename =
        inventory.name
          .replace(/[<>:"/\\|?*]/g, '_')
          .trim()
          .replace(/[. ]+$/, '') || `inventario-${inventoryId}`
      link.setAttribute('download', `${filename}.xlsx`)
      document.body.appendChild(link)
      link.click() // ⬅️ inicia o download
      link.remove()
      window.URL.revokeObjectURL(url)
    } catch (error) {
      console.error('Erro ao gerar planilha do patrimonio:', error)
    }
  }

  const handleOpenClearInventoryModal = () => {
    if (spreadsheet.state.status === 'importing' || clearing.current) return
    if (!itemData.length) {
      toast.error('O inventário já está vazio.')
      return
    }
    setIsClearAllModalOpen(true)
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

  const handleConfirmClearInventory = async () => {
    if (clearing.current || spreadsheet.state.status === 'importing') return
    clearing.current = true
    const toastId = toast.loading('Removendo todos os itens do inventário...')
    try {
      setIsUploading(true)
      await clearInventoryItemsRequest(Number(id))
      setItemData([])
      setSelectedRows([])
      setIsClearAllModalOpen(false)
      spreadsheet.remove()
      toast.success('Todos os itens foram removidos do inventário!', {
        id: toastId,
      })
    } catch (error) {
      console.error('Erro ao limpar itens do inventário:', error)
      toast.error(getSpreadsheetFailure(error).message, {
        id: toastId,
      })
    } finally {
      clearing.current = false
      setIsUploading(false)
    }
  }

  // Atualiza imediatamente o item modificado na lista local
  const handleItemUpdated = useCallback((updatedItem: Item) => {
    setItemData((currentItems) =>
      currentItems.map((item) =>
        item.id === updatedItem.id
          ? { ...item, ...updatedItem, isValid: updatedItem.isValid ?? true }
          : item,
      ),
    )
  }, [])

  const lastScannedRef = useRef<{ code: string; time: number }>({
    code: '',
    time: 0,
  })

  // Callback acionado quando a câmera lê com sucesso um código de barras ou QR Code
  const handleScanSuccess = useCallback(
    async (decodedCode: string) => {
      const rawCode = decodedCode.replace(/[\r\n\t]/g, '').trim()
      if (!rawCode || scanBlocked) return

      // Debounce de 1.5s para evitar avisos repetidos caso ocorram disparos simultâneos
      const now = Date.now()
      if (
        lastScannedRef.current.code === rawCode &&
        now - lastScannedRef.current.time < 1500
      ) {
        return
      }
      lastScannedRef.current = { code: rawCode, time: now }

      const cleanCode = rawCode.toLowerCase()
      const digitsOnly = cleanCode.replace(/\D/g, '')
      const strippedZeros = cleanCode.replace(/^0+/, '')

      // 1. Busca flexível do item correspondente na lista do inventário atual
      const foundItem = itemData.find((item) => {
        const itemCode = (item.code || '').trim().toLowerCase()
        const itemId = String(item.id)
        const itemDigits = itemCode.replace(/\D/g, '')
        const itemStrippedZeros = itemCode.replace(/^0+/, '')
        const itemQr = (item.qrCode || item.qr_code || '').trim().toLowerCase()

        // 1.1 Casamento exato por código ou ID
        if (itemCode === cleanCode || itemId === cleanCode) return true

        // 1.2 Casamento por QR Code UUID
        if (itemQr && itemQr === cleanCode) return true

        // 1.3 Casamento sem zeros à esquerda (ex: etiqueta com "0052731" e sistema com "52731")
        if (
          strippedZeros &&
          itemStrippedZeros &&
          strippedZeros === itemStrippedZeros
        ) {
          return true
        }

        // 1.4 Casamento numérico direto (se ambos tiverem dígitos)
        if (digitsOnly && itemDigits && digitsOnly === itemDigits) return true

        // 1.5 Casamento caso o scanner retorne URL
        if (
          cleanCode.endsWith(`/${itemCode}`) ||
          cleanCode.endsWith(`/${itemId}`)
        ) {
          return true
        }

        return false
      })

      if (foundItem) {
        // 2. Muda o status do item instantaneamente para verificado (código verde)
        const verifiedItem: Item = {
          ...foundItem,
          isValid: true,
        }

        // 3. Atualiza os contadores em tempo real e a lista imediatamente na UI
        setItemData((currentItems) =>
          currentItems.map((item) =>
            item.id === foundItem.id ? { ...item, isValid: true } : item,
          ),
        )

        // 4. Persiste o status verificado no backend imediatamente
        try {
          const saved = await updateItemRequest({
            ...verifiedItem,
            price: verifiedItem.price,
          })
          if (saved) {
            handleItemUpdated(saved)
          }
        } catch (err) {
          console.error('Erro ao persistir status verificado do item:', err)
          toast.error('Erro ao salvar verificação no servidor.', {
            id: 'scan-error-toast',
          })
        }

        // 5. Abre a Drawer com os dados e trava o campo de carga (somente leitura)
        setScannedItem(verifiedItem)
        setIsDrawerOpen(true)
        toast.success(`Patrimônio "${foundItem.code}" verificado!`, {
          id: 'scan-success-toast',
        })
      } else {
        toast.error(`O código "${rawCode}" não pertence a este inventário.`, {
          id: 'scan-error-toast',
          duration: 4000,
        })
      }
    },
    [itemData, handleItemUpdated, scanBlocked],
  )

  return (
    <div className="page-shell flex min-h-screen flex-col gap-6">
      <InventoryCompletion
        key={String(id)}
        inventoryId={Number(id)}
        disabled={isUploading || spreadsheet.state.status === 'importing'}
        onCompletedChange={setFinalized}
      />
      {/* Topo da Página: Três Cards com mesma altura e padrão visual (Design System do Inventarium) */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5 w-full items-stretch">
        {/* Card 1: Estatísticas (Contadores dinâmicos + texto de orientação) */}
        <div className="h-full">
          <InventoryItemsStatus content={itemData} />
        </div>

        {/* Card 2: Escaneamento (Código de Barras e QR Code via Câmera) */}
        <div className="h-full">
          <ScannerCard
            onOpenScanner={() => !scanBlocked && setIsScannerOpen(true)}
            disabled={scanBlocked}
            disabledMessage={scanBlockedMessage}
          />
        </div>

        {/* Card 3: Importação de Planilha (Upload e Drag-and-drop) */}
        <div className="h-full">
          <FileUploadComponent
            state={spreadsheet.state}
            onSelect={spreadsheet.select}
            onRemove={spreadsheet.remove}
            onProcess={spreadsheet.process}
            disabled={isUploading || importBlocked}
            blockedMessage={importBlockedMessage}
          />
        </div>
      </div>

      {/* Tabela Híbrida (Cards no mobile / Tabela no desktop) */}
      <div className="w-full">
        <Table
          header={[
            { key: 'code', headerText: 'Código' },
            { key: 'status', headerText: 'Status' },
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
          onClearInventory={handleOpenClearInventoryModal}
          disableClear={isUploading || spreadsheet.state.status === 'importing'}
          hideClear={!canManageInventories}
          onExportSheet={handleExportSheet}
          onSendEmailSheet={handleOpenEmailModal}
          onDeleteItem={
            canManageInventories ? handleOpenDeleteItemModal : undefined
          }
        />
      </div>

      {isUploading && (
        <div className="fixed inset-0 z-50 flex flex-col items-center justify-center gap-4 bg-background/60 backdrop-blur-sm">
          <Spinner className="size-12 text-primary" />
          <p className="text-lg text-muted-foreground">
            Carregando planilha...
          </p>
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

      {isClearAllModalOpen && (
        <ConfirmationModal
          isOpen={isClearAllModalOpen}
          onClose={() => setIsClearAllModalOpen(false)}
          onConfirm={handleConfirmClearInventory}
          title="Limpar Inventário"
          message="Tem certeza que deseja remover todos os itens e limpar este inventário? Esta ação é irreversível e excluirá todos os bens cadastrados."
          confirmButtonText="Sim, limpar inventário"
          cancelButtonText="Cancelar"
          busy={isUploading}
        />
      )}

      {/* Modal do Scanner de Câmera Web */}
      <BarcodeQrScannerModal
        isOpen={isScannerOpen && !scanBlocked}
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
          if (!scanBlocked) setIsScannerOpen(true)
        }}
      />
    </div>
  )
}
