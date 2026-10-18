'use client'

import { cn } from '@/lib/utils'
import { chipToneClass } from '@/lib/tone'
import { Badge as UiBadge } from '@/components/ui/badge'
import { Card as UiCard } from '@/components/ui/card'

import { Button as UiButton } from '@/components/ui/button'

import { useCallback, useEffect, useRef, useState } from 'react'
import axios from 'axios'
import { toast } from 'sonner'
import { Clock3 } from 'lucide-react'
import { ConfirmationModal } from '@/components/organisms/modalConfirmation'
import { useInventory } from '@/contexts/InventoryContext'
import {
  finalizeInventoryRequest,
  getInventoryRequest,
  type InventoryDetails,
} from '@/services/inventory'

const failureMessage = (error: unknown) => {
  if (axios.isAxiosError(error)) {
    if (error.response?.status === 403)
      return 'Você não tem permissão para finalizar este inventário.'
    if (error.response?.status === 409)
      return 'Este inventário já foi finalizado. O estado foi atualizado.'
    if (error.response?.status === 404) return 'Inventário não encontrado.'
  }
  return 'Não foi possível concluir a operação. Tente novamente.'
}

export const InventoryCompletion = ({
  inventoryId,
  disabled = false,
  onCompletedChange,
}: {
  inventoryId: number
  disabled?: boolean
  onCompletedChange?: (completed: boolean) => void
}) => {
  const { refreshInventories } = useInventory()
  const [details, setDetails] = useState<InventoryDetails | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const submitting = useRef(false)

  const load = useCallback(
    async (signal?: AbortSignal) => {
      try {
        const result = await getInventoryRequest(inventoryId, signal)
        if (!signal?.aborted) {
          setDetails(result)
          setError(null)
        }
      } catch (error) {
        if (!signal?.aborted) {
          setDetails(null)
          setError(failureMessage(error))
        }
      }
    },
    [inventoryId],
  )

  useEffect(() => {
    const controller = new AbortController()
    load(controller.signal)
    return () => controller.abort()
  }, [load])

  const finish = async () => {
    if (submitting.current || disabled) return
    submitting.current = true
    setBusy(true)
    setError(null)
    try {
      const inventory = await finalizeInventoryRequest(inventoryId)
      setDetails({ inventory, canFinalize: false })
      setOpen(false)
      toast.success('Inventário finalizado com sucesso!')
      await refreshInventories()
    } catch (error) {
      setOpen(false)
      if (
        axios.isAxiosError(error) &&
        [403, 409].includes(error.response?.status ?? 0)
      ) {
        await load()
        await refreshInventories()
      }
      setError(failureMessage(error))
    } finally {
      submitting.current = false
      setBusy(false)
    }
  }

  const completed = details?.inventory.status === 'CONCLUIDO'
  useEffect(() => {
    onCompletedChange?.(completed)
  }, [completed, onCompletedChange])
  return (
    <UiCard
      role="region"
      aria-label="Situação do inventário"
      className="gap-0 p-5"
    >
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="space-y-2">
          <h2 className="text-lg font-semibold text-foreground">
            Situação do inventário
          </h2>
          {details ? (
            <>
              <UiBadge
                variant="outline"
                className={cn(
                  'rounded-md',
                  chipToneClass[
                    completed
                      ? 'success'
                      : details.inventory.status === 'VENCIDO'
                        ? 'danger'
                        : 'primary'
                  ],
                )}
              >
                <Clock3 />
                {completed
                  ? 'Finalizado'
                  : details.inventory.status === 'VENCIDO'
                    ? 'Atrasado'
                    : 'Em andamento'}
              </UiBadge>
              {details.inventory.completedAt && (
                <p className="text-sm text-muted-foreground">
                  Finalizado em{' '}
                  {new Date(details.inventory.completedAt).toLocaleString(
                    'pt-BR',
                  )}
                </p>
              )}
              {!completed && (
                <p className="text-sm text-muted-foreground">
                  A finalização registra a data e atualiza os indicadores do
                  Dashboard.
                </p>
              )}
            </>
          ) : (
            !error && <p role="status">Carregando situação...</p>
          )}
        </div>
        {details?.canFinalize && !completed && (
          <UiButton
            variant="default"
            className="w-auto"
            type="button"
            disabled={busy || disabled}
            onClick={() => setOpen(true)}
          >
            Finalizar inventário
          </UiButton>
        )}
      </div>
      {error && (
        <div role="alert" className="mt-3 text-sm text-destructive">
          <p>{error}</p>
          <UiButton
            variant="link"
            type="button"
            className="mt-2 h-auto p-0 text-destructive"
            disabled={busy}
            onClick={() => load()}
          >
            Atualizar situação
          </UiButton>
        </div>
      )}
      <ConfirmationModal
        isOpen={open}
        onClose={() => {
          if (!submitting.current) setOpen(false)
        }}
        onConfirm={finish}
        title="Finalizar inventário"
        message="Deseja finalizar este inventário? O status será alterado para finalizado e a data será registrada. Não há opção de reabertura."
        confirmButtonText="Sim, finalizar"
        busy={busy}
      />
    </UiCard>
  )
}
