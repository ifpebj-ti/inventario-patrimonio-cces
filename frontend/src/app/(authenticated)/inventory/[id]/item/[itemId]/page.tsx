'use client'

import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog'
import { badgeVariant } from '@/lib/tone'
import { Badge as UiBadge } from '@/components/ui/badge'
import { Button as UiButton } from '@/components/ui/button'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { useAdministrationAccess } from '@/contexts/AdministrationAccess'
import { useInventorySector } from '@/hooks/useInventorySector'
import { Spinner } from '@/components/ui/spinner'

import { Card as UiCard } from '@/components/ui/card'

import { Label as UiLabel } from '@/components/ui/label'
import { Input as UiInput } from '@/components/ui/input'
import { Textarea as UiTextarea } from '@/components/ui/textarea'

import { useParams } from 'next/navigation'
import { useEffect, useState } from 'react'
import type { Item, Observation } from '@/commons/models/item'
import {
  getItemByIdRequest,
  updateItemRequest,
  updateItemNotesRequest,
} from '@/services/item'
import { ObservationModal } from '@/components/organisms/modalObservation'
import { toast } from 'sonner'

type Draft = {
  code: string
  description: string
  responsible: string
  locale: string
  price: string
}
const draftFrom = (item: Item): Draft => ({
  code: item.code,
  description: item.description || '',
  responsible: item.responsible || '',
  locale: item.locale || '',
  price: String(item.price ?? '').replace('.', ','),
})

