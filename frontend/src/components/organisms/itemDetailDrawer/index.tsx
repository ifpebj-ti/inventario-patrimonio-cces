'use client'

import { Button as UiButton } from '@/components/ui/button'
import { Card as UiCard } from '@/components/ui/card'
import { Label as UiLabel } from '@/components/ui/label'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import { Textarea as UiTextarea } from '@/components/ui/textarea'

import { ConfirmationModal } from '@/components/organisms/modalConfirmation'

import React, { useState, useEffect } from 'react'
import { Item, Observation } from '@/commons/models/item'
import {
  X,
  CheckCircle2,
  Clock,
  Plus,
  Trash2,
  Barcode,
  Save,
} from 'lucide-react'
import { updateItemRequest, updateItemNotesRequest } from '@/services/item'
import { toast } from 'sonner'

interface ItemDetailDrawerProps {
  isOpen: boolean
  onClose: () => void
  item: Item | null
  onItemUpdated: (updatedItem: Item) => void
  onScanNext?: () => void
  isResponsibleLocked?: boolean
}

export const ItemDetailDrawer: React.FC<ItemDetailDrawerProps> = ({
  isOpen,
  onClose,
  item,
  onItemUpdated,
}) => {
  const [formData, setFormData] = useState<Item | null>(null)
  const [deleteIndex, setDeleteIndex] = React.useState<number | null>(null)
  const [observations, setObservations] = useState<Observation[]>([])
  const [newNoteText, setNewNoteText] = useState('')
  const [isSaving, setIsSaving] = useState(false)

  // Sincroniza dados sempre que o item muda ou a Drawer abre
  useEffect(() => {
    if (item) {
      setFormData({ ...item })
      setObservations(item.observations ? [...item.observations] : [])
      setNewNoteText('')
      setDeleteIndex(null)
    }
  }, [item, isOpen])

  if (!formData) return null

  // Alterna o status de validação (Válido / Pendente)
  const toggleValidationStatus = () => {
    setFormData((prev) => (prev ? { ...prev, isValid: !prev.isValid } : null))
  }

  // Adiciona nova observação
  const handleAddObservation = () => {
    if (!newNoteText.trim()) return
    const newObs: Observation = {
      id: null,
      content: newNoteText.trim(),
    }
    setObservations((prev) => [...prev, newObs])
    setNewNoteText('')
  }

  // Remove observação
  const handleDeleteObservation = (index: number) => {
    setObservations((prev) => prev.filter((_, i) => i !== index))
  }

  // Salva no backend
  const handleSave = async () => {
    if (!formData || isSaving) return

    try {
      setIsSaving(true)

      // 1. Atualiza dados principais do item preservando o status de validação
      const payload: Item = {
        ...formData,
        isValid: formData.isValid,
      }
      const updatedItem = await updateItemRequest(payload)

      // 2. Atualiza observações se houver alterações
      const finalItem = await updateItemNotesRequest(
        updatedItem.id,
        newNoteText.trim()
          ? [...observations, { id: null, content: newNoteText.trim() }]
          : observations,
      )

      // Garante que o status isValid modificado pelo usuário seja mantido
      const itemToEmit: Item = {
        ...finalItem,
        isValid: formData.isValid ?? finalItem.isValid,
      }

      onItemUpdated(itemToEmit)
      toast.success(`Patrimônio ${itemToEmit.code} atualizado!`)

      onClose()
    } catch (error) {
      console.error('Erro ao salvar item:', error)
      toast.error('Não foi possível salvar as alterações do item.')
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <>
      <Sheet
        open={isOpen}
        onOpenChange={(open) => {
          if (!open && !isSaving) onClose()
        }}
      >
        <SheetContent
          side="right"
          showCloseButton={false}
          className="w-screen gap-0 sm:max-w-md"
        >
          {/* Topo / Header */}
          <SheetHeader className="flex-row items-center justify-between border-b bg-muted/50 px-5 py-4 sm:px-6">
            <div className="flex min-w-0 items-center gap-2.5">
              <div className="shrink-0 rounded-lg bg-primary/10 p-2 text-primary">
                <Barcode className="size-5" />
              </div>
              <div className="truncate">
                <SheetDescription className="block text-[11px] font-bold tracking-wider uppercase">
                  Item Auditado
                </SheetDescription>
                <SheetTitle className="truncate text-2xl font-bold sm:text-3xl">
                  {formData.code}
                </SheetTitle>
              </div>
            </div>

            <UiButton
              variant="ghost"
              size="icon-lg"
              type="button"
              onClick={() => {
                if (!isSaving) onClose()
              }}
              aria-label="Fechar gaveta"
              className="text-muted-foreground"
            >
              <X className="size-5" />
            </UiButton>
          </SheetHeader>

          {/* Corpo / Conteúdo com Scroll */}
          <div className="flex-1 space-y-5 overflow-y-auto px-5 py-5 sm:px-6">
            {/* Toggle de Status: Verificado vs Pendente */}
            <UiCard className="flex-row items-center justify-between gap-3 bg-muted p-4 shadow-none">
              <div className="min-w-0">
                <span className="block text-xs font-semibold text-foreground">
                  Status de Verificação
                </span>
                <span className="block text-[11px] leading-tight text-muted-foreground">
                  {formData.isValid
                    ? 'Item conferido fisicamente no local.'
                    : 'Pendente de conferência ou ausente.'}
                </span>
              </div>

              <UiButton
                variant={formData.isValid ? 'success' : 'default'}
                type="button"
                disabled={isSaving}
                onClick={toggleValidationStatus}
                className={
                  formData.isValid
                    ? 'shrink-0'
                    : 'shrink-0 bg-warning text-white hover:bg-warning/90'
                }
              >
                {formData.isValid ? (
                  <>
                    <CheckCircle2 />
                    <span>Verificado</span>
                  </>
                ) : (
                  <>
                    <Clock />
                    <span>Pendente</span>
                  </>
                )}
              </UiButton>
            </UiCard>

            <UiCard
              role="region"
              aria-label="Dados cadastrais do item"
              className="gap-3 p-4 shadow-none"
            >
              <h3 className="text-sm font-semibold text-foreground">
                Dados do patrimônio
              </h3>
              <dl className="space-y-3 text-sm">
                {[
                  ['Descrição', formData.description],
                  ['Carga (responsável)', formData.responsible],
                  ['Sala cadastrada', formData.locale],
                ].map(([label, value]) => (
                  <div key={label}>
                    <dt className="font-medium text-muted-foreground">
                      {label}
                    </dt>
                    <dd className="mt-1 break-words text-foreground">
                      {value || 'Não informado'}
                    </dd>
                  </div>
                ))}
              </dl>
            </UiCard>

            {/* Observações de Campo */}
            <div className="space-y-3 border-t pt-3">
              <UiLabel className="text-xs font-bold tracking-wider text-muted-foreground uppercase">
                Observações de Campo ({observations.length})
              </UiLabel>

              <UiCard className="gap-3 border-primary/25 bg-primary/10 p-4 shadow-none">
                <UiLabel
                  htmlFor="audit-new-observation"
                  className="text-sm font-semibold text-foreground"
                >
                  Nova observação
                </UiLabel>
                <p className="text-xs text-muted-foreground">
                  O item está em outra sala? Informe aqui a sala em que foi
                  encontrado e outras diferenças identificadas.
                </p>
                <UiTextarea
                  id="audit-new-observation"
                  rows={3}
                  value={newNoteText}
                  disabled={isSaving}
                  onChange={(event) => setNewNoteText(event.target.value)}
                  placeholder="Ex.: Encontrado na sala 203, diferente da sala cadastrada."
                  className="bg-card"
                />
                <UiButton
                  type="button"
                  onClick={handleAddObservation}
                  disabled={isSaving || !newNoteText.trim()}
                  className="w-full"
                >
                  <Plus /> Adicionar observação à lista
                </UiButton>
                <p className="text-xs text-muted-foreground">
                  Ao terminar, clique em Salvar. O texto digitado também será
                  incluído ao salvar.
                </p>
              </UiCard>

              {/* Lista de Observações */}
              <div className="max-h-48 space-y-2 overflow-y-auto pr-1">
                {observations.length === 0 ? (
                  <p className="py-2 text-xs text-muted-foreground italic">
                    Nenhuma observação registrada para este item.
                  </p>
                ) : (
                  observations.map((obs, idx) => (
                    <div
                      key={idx}
                      className="flex items-start justify-between gap-2 rounded-lg border bg-muted p-2.5 text-xs text-foreground"
                    >
                      <span className="leading-relaxed">{obs.content}</span>
                      <UiButton
                        variant="ghost"
                        size="icon-xs"
                        type="button"
                        disabled={isSaving}
                        onClick={() => setDeleteIndex(idx)}
                        aria-label="Deletar observação"
                        className="shrink-0 text-muted-foreground hover:bg-destructive/10 hover:text-destructive"
                      >
                        <Trash2 />
                      </UiButton>
                    </div>
                  ))
                )}
              </div>
            </div>
          </div>

          {/* Rodapé de Ações Finais */}
          <SheetFooter className="border-t bg-muted/50 p-5">
            <UiButton
              size="lg"
              type="button"
              onClick={handleSave}
              disabled={isSaving}
              className="w-full"
            >
              <Save />
              <span>{isSaving ? 'Salvando...' : 'Salvar'}</span>
            </UiButton>
          </SheetFooter>
        </SheetContent>
      </Sheet>
      <ConfirmationModal
        isOpen={deleteIndex !== null}
        onClose={() => setDeleteIndex(null)}
        onConfirm={() => {
          if (deleteIndex !== null) handleDeleteObservation(deleteIndex)
          setDeleteIndex(null)
        }}
        title="Excluir observação"
        message="Deseja excluir esta observação? A remoção será gravada quando você salvar as alterações."
        confirmButtonText="Excluir observação"
      />
    </>
  )
}
