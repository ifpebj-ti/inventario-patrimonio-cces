import { useEffect, useState } from 'react'
import { getInventoryRequest } from '@/services/inventory'

// Setor do inventário aberto. O cargo do usuário vale por setor, então as telas de inventário e de
// item só sabem o que ele pode fazer depois de conhecer o setor. `undefined` enquanto carrega;
// `null` se o inventário não tem setor (legado) ou não foi possível ler.
export const useInventorySector = (inventoryId: number) => {
  const [sectorId, setSectorId] = useState<number | null | undefined>(undefined)

  useEffect(() => {
    if (!Number.isFinite(inventoryId)) return
    const controller = new AbortController()
    getInventoryRequest(inventoryId, controller.signal)
      .then((details) => {
        if (!controller.signal.aborted) setSectorId(details.inventory.sectorId)
      })
      .catch(() => {
        if (!controller.signal.aborted) setSectorId(null)
      })
    return () => controller.abort()
  }, [inventoryId])

  return sectorId
}
