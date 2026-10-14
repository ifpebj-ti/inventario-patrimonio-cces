'use client'

import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button as UiButton } from '@/components/ui/button'
import { Card as UiCard } from '@/components/ui/card'
import { Label as UiLabel } from '@/components/ui/label'
import { Textarea as UiTextarea } from '@/components/ui/textarea'

import { ConfirmationModal } from '@/components/organisms/modalConfirmation'

import React from 'react'
import { Plus, X } from 'lucide-react'
import { Observation } from '@/commons/models/item'

// Define as propriedades que o modal de observações espera receber.
interface ObservationModalProps {
  isOpen: boolean
  onClose: () => void
  onSave: (updatedNotes: Observation[]) => void | Promise<void>
  currentNotes: Observation[]
}

// Componente de modal para visualizar, adicionar, editar e deletar observações de um item.
export const ObservationModal: React.FC<ObservationModalProps> = ({
  isOpen,
  onClose,
  currentNotes,
  onSave,
}) => {
  // Estado interno para manipular as observações sem afetar o estado pai até que 'Concluir' seja clicado.
  const [deleteIndex, setDeleteIndex] = React.useState<number | null>(null)
  const [observations, setObservations] = React.useState<Observation[]>([])
  // Estado para o campo de texto de uma nova observação.
  const [newObservationText, setNewObservationText] = React.useState<string>('')
  // Estado para controlar qual observação está sendo editada no momento (pelo seu índice no array).
  const [editingIndex, setEditingIndex] = React.useState<number | null>(null)

  const [busy, setBusy] = React.useState(false)
  const [error, setError] = React.useState<string | null>(null)

  // Efeito que inicializa ou reseta o estado do modal sempre que ele é aberto.
  React.useEffect(() => {
    // Só executa a lógica se o modal estiver sendo aberto.
    if (isOpen) {
      // Carrega as observações atuais (vindas do componente pai) no estado local.
      setObservations(currentNotes ?? [])
      // Reseta os campos de texto e o modo de edição.
      setError(null)
      setNewObservationText('')
      setEditingIndex(null)
      setDeleteIndex(null)
    }
  }, [isOpen, currentNotes])

  // Se o modal não estiver aberto, não renderiza nada.
  if (!isOpen) return null

  // Adiciona uma nova observação à lista local.
  const handleAddObservation = () => {
    const trimmedText = newObservationText.trim()
    // Só adiciona se o texto não estiver vazio.
    if (trimmedText) {
      const newObservation: Observation = {
        id: null, // O ID será gerado pelo backend ao salvar.
        content: trimmedText,
      }
      setObservations((prev) => [...prev, newObservation])
      setNewObservationText('')
    }
  }

  // Deleta uma observação da lista local com base no seu índice.
  const handleDeleteObservation = (indexToDelete: number) => {
    setObservations((prev) =>
      prev.filter((_, index) => index !== indexToDelete),
    )
    // Lógica para ajustar o índice de edição caso um item seja removido.
    if (editingIndex === indexToDelete) {
      setEditingIndex(null)
    } else if (editingIndex !== null && editingIndex > indexToDelete) {
      setEditingIndex((prev) => (prev !== null ? prev - 1 : null))
    }
  }

  // Prepara o estado para entrar no modo de edição de uma observação específica.
  const handleStartEdit = (index: number) => {
    setEditingIndex(index)
  }

  // Função final chamada pelo botão "Concluir".
  const handleConclude = async () => {
    if (busy) return
    const finalObservations = observations
      .filter((note) => note.content.trim() !== '')
      .map((note) => ({ ...note, content: note.content.trim() }))
    if (newObservationText.trim())
      finalObservations.push({ id: null, content: newObservationText.trim() })
    setBusy(true)
    setError(null)
    try {
      await onSave(finalObservations)
      onClose()
    } catch {
      setError('Não foi possível salvar as observações. Tente novamente.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <Dialog
        open
        onOpenChange={(open) => {
          if (!open && !busy) onClose()
        }}
      >
        <DialogContent
          showCloseButton={false}
          className="flex max-h-[90vh] flex-col overflow-y-auto rounded-xl gap-0 p-6 sm:max-w-lg sm:p-8"
        >
          <DialogTitle className="sr-only">Editar observações</DialogTitle>
          <h2 className="text-xl font-bold text-foreground pr-8">
            Editar observações
          </h2>
          <UiButton
            variant="ghost"
            size="icon-sm"
            type="button"
            disabled={busy}
            onClick={onClose}
            aria-label="Fechar modal"
            className="absolute top-4 right-4 text-muted-foreground"
          >
            <X />
          </UiButton>
          <p className="text-sm text-muted-foreground mt-2 mb-6">
            Adicione, edite ou exclua notas. Ao terminar, clique em Salvar
            observações para gravar as mudanças.
          </p>
          <fieldset disabled={busy} className="space-y-6">
            <UiCard
              role="region"
              aria-label="Adicionar nova observação"
              className="gap-0 border-primary/25 bg-primary/10 p-4 shadow-none"
            >
              <UiLabel
                htmlFor="new-observation"
                className="text-sm font-semibold text-foreground mb-2"
              >
                Nova observação
              </UiLabel>
              <UiTextarea
                id="new-observation"
                rows={3}
                value={newObservationText}
                onChange={(event) => setNewObservationText(event.target.value)}
                placeholder="Nova observação"
                className="resize-y bg-card"
              />
              <div className="flex justify-end mt-3">
                <UiButton
                  variant="default"
                  className="w-full sm:w-auto"
                  type="button"
                  disabled={!newObservationText.trim()}
                  onClick={handleAddObservation}
                >
                  <Plus /> Adicionar à lista
                </UiButton>
              </div>
              <p className="text-xs text-muted-foreground mt-2">
                A nota entra na lista abaixo. Você também pode salvar
                diretamente para incluir o texto digitado.
              </p>
            </UiCard>
            <section aria-label="Lista de observações" className="space-y-3">
              <h3 className="text-sm font-semibold text-foreground">
                Observações ({observations.length})
              </h3>
              {observations.length === 0 ? (
                <p className="rounded-lg border border-dashed p-4 text-sm text-muted-foreground">
                  Nenhuma observação. Escreva sua primeira nota acima.
                </p>
              ) : (
                observations.map((note, index) => (
                  <UiCard
                    key={index}
                    className="gap-0 bg-muted p-4 shadow-none"
                  >
                    <p className="text-xs font-medium text-muted-foreground mb-2">
                      Observação {index + 1}
                    </p>
                    {editingIndex === index ? (
                      <UiLabel className="flex-col items-stretch gap-1 text-sm text-foreground">
                        Texto da observação
                        <UiTextarea
                          autoFocus
                          rows={3}
                          className="bg-card"
                          value={note.content}
                          onChange={(event) =>
                            setObservations((previous) =>
                              previous.map((entry, position) =>
                                position === index
                                  ? { ...entry, content: event.target.value }
                                  : entry,
                              ),
                            )
                          }
                        />
                      </UiLabel>
                    ) : (
                      <p className="text-sm text-foreground whitespace-pre-wrap break-words">
                        {note.content}
                      </p>
                    )}
                    <div className="flex flex-wrap justify-end gap-3 mt-3">
                      <UiButton
                        variant="ghost"
                        type="button"
                        onClick={() =>
                          editingIndex === index
                            ? setEditingIndex(null)
                            : handleStartEdit(index)
                        }
                        className="text-primary hover:bg-primary/10 hover:text-primary"
                      >
                        {editingIndex === index ? 'Concluir edição' : 'Editar'}
                      </UiButton>
                      <UiButton
                        variant="ghost"
                        type="button"
                        onClick={() => setDeleteIndex(index)}
                        className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                      >
                        Excluir
                      </UiButton>
                    </div>
                  </UiCard>
                ))
              )}
            </section>
          </fieldset>
          {error && (
            <Alert
              variant="destructive"
              className="mt-4 border-destructive/25 bg-destructive/10"
            >
              <AlertDescription>{error}</AlertDescription>
            </Alert>
          )}
          <div className="flex flex-col-reverse sm:flex-row justify-end gap-3 mt-6 pt-4 border-t">
            <UiButton
              variant={'outline'}
              className="w-full sm:w-auto"
              type="button"
              disabled={busy}
              onClick={onClose}
            >
              Cancelar
            </UiButton>
            <UiButton
              variant="default"
              className="w-full sm:w-auto"
              type="button"
              disabled={
                busy || observations.some((note) => !note.content.trim())
              }
              onClick={handleConclude}
            >
              {busy ? 'Salvando...' : 'Salvar observações'}
            </UiButton>
          </div>
        </DialogContent>
      </Dialog>
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
