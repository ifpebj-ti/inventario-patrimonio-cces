'use client'

import { Button as UiButton } from '@/components/ui/button'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Card as UiCard } from '@/components/ui/card'
import { Spinner } from '@/components/ui/spinner'
import {
  InventoryFilters,
  type StatusFilter,
} from '@/components/organisms/inventoryFilters'
import { EditInventoryModal } from '@/components/organisms/editInventoryModal'
import { ConfirmationModal } from '@/components/organisms/modalConfirmation'
import { NewInventoryModal } from '@/components/organisms/newInventoryModal'
import { Table } from '@/components/organisms/table'
import { useInventory } from '@/contexts/InventoryContext'
import {
  deleteInventoryRequest,
  InventoryResponse,
  updateInventoryRequest,
} from '@/services/inventory'
import { useRouter, useSearchParams } from 'next/navigation'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { toast } from 'sonner'
import { Plus } from 'lucide-react'
import { useAuth } from '@/hooks/useAuth'
import { useAdministrationAccess } from '@/contexts/AdministrationAccess'
import { listSectors } from '@/services/administration'
import type { Sector } from '@/commons/models/administration'

const parseDate = (value: string | null) => {
  if (!value) return null
  if (/^\d{2}\/\d{2}\/\d{4}$/.test(value)) {
    const [day, month, year] = value.split('/')
    return new Date(`${year}-${month}-${day}T00:00:00`)
  }
  const date = new Date(value.includes('T') ? value : `${value}T00:00:00`)
  if (Number.isNaN(date.getTime())) return null
  date.setHours(0, 0, 0, 0)
  return date
}

