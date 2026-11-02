import { useEffect, useState } from 'react'
import type { Room } from '@/commons/models/room'
import { listSectorRooms } from '@/services/rooms'

// Salas do setor, para quem valida escolher onde o item foi encontrado. `loaded` só vira
// verdadeiro depois da resposta, para a tela não decidir antes de saber se o setor tem salas.
// Sem permissão de leitura ou com erro, a lista fica vazia (o servidor continua exigindo a sala).
export const useSectorRooms = (sectorId: number | null | undefined) => {
  const [state, setState] = useState<{
    sectorId: number | null | undefined
    rooms: Room[]
  } | null>(null)

  useEffect(() => {
    if (!sectorId) return
    const controller = new AbortController()
    listSectorRooms(sectorId, controller.signal)
      .then((rooms) => {
        if (!controller.signal.aborted) setState({ sectorId, rooms })
      })
      .catch(() => {
        if (!controller.signal.aborted) setState({ sectorId, rooms: [] })
      })
    return () => controller.abort()
  }, [sectorId])

  const current = state && state.sectorId === sectorId ? state : null
  return {
    rooms: current?.rooms ?? [],
    loaded: sectorId === null || current !== null,
  }
}
