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

  const handleRowDoubleClick = (inventory: InventoryResponse) => {
    router.push(`/inventory/${inventory.id}`)
  }

  const handleOpenDeleteModal = (inventory: InventoryResponse) => {
    setInventoryToDelete(inventory)
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

  const handleConfirmEdit = async (data: UpdateInventory) => {
    if (!inventoryToEdit) return
    try {
      const updatedInventory = await updateInventoryRequest(
        data,
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
    <div className="overflow-x-hidden flex flex-col justify-center items-center">
      <div className="flex flex-col items-center min-h-screen">
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
        <Button
          text="Novo Inventário"
          type="button"
          width="w-72"
          onClick={openNewInventoryModal}
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
