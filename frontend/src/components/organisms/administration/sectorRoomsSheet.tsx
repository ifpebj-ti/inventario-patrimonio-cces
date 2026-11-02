'use client'

import { useEffect, useState } from 'react'
import { Pencil, Plus, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { Button as UiButton } from '@/components/ui/button'
import { Input as UiInput } from '@/components/ui/input'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import type { Sector } from '@/commons/models/administration'
import {
  ROOM_NAME_MAX_LENGTH,
  roomNameSchema,
  type Room,
} from '@/commons/models/room'
import { useAdministrationAccess } from '@/contexts/AdministrationAccess'
import { administrationFailure } from '@/services/administration'
import {
  createRoom,
  deleteRoom,
  listSectorRooms,
  renameRoom,
} from '@/services/rooms'
import { AdminConfirmation, ErrorNotice, useOperation } from './shared'

const sameName = (a: string, b: string) =>
  a
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
    .toLocaleLowerCase() ===
  b
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
    .toLocaleLowerCase()

/**
 * Barra lateral das salas de um setor. Quem gerencia o setor (administrador ou gestor dele; a tela
 * só abre para eles) cadastra, renomeia e exclui salas. Sala com itens vinculados não é excluída.
 * Em setor com salas, validar um item passa a exigir informar a sala em que ele foi encontrado.
 */
export const SectorRoomsSheet = ({
  sector,
  onClose,
}: {
  sector: Sector
  onClose: () => void
}) => {
  const { refresh } = useAdministrationAccess()
  const [rooms, setRooms] = useState<Room[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [newName, setNewName] = useState('')
  const [formError, setFormError] = useState<string | null>(null)
  const [renaming, setRenaming] = useState<{ id: number; name: string } | null>(
    null,
  )
  const [removal, setRemoval] = useState<Room | null>(null)
  const { busy, run } = useOperation()
  const [reloadKey, setReloadKey] = useState(0)
  const reload = () => setReloadKey((key) => key + 1)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    listSectorRooms(sector.id)
      .then((rows) => {
        if (!cancelled) setRooms(rows)
      })
      .catch((failure) => {
        if (!cancelled) setError(administrationFailure(failure).message)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [sector.id, reloadKey])

  const validate = (name: string, ignoreId?: number) => {
    const parsed = roomNameSchema.safeParse(name)
    if (!parsed.success) return parsed.error.issues[0].message
    if (rooms.some((room) => room.id !== ignoreId && sameName(room.name, name)))
      return 'Já existe uma sala com este nome neste setor.'
    return null
  }
  const fail = async (failure: unknown, conflict?: string) => {
    const problem = administrationFailure(failure, conflict)
    toast.error(problem.message)
    if (problem.status === 403) await refresh()
    if (problem.status === 404 || problem.status === 409) reload()
  }
  const sorted = [...rooms].sort((a, b) =>
    a.name.localeCompare(b.name, 'pt-BR', { numeric: true }),
  )

  const add = () =>
    run(async () => {
      const problem = validate(newName)
      setFormError(problem)
      if (problem) return
      try {
        const created = await createRoom(sector.id, newName)
        setRooms((rows) => [...rows, created])
        setNewName('')
        toast.success('Sala cadastrada.')
      } catch (failure) {
        setFormError(administrationFailure(failure).message)
      }
    })
  const rename = () =>
    run(async () => {
      if (!renaming) return
      const problem = validate(renaming.name, renaming.id)
      setFormError(problem)
      if (problem) return
      try {
        const updated = await renameRoom(renaming.id, renaming.name)
        setRooms((rows) =>
          rows.map((row) => (row.id === updated.id ? updated : row)),
        )
        setRenaming(null)
        toast.success('Sala renomeada.')
      } catch (failure) {
        setFormError(administrationFailure(failure).message)
      }
    })
  const remove = () =>
    run(async () => {
      if (!removal) return
      try {
        await deleteRoom(removal.id)
        setRooms((rows) => rows.filter((row) => row.id !== removal.id))
        setRemoval(null)
        toast.success('Sala excluída.')
      } catch (failure) {
        setRemoval(null)
        await fail(
          failure,
          'A sala possui itens vinculados e não pode ser excluída.',
        )
      }
    })

  return (
    <>
      <Sheet
        open
        onOpenChange={(open) => {
          if (!open && !busy) onClose()
        }}
      >
        <SheetContent
          side="right"
          className="flex w-full flex-col gap-0 sm:max-w-md"
        >
          <SheetHeader className="border-b pr-12">
            <SheetTitle className="text-lg leading-snug">
              Salas de {sector.name}
            </SheetTitle>
            <SheetDescription>
              {rooms.length} {rooms.length === 1 ? 'sala' : 'salas'}. Com salas
              cadastradas, validar um item exige informar a sala em que ele foi
              encontrado.
            </SheetDescription>
            <form
              className="mt-2 flex gap-2"
              onSubmit={(event) => {
                event.preventDefault()
                add()
              }}
            >
              <UiInput
                aria-label="Nome da nova sala"
                placeholder="Nome da sala"
                value={newName}
                maxLength={ROOM_NAME_MAX_LENGTH}
                disabled={busy || loading || !!error}
                onChange={(event) => setNewName(event.target.value)}
              />
              <UiButton
                type="submit"
                disabled={busy || loading || !!error || !newName.trim()}
              >
                <Plus /> Adicionar
              </UiButton>
            </form>
            {formError && !renaming && (
              <p role="alert" className="text-sm text-destructive">
                {formError}
              </p>
            )}
          </SheetHeader>
          <div className="flex-1 space-y-2 overflow-y-auto px-4 py-4">
            <ErrorNotice message={error} retry={reload} />
            {loading ? (
              <p role="status">Carregando salas...</p>
            ) : (
              !error &&
              (sorted.length === 0 ? (
                <p className="text-sm text-muted-foreground">
                  Nenhuma sala cadastrada neste setor. As salas também são
                  criadas ao importar uma planilha com a coluna Sala.
                </p>
              ) : (
                <ul className="space-y-2">
                  {sorted.map((room) => (
                    <li
                      key={room.id}
                      className="flex flex-col gap-2 rounded-lg border p-3"
                    >
                      {renaming?.id === room.id ? (
                        <form
                          className="flex flex-col gap-2"
                          onSubmit={(event) => {
                            event.preventDefault()
                            rename()
                          }}
                        >
                          <UiInput
                            autoFocus
                            aria-label={`Novo nome da sala ${room.name}`}
                            value={renaming.name}
                            maxLength={ROOM_NAME_MAX_LENGTH}
                            disabled={busy}
                            onChange={(event) =>
                              setRenaming({
                                id: room.id,
                                name: event.target.value,
                              })
                            }
                          />
                          {formError && (
                            <p
                              role="alert"
                              className="text-sm text-destructive"
                            >
                              {formError}
                            </p>
                          )}
                          <div className="flex gap-2">
                            <UiButton
                              type="submit"
                              size="sm"
                              disabled={
                                busy ||
                                !renaming.name.trim() ||
                                renaming.name.trim() === room.name
                              }
                            >
                              Salvar
                            </UiButton>
                            <UiButton
                              type="button"
                              size="sm"
                              variant="outline"
                              disabled={busy}
                              onClick={() => {
                                setRenaming(null)
                                setFormError(null)
                              }}
                            >
                              Cancelar
                            </UiButton>
                          </div>
                        </form>
                      ) : (
                        <div className="flex items-center justify-between gap-2">
                          <div className="min-w-0">
                            <p className="font-medium break-words">
                              {room.name}
                            </p>
                            <p className="text-sm text-muted-foreground">
                              {room.itemCount}{' '}
                              {room.itemCount === 1 ? 'item' : 'itens'}
                            </p>
                          </div>
                          <div className="flex shrink-0 gap-1">
                            <UiButton
                              type="button"
                              variant="ghost"
                              size="icon-sm"
                              disabled={busy}
                              aria-label={`Renomear sala ${room.name}`}
                              onClick={() => {
                                setFormError(null)
                                setRenaming({ id: room.id, name: room.name })
                              }}
                            >
                              <Pencil />
                            </UiButton>
                            {room.deletable && (
                              <UiButton
                                type="button"
                                variant="ghost"
                                size="icon-sm"
                                disabled={busy}
                                aria-label={`Excluir sala ${room.name}`}
                                className="text-muted-foreground hover:bg-destructive/10 hover:text-destructive"
                                onClick={() => setRemoval(room)}
                              >
                                <Trash2 />
                              </UiButton>
                            )}
                          </div>
                        </div>
                      )}
                    </li>
                  ))}
                </ul>
              ))
            )}
          </div>
        </SheetContent>
      </Sheet>
      <AdminConfirmation
        isOpen={!!removal}
        onClose={() => setRemoval(null)}
        onConfirm={remove}
        busy={busy}
        title="Excluir sala"
        confirmLabel="Excluir"
        tone="destructive"
        message={
          <>
            Deseja excluir a sala{' '}
            <strong className="font-semibold">{removal?.name}</strong>?
          </>
        }
        details={[
          'Esta ação não pode ser desfeita.',
          'Só é possível excluir sala sem itens vinculados.',
        ]}
      />
    </>
  )
}
