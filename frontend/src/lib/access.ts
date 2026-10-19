// Acesso do usuário na interface. O back-end é quem de fato autoriza; aqui só escondemos o que o
// usuário não pode usar.
//
// - Administrador (coluna is_administrator): vê e gerencia tudo, em todos os setores.
// - Os demais têm um cargo em cada setor em que estão alocados, e as permissões do cargo valem
//   só naquele setor:
//   - MANAGE_SECTOR: gerencia os inventários do setor e aloca usuários nele.
//   - MANAGE_ITEM:   escaneia, valida itens e adiciona observações.
//   - sem permissão: somente leitura no setor.
import type { Allocation } from '@/contexts/Auth/types'

export const MANAGE_SECTOR = 'MANAGE_SECTOR'
export const MANAGE_ITEM = 'MANAGE_ITEM'

export type SessionAccess = {
  administrator: boolean
  allocations: Allocation[]
}

export const capabilitiesOf = ({
  administrator,
  allocations,
}: SessionAccess) => {
  const managedSectorIds = allocations
    .filter((allocation) => allocation.permissions.includes(MANAGE_SECTOR))
    .map((allocation) => allocation.sectorId)
  const operatedSectorIds = allocations
    .filter(
      (allocation) =>
        allocation.permissions.includes(MANAGE_SECTOR) ||
        allocation.permissions.includes(MANAGE_ITEM),
    )
    .map((allocation) => allocation.sectorId)
  const managesSomeSector = managedSectorIds.length > 0
  return {
    isAdmin: administrator,
    // Setores em que o cargo do usuário tem MANAGE_SECTOR.
    managedSectorIds,
    // Telas de Usuários e de Cargos (criar, editar e excluir cargos): só o administrador.
    canManageUsers: administrator,
    canManageRoles: administrator,
    // Tela de setores: admin gerencia, gestor consulta os seus.
    canReadSectors: administrator || managesSomeSector,
    // Criar inventário: admin, ou gestor de algum setor (o setor é escolhido no formulário).
    canCreateInventories: administrator || managesSomeSector,
    // Criar, editar, finalizar e excluir inventários; importar e remover itens; editar dados:
    // admin ou gestor do setor do inventário.
    canManageInventoryIn: (sectorId?: number | null) =>
      administrator ||
      (sectorId != null && managedSectorIds.includes(sectorId)),
    // Escanear, validar itens e registrar observações no setor do inventário.
    canOperateItemsIn: (sectorId?: number | null) =>
      administrator ||
      (sectorId != null && operatedSectorIds.includes(sectorId)),
  }
}

// Texto curto do cargo do usuário para o menu: o cargo, se for o mesmo em todos os setores.
export const accessLabel = (user: {
  administrator: boolean
  allocations: Allocation[]
}) => {
  if (user.administrator) return 'Administrador'
  const names = [...new Set(user.allocations.map((a) => a.roleName))]
  if (names.length === 0) return 'Sem acesso'
  return names.length === 1 ? names[0] : 'Vários cargos'
}
