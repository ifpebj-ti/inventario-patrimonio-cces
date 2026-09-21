'use client'

import { Table } from '@/components/organisms/table'
import { useCallback, useState } from 'react'
import {
  deleteInventoryRequest,
  InventoryResponse,
  updateInventoryRequest,
  UpdateInventory,
} from '@/services/inventory'
import { Button } from '@/components/atoms/button'
import { useRouter } from 'next/navigation'
import { NewInventoryModal } from '@/components/organisms/newInventoryModal'
import { ConfirmationModal } from '@/components/organisms/modalConfirmation'
import toast from 'react-hot-toast'
import { EditInventoryModal } from '@/components/organisms/editInventoryModal'
import { useInventory } from '@/contexts/InventoryContext'
import { Item } from '@/commons/models/item'

export default function Dashboard() {
  const { inventories: inventoryData, refreshInventories } = useInventory()
  const [isNewInventoryModalOpen, setIsNewInventoryModalOpen] = useState(false)
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false)
  const [inventoryToDelete, setInventoryToDelete] =
    useState<InventoryResponse | null>(null)
  const router = useRouter()
  const [isEditModalOpen, setIsEditModalOpen] = useState(false)
  const [inventoryToEdit, setInventoryToEdit] =
    useState<InventoryResponse | null>(null)

  const openNewInventoryModal = () => setIsNewInventoryModalOpen(true)
  const closeNewInventoryModal = () => setIsNewInventoryModalOpen(false)

  const handleInventoryCreated = useCallback(async () => {
    await refreshInventories()
    closeNewInventoryModal()
  }, [refreshInventories])

  const handleRowDoubleClick = (item: InventoryResponse | Item) => {
    router.push(`/inventory/${item.id}`)
  }

  const handleOpenDeleteModal = (item: InventoryResponse | Item) => {
    setInventoryToDelete(item as InventoryResponse)
    setIsDeleteModalOpen(true)
  }

  const handleCloseDeleteModal = () => {
    setIsDeleteModalOpen(false)
    setInventoryToDelete(null)
  }

  const handleConfirmDelete = async () => {
    if (!inventoryToDelete) return

    try {
      await deleteInventoryRequest(inventoryToDelete.id)
      await refreshInventories()
      toast.success(
        `Inventário "${inventoryToDelete.name}" deletado com sucesso!`,
      )
    } catch (error) {
      toast.error('Falha ao deletar o inventário.')
      console.error(error)
    } finally {
      handleCloseDeleteModal()
    }
  }

  const handleOpenEditModal = (inventory: InventoryResponse) => {
    setInventoryToEdit(inventory)
    setIsEditModalOpen(true)
  }

  const handleCloseEditModal = () => {
    setIsEditModalOpen(false)
    setInventoryToEdit(null)
  }

  const handleConfirmEdit = async (data: {
    name: string
    description?: string
  }) => {
    if (!inventoryToEdit) return
    try {
      const updatedInventory = await updateInventoryRequest(
        {
          name: data.name,
          description: data.description || '',
        },
        inventoryToEdit.id,
      )
      await refreshInventories()
      toast.success(
        `Inventário "${updatedInventory.name}" atualizado com sucesso!`,
      )
    } catch (error) {
      toast.error('Falha ao atualizar o inventário.')
      console.error(error)
    } finally {
      handleCloseEditModal()
    }
  }

  return (
    <div className="w-full max-w-[90rem] mx-auto px-4 sm:px-6 lg:px-8 py-4 sm:py-6 flex flex-col gap-4 min-h-screen">
      {/* Top Action Bar: Título e Botão Novo Inventário */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 w-full px-2 sm:px-4">
        <div>
          <h1 className="font-['Linden_Hill',serif] text-2xl sm:text-3xl text-slate-800 font-semibold">
            Inventários
          </h1>
          <p className="text-xs sm:text-sm text-slate-500">
            Gerencie e acompanhe todos os seus inventários cadastrados
          </p>
        </div>
        <div className="w-full sm:w-auto">
          <Button
            text="Novo Inventário"
            type="button"
            width="w-full sm:w-60"
            onClick={openNewInventoryModal}
          />
        </div>
      </div>

      {/* Tabela Híbrida (Cards no mobile / Tabela no desktop) */}
      <div className="w-full">
        <Table
          header={[
            { key: 'name', headerText: 'Nome' },
            { key: 'description', headerText: 'Descrição' },
            { key: 'createdAt', headerText: 'Data de Criação' },
          ]}
          content={inventoryData}
          onRowDoubleClick={handleRowDoubleClick}
          onDeleteItem={handleOpenDeleteModal}
          onEditItem={handleOpenEditModal}
        />
      </div>

      {isNewInventoryModalOpen && (
        <NewInventoryModal
          onClose={closeNewInventoryModal}
          onInventoryCreated={handleInventoryCreated}
        />
      )}

      {isDeleteModalOpen && (
        <ConfirmationModal
          isOpen={isDeleteModalOpen}
          onClose={handleCloseDeleteModal}
          onConfirm={handleConfirmDelete}
          title="Confirmar Deleção"
          message={`Você tem certeza que deseja deletar o inventário "${inventoryToDelete?.name}"? Esta ação não pode ser desfeita.`}
          confirmButtonText="Sim, deletar"
        />
      )}

      {isEditModalOpen && (
        <EditInventoryModal
          isOpen={isEditModalOpen}
          onClose={handleCloseEditModal}
          onConfirmEdit={handleConfirmEdit}
          inventory={inventoryToEdit}
        />
      )}
    </div>
  )
}