export default function ItemDetailPage() {
  const { id, itemId } = useParams()
  const [item, setItem] = useState<Item | null>(null)
  const [draft, setDraft] = useState<Draft | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [editError, setEditError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [notesOpen, setNotesOpen] = useState(false)
  // Editar dados do item: gestor do setor do inventário (ou admin). Observações: também quem tem
  // MANAGE_ITEM nele. Os demais só leem. O cargo vale por setor.
  const { canManageInventoryIn, canOperateItemsIn } = useAdministrationAccess()
  const inventorySectorId = useInventorySector(Number(id))
  const canManageInventories = canManageInventoryIn(inventorySectorId)
  const canOperateItems = canOperateItemsIn(inventorySectorId)

  useEffect(() => {
    let active = true
    setLoading(true)
    setError(null)
    setItem(null)
    setDraft(null)
    setNotesOpen(false)
    getItemByIdRequest(Number(itemId))
      .then((result) => {
        if (active) setItem(result)
      })
      .catch(() => {
        if (active) setError('Não foi possível carregar o item.')
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [itemId])

  const saveItem = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!item || !draft || busy) return
    const price = Number(draft.price.trim().replace(',', '.'))
    if (!draft.price.trim() || !Number.isFinite(price) || price < 0) {
      setEditError('Informe um valor válido, maior ou igual a zero.')
      return
    }
    setBusy(true)
    setEditError(null)
    try {
      const saved = await updateItemRequest({
        ...item,
        ...draft,
        code: draft.code.trim(),
        price,
      })
      setItem(saved)
      setDraft(null)
      toast.success('Item atualizado com sucesso.')
    } catch {
      setEditError('Não foi possível salvar o item. Tente novamente.')
    } finally {
      setBusy(false)
    }
  }

  const saveNotes = async (notes: Observation[]) => {
    if (!item) throw new Error('Item não encontrado.')
    const saved = await updateItemNotesRequest(item.id, notes)
    setItem(saved)
    toast.success('Observações atualizadas com sucesso.')
  }

  const change = (key: keyof Draft, value: string) =>
    setDraft((previous) => (previous ? { ...previous, [key]: value } : null))

  return (
    <div className="page-shell flex flex-col gap-6">
      {loading && (
        <p
          role="status"
          className="flex items-center gap-2 text-muted-foreground"
        >
          <Spinner className="text-primary" />
          Carregando detalhes do item...
        </p>
      )}
      {error && (
        <Alert
          variant="destructive"
          className="border-destructive/25 bg-destructive/10"
        >
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}
      {item && (
        <>
          <header className="flex flex-wrap items-center justify-between gap-4">
            <div>
              <p className="text-sm text-muted-foreground">Detalhes do item</p>
              <h1 className="page-heading break-all">{item.code}</h1>
            </div>
            <UiBadge
              variant={badgeVariant[item.isValid ? 'success' : 'danger']}
              className="rounded-md font-semibold"
            >
              {item.isValid ? 'Verificado' : 'Pendente'}
            </UiBadge>
          </header>
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <UiCard
              role="region"
              className="gap-0 p-6"
              aria-label="Dados do item"
            >
              <div className="flex flex-wrap items-center justify-between gap-3 mb-5">
                <h2 className="text-lg font-semibold text-foreground">
                  Dados do item
                </h2>
                {canManageInventories && (
                  <UiButton
                    variant="default"
                    className="w-auto"
                    type="button"
                    onClick={() => {
                      setEditError(null)
                      setDraft(draftFrom(item))
                    }}
                  >
                    Editar item
                  </UiButton>
                )}
              </div>
              <dl className="space-y-4 text-sm">
                {[
                  ['Descrição', item.description],
                  ['Carga (responsável)', item.responsible],
                  ['Sala / Local', item.locale],
                  [
                    'Valor',
                    Number(item.price).toLocaleString('pt-BR', {
                      style: 'currency',
                      currency: 'BRL',
                    }),
                  ],
                ].map(([label, value]) => (
                  <div key={label}>
                    <dt className="font-medium text-muted-foreground">
                      {label}
                    </dt>
                    <dd className="mt-1 text-foreground break-words">
                      {value || 'Não informado'}
                    </dd>
                  </div>
                ))}
              </dl>
            </UiCard>
            <UiCard
              role="region"
              className="gap-0 p-6"
              aria-label="Observações do item"
            >
              <div className="flex flex-wrap items-center justify-between gap-3 mb-5">
                <h2 className="text-lg font-semibold text-foreground">
                  Observações
                </h2>
                {canOperateItems && (
                  <UiButton
                    variant={'outline'}
                    className="w-auto"
                    type="button"
                    onClick={() => setNotesOpen(true)}
                  >
                    Editar observações
                  </UiButton>
                )}
              </div>
              <div className="space-y-3 text-sm text-foreground">
                {item.observations?.length ? (
                  item.observations.map((note, index) => (
                    <p
                      key={
                        note.id != null ? `note-${note.id}` : `draft-${index}`
                      }
                      className="rounded-lg bg-muted p-3 break-words"
                    >
                      {note.content}
                    </p>
                  ))
                ) : (
                  <p className="text-muted-foreground">
                    Nenhuma observação registrada.
                  </p>
                )}
              </div>
            </UiCard>
          </div>
        </>
      )}
      {draft && (
        <Dialog
          open
          onOpenChange={(open) => {
            if (!open && !busy) (() => setDraft(null))()
          }}
        >
          <DialogContent
            showCloseButton={false}
            className="flex max-h-[90vh] flex-col overflow-y-auto rounded-xl gap-0 p-6 sm:max-w-lg sm:p-8"
          >
            <DialogTitle className="sr-only">Editar item</DialogTitle>
            <h2 className="text-xl font-bold text-foreground mb-2">
              Editar item
            </h2>
            <p className="text-sm text-muted-foreground mb-6">
              Atualize os dados cadastrais deste patrimônio.
            </p>
            <form onSubmit={saveItem} className="space-y-4">
              <fieldset disabled={busy} className="space-y-4">
                <UiLabel className="flex-col items-stretch gap-1.5 text-sm font-medium text-foreground">
                  Código patrimonial
                  <UiInput
                    required
                    value={draft.code}
                    onChange={(event) => change('code', event.target.value)}
                  />
                </UiLabel>
                <UiLabel className="flex-col items-stretch gap-1.5 text-sm font-medium text-foreground">
                  Descrição
                  <UiTextarea
                    rows={3}
                    value={draft.description}
                    onChange={(event) =>
                      change('description', event.target.value)
                    }
                  />
                </UiLabel>
                <UiLabel className="flex-col items-stretch gap-1.5 text-sm font-medium text-foreground">
                  Carga (responsável)
                  <UiInput
                    value={draft.responsible}
                    onChange={(event) =>
                      change('responsible', event.target.value)
                    }
                  />
                </UiLabel>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                  <UiLabel className="flex-col items-stretch gap-1.5 text-sm font-medium text-foreground">
                    Sala / Local
                    <UiInput
                      value={draft.locale}
                      onChange={(event) => change('locale', event.target.value)}
                    />
                  </UiLabel>
                  <UiLabel className="flex-col items-stretch gap-1.5 text-sm font-medium text-foreground">
                    Valor (R$)
                    <UiInput
                      required
                      inputMode="decimal"
                      value={draft.price}
                      onChange={(event) => change('price', event.target.value)}
                    />
                  </UiLabel>
                </div>
              </fieldset>
              {editError && (
                <Alert
                  variant="destructive"
                  className="border-destructive/25 bg-destructive/10"
                >
                  <AlertDescription>{editError}</AlertDescription>
                </Alert>
              )}
              <div className="flex flex-col-reverse sm:flex-row justify-end gap-3 pt-4">
                <UiButton
                  variant={'outline'}
                  className="w-full sm:w-auto"
                  type="button"
                  disabled={busy}
                  onClick={() => setDraft(null)}
                >
                  Cancelar
                </UiButton>
                <UiButton
                  variant="default"
                  className="w-full sm:w-auto"
                  type="submit"
                  disabled={busy}
                >
                  {busy ? 'Salvando...' : 'Salvar item'}
                </UiButton>
              </div>
            </form>
          </DialogContent>
        </Dialog>
      )}
      {item && (
        <ObservationModal
          isOpen={notesOpen}
          onClose={() => setNotesOpen(false)}
          currentNotes={item.observations || []}
          onSave={saveNotes}
        />
      )}
    </div>
  )
}