export default function InventoryListPage() {
  const { inventories, isLoading, error, refreshInventories } = useInventory()
  const { user } = useAuth()
  // Só admin e gestor criam, editam e excluem inventários; os demais perfis só consultam.
  const { canManageInventories } = useAdministrationAccess()
  const router = useRouter()
  const searchParams = useSearchParams()
  const [search, setSearch] = useState('')
  const [statusFilter, setStatusFilter] = useState<StatusFilter>(
    searchParams.get('status') === 'OPEN'
      ? 'EM_ANDAMENTO'
      : ['EM_ANDAMENTO', 'CONCLUIDO', 'VENCIDO'].includes(
            searchParams.get('status') || '',
          )
        ? (searchParams.get('status') as StatusFilter)
        : 'TODOS',
  )
  const [sectorFilter, setSectorFilter] = useState('TODOS')
  const [yearFilter, setYearFilter] = useState('TODOS')
  const [isNewOpen, setIsNewOpen] = useState(false)
  const [inventoryToDelete, setInventoryToDelete] =
    useState<InventoryResponse | null>(null)
  const [inventoryToEdit, setInventoryToEdit] =
    useState<InventoryResponse | null>(null)
  const [sectorCatalog, setSectorCatalog] = useState<Sector[]>([])

  useEffect(() => {
    if (
      !user?.permissions.some((permission) =>
        ['ADMIN', 'MANAGE_SECTOR'].includes(permission),
      )
    )
      return
    const controller = new AbortController()
    listSectors(controller.signal)
      .then(setSectorCatalog)
      .catch(() => {
        if (!controller.signal.aborted)
          toast.error('Não foi possível carregar o catálogo de setores.')
      })
    return () => controller.abort()
  }, [user?.permissions])

  const sectors = useMemo(
    () =>
      Array.from(
        new Map([
          ...sectorCatalog
            .filter(
              (sector) =>
                user?.permissions.includes('ADMIN') ||
                user?.sectorIds.includes(sector.id),
            )
            .map((sector) => [sector.id, sector.name] as const),
          ...inventories
            .filter(
              (item) =>
                item.sectorId &&
                item.sectorName &&
                (user?.permissions.includes('ADMIN') ||
                  user?.sectorIds.includes(item.sectorId)),
            )
            .map((item) => [item.sectorId!, item.sectorName!] as const),
        ]),
      ).map(([id, name]) => ({ id, name })),
    [inventories, user?.sectorIds, user?.permissions, sectorCatalog],
  )

  const years = useMemo(
    () =>
      [
        ...new Set(
          inventories
            .map((inventory) => parseDate(inventory.createdAt)?.getFullYear())
            .filter((year): year is number => year !== undefined),
        ),
      ].sort((a, b) => b - a),
    [inventories],
  )

  const filteredInventories = useMemo(() => {
    const normalize = (value: string) =>
      value
        .normalize('NFD')
        .replace(/\p{Diacritic}/gu, '')
        .toLocaleLowerCase('pt-BR')
    const normalizedSearch = normalize(search.trim())
    return inventories.filter((inventory) => {
      return (
        (!normalizedSearch ||
          normalize(inventory.name).includes(normalizedSearch)) &&
        (statusFilter === 'TODOS' || inventory.status === statusFilter) &&
        (sectorFilter === 'TODOS' ||
          inventory.sectorId === Number(sectorFilter)) &&
        (yearFilter === 'TODOS' ||
          parseDate(inventory.createdAt)?.getFullYear() === Number(yearFilter))
      )
    })
  }, [yearFilter, inventories, search, sectorFilter, statusFilter])

  const onCreated = useCallback(async () => {
    await refreshInventories()
    setIsNewOpen(false)
  }, [refreshInventories])
  const onDelete = async () => {
    if (!inventoryToDelete) return
    try {
      await deleteInventoryRequest(inventoryToDelete.id)
      await refreshInventories()
      toast.success('Inventário excluído com sucesso.')
    } catch {
      toast.error('Não foi possível excluir o inventário.')
    } finally {
      setInventoryToDelete(null)
    }
  }
  const onEdit = async (data: {
    name: string
    description?: string
    plannedEndDate: string
    sectorId: number
  }) => {
    if (!inventoryToEdit) return
    try {
      await updateInventoryRequest(
        {
          name: data.name,
          description: data.description || '',
          plannedEndDate: data.plannedEndDate,
          sectorId: data.sectorId,
        },
        inventoryToEdit.id,
      )
      await refreshInventories()
      toast.success('Inventário atualizado com sucesso.')
      setInventoryToEdit(null)
    } catch {
      toast.error('Não foi possível atualizar o inventário.')
    }
  }

  return (
    <div className="page-shell inventory-reference flex min-h-screen flex-col gap-4">
      <header className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="page-heading">Inventários</h1>
          <p className="page-description">
            {canManageInventories
              ? 'Consulte, filtre e gerencie os inventários cadastrados.'
              : 'Consulte e filtre os inventários do seu setor.'}
          </p>
        </div>
        {canManageInventories && (
          <UiButton
            type="button"
            className="inventory-new-button shrink-0"
            onClick={() => setIsNewOpen(true)}
          >
            <Plus /> Novo Inventário
          </UiButton>
        )}
      </header>
      <InventoryFilters
        search={search}
        onSearch={setSearch}
        status={statusFilter}
        onStatus={setStatusFilter}
        sector={sectorFilter}
        onSector={setSectorFilter}
        sectors={sectors}
        year={yearFilter}
        onYear={setYearFilter}
        years={years}
        onClear={() => {
          setSearch('')
          setStatusFilter('TODOS')
          setSectorFilter('TODOS')
          setYearFilter('TODOS')
          router.replace('/inventory', { scroll: false })
        }}
      />
      {isLoading ? (
        <UiCard className="items-center p-10 text-sm text-muted-foreground">
          <Spinner className="size-6 text-primary" />
          Carregando inventários…
        </UiCard>
      ) : error ? (
        <Alert
          variant="destructive"
          className="border-destructive/25 bg-destructive/10 p-6"
        >
          <AlertDescription className="items-center text-center">
            <p>{error}</p>
            <UiButton
              variant="default"
              className="mt-4 w-52"
              type="button"
              onClick={() => refreshInventories()}
            >
              Tentar novamente
            </UiButton>
          </AlertDescription>
        </Alert>
      ) : (
        <Table
          appearance="inventory"
          filterToolbar={
            <div className="flex w-full flex-wrap items-center justify-between gap-3">
              <span role="status" className="text-xs text-muted-foreground">
                {filteredInventories.length} de {inventories.length} inventários
              </span>
            </div>
          }

          header={[
            { key: 'name', headerText: 'Nome' },
            { key: 'description', headerText: 'Descrição' },
            { key: 'sectorName', headerText: 'Setor' },
            { key: 'createdAt', headerText: 'Criação' },
            { key: 'completedAt', headerText: 'Data de finalização' },
            { key: 'plannedEndDate', headerText: 'Finalização prevista' },
            { key: 'status', headerText: 'Status' },
          ]}
          content={filteredInventories}
          onRowDoubleClick={(item) => router.push(`/inventory/${item.id}`)}
          onDeleteItem={
            canManageInventories
              ? (item) => setInventoryToDelete(item as InventoryResponse)
              : undefined
          }
          onEditItem={
            canManageInventories
              ? (item) => setInventoryToEdit(item as InventoryResponse)
              : undefined
          }
        />
      )}
      {canManageInventories && isNewOpen && (
        <NewInventoryModal
          onClose={() => setIsNewOpen(false)}
          onInventoryCreated={onCreated}
        />
      )}
      {inventoryToDelete && (
        <ConfirmationModal
          isOpen
          onClose={() => setInventoryToDelete(null)}
          onConfirm={onDelete}
          title="Confirmar exclusão"
          message={`Deseja excluir o inventário "${inventoryToDelete.name}"? Esta ação não pode ser desfeita.`}
          confirmButtonText="Excluir"
        />
      )}
      {inventoryToEdit && (
        <EditInventoryModal
          isOpen
          onClose={() => setInventoryToEdit(null)}
          onConfirmEdit={onEdit}
          inventory={inventoryToEdit}
        />
      )}
    </div>
  )
}
