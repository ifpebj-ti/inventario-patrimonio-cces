'use client'

import { useState } from 'react'
import { Building2, Plus, Search, TriangleAlert, X } from 'lucide-react'
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog'
import { Button as UiButton } from '@/components/ui/button'
import { Input as UiInput } from '@/components/ui/input'
import { Label as UiLabel } from '@/components/ui/label'
import { StatusBadge } from '@/components/ui/status-badge'
import { profileLabel } from '@/lib/access-labels'
import { cn } from '@/lib/utils'
import type {
  ManagedUser,
  Profile,
  Sector,
} from '@/commons/models/administration'
import { ErrorNotice } from './shared'

// O que o administrador escolheu e ainda não foi enviado: o perfil novo (se mudou) e os setores a
// incluir e a remover. É enviado de uma vez por "Salvar alterações".
export type AccessChanges = {
  profileId?: number
  allocate: Sector[]
  deallocate: Sector[]
}

type SectorRow = { id: number; status: 'keep' | 'remove' | 'add' }

export const UserAccessModal = ({
  user,
  initials,
  profiles,
  sectors,
  isSelf,
  canRemoveSector,
  toggleNote,
  busy,
  inactive,
  error,
  onClose,
  onSave,
  onToggleActive,
}: {
  user: ManagedUser
  initials: string
  // Perfis que quem está editando pode atribuir.
  profiles: Profile[]
  sectors: Sector[]
  isSelf: boolean
  canRemoveSector: (sectorId: number) => boolean
  // Quando ativar/desativar não é permitido, o motivo; senão null.
  toggleNote: string | null
  busy: boolean
  // Há uma confirmação aberta por cima: o conteúdo fica inerte.
  inactive: boolean
  error: string | null
  onClose: () => void
  onSave: (changes: AccessChanges) => void
  onToggleActive: () => void
}) => {
  const [desiredProfile, setDesiredProfile] = useState<number | null>(
    user.profileId,
  )
  const [desiredSectors, setDesiredSectors] = useState<number[]>(user.sectorIds)
  const [query, setQuery] = useState('')
  const [picked, setPicked] = useState<number | null>(null)

  const sectorById = (id: number) => sectors.find((row) => row.id === id)
  const profileChanged =
    desiredProfile !== null && desiredProfile !== user.profileId
  const allocate = desiredSectors
    .filter((id) => !user.sectorIds.includes(id))
    .map(sectorById)
    .filter((sector): sector is Sector => !!sector)
  const deallocate = user.sectorIds
    .filter((id) => !desiredSectors.includes(id))
    .map(sectorById)
    .filter((sector): sector is Sector => !!sector)
  const pending = (profileChanged ? 1 : 0) + allocate.length + deallocate.length

  const rows: SectorRow[] = [
    ...user.sectorIds.map((id) => ({
      id,
      status: desiredSectors.includes(id)
        ? ('keep' as const)
        : ('remove' as const),
    })),
    ...desiredSectors
      .filter((id) => !user.sectorIds.includes(id))
      .map((id) => ({ id, status: 'add' as const })),
  ]

  const search = query.trim().toLocaleLowerCase()
  const results = sectors
    .filter(
      (sector) =>
        sector.active &&
        !user.sectorIds.includes(sector.id) &&
        !desiredSectors.includes(sector.id) &&
        search !== '' &&
        `${sector.name} ${sector.code ?? ''}`
          .toLocaleLowerCase()
          .includes(search),
    )
    .slice(0, 6)
  const hasAvailable = sectors.some(
    (sector) =>
      sector.active &&
      !user.sectorIds.includes(sector.id) &&
      !desiredSectors.includes(sector.id),
  )

  const addPicked = (id: number | null) => {
    if (id === null) return
    setDesiredSectors((ids) => (ids.includes(id) ? ids : [...ids, id]))
    setQuery('')
    setPicked(null)
  }
  const setStatus = (id: number, status: SectorRow['status']) =>
    setDesiredSectors((ids) =>
      status === 'remove'
        ? ids.filter((value) => value !== id)
        : ids.includes(id)
          ? ids
          : [...ids, id],
    )

  const save = () =>
    onSave({
      profileId: profileChanged ? (desiredProfile as number) : undefined,
      allocate,
      deallocate,
    })

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && !(busy || inactive)) onClose()
      }}
    >
      <DialogContent
        showCloseButton={false}
        className="flex max-h-[92vh] flex-col gap-0 overflow-hidden rounded-xl p-0 sm:max-w-lg"
      >
        <header className="flex shrink-0 items-start gap-3 border-b px-6 py-4">
          <div className="flex size-11 shrink-0 items-center justify-center rounded-full bg-primary/10 text-sm font-semibold text-primary">
            {initials || '?'}
          </div>
          <div className="min-w-0 flex-1 space-y-0.5">
            <div className="flex flex-wrap items-center gap-2">
              <DialogTitle className="text-lg leading-snug">
                <span className="sr-only">Acesso de </span>
                {user.name}
              </DialogTitle>
              <StatusBadge tone={user.active ? 'success' : 'neutral'}>
                {user.active ? 'Ativo' : 'Inativo'}
              </StatusBadge>
            </div>
            <p className="text-sm break-all text-muted-foreground">
              {user.email}
            </p>
          </div>
          <UiButton
            variant="ghost"
            size="icon"
            disabled={busy || inactive}
            onClick={onClose}
            aria-label="Fechar diálogo"
          >
            <X />
          </UiButton>
        </header>

        <div
          inert={inactive}
          className="flex-1 space-y-6 overflow-y-auto px-6 py-5"
        >
          <ErrorNotice message={error} />

          <section className="space-y-3">
            <div>
              <h3 className="font-semibold">Perfil de acesso</h3>
              <p className="text-sm text-muted-foreground">
                Define o que o usuário pode ver e fazer no sistema.
              </p>
            </div>
            <fieldset className="space-y-2" disabled={busy}>
              <legend className="sr-only">Perfil do usuário</legend>
              {profiles.map((profile) => {
                const selected = desiredProfile === profile.id
                return (
                  <label
                    key={profile.id}
                    className={cn(
                      'flex cursor-pointer items-start gap-3 rounded-lg border p-3 transition-colors',
                      selected
                        ? 'border-primary bg-primary/5 ring-1 ring-primary'
                        : 'hover:bg-muted/50',
                    )}
                  >
                    <input
                      type="radio"
                      name="access-profile"
                      value={profile.id}
                      checked={selected}
                      onChange={() => setDesiredProfile(profile.id)}
                      className="mt-1 size-4 accent-primary"
                    />
                    <span className="min-w-0 space-y-0.5">
                      <span className="flex flex-wrap items-center gap-2 font-medium">
                        {profileLabel(profile.name)}
                        {profile.id === user.profileId && (
                          <StatusBadge tone="primary">Perfil atual</StatusBadge>
                        )}
                      </span>
                      {profile.description && (
                        <span className="block text-sm text-muted-foreground">
                          {profile.description}
                        </span>
                      )}
                    </span>
                  </label>
                )
              })}
            </fieldset>
            {user.profileId === null && (
              <p className="text-sm text-muted-foreground">
                Este usuário ainda não tem perfil: não tem permissões até você
                escolher um.
              </p>
            )}
            {isSelf && (
              <p className="flex items-start gap-2 text-sm text-warning">
                <TriangleAlert className="mt-0.5 size-4 shrink-0" />
                Alterar seu próprio perfil pode remover seu acesso às telas
                administrativas.
              </p>
            )}
          </section>

          <section className="space-y-3 border-t pt-5">
            <div>
              <h3 className="font-semibold">
                Setores associados{' '}
                <span className="font-normal text-muted-foreground">
                  ({desiredSectors.length})
                </span>
              </h3>
              <p className="text-sm text-muted-foreground">
                O usuário só vê os inventários dos setores em que estiver
                alocado.
              </p>
            </div>
            {rows.length === 0 ? (
              <p className="flex items-center gap-2 rounded-lg border border-dashed px-3 py-3 text-sm text-muted-foreground">
                <Building2 className="size-4 shrink-0" />
                Nenhum setor associado.
              </p>
            ) : (
              <ul className="space-y-2">
                {rows.map(({ id, status }) => {
                  const sector = sectorById(id)
                  const name = sector?.name || `Setor #${id}`
                  return (
                    <li
                      key={id}
                      className={cn(
                        'flex items-center justify-between gap-2 rounded-lg border px-3 py-1.5 text-sm',
                        status === 'remove' &&
                          'border-destructive/30 bg-destructive/5',
                        status === 'add' && 'border-primary/40 bg-primary/5',
                      )}
                    >
                      <span className="flex min-w-0 flex-wrap items-center gap-2">
                        <Building2 className="size-4 shrink-0 text-muted-foreground" />
                        <span
                          className={cn(
                            status === 'remove' &&
                              'text-muted-foreground line-through',
                          )}
                        >
                          {name}
                        </span>
                        {sector && !sector.active && (
                          <StatusBadge tone="neutral">Inativo</StatusBadge>
                        )}
                        {status === 'add' && (
                          <StatusBadge tone="primary">Novo</StatusBadge>
                        )}
                        {status === 'remove' && (
                          <StatusBadge tone="danger">Será removido</StatusBadge>
                        )}
                      </span>
                      {status === 'keep' ? (
                        <UiButton
                          variant="ghost"
                          size="sm"
                          type="button"
                          className="text-muted-foreground hover:bg-destructive/10 hover:text-destructive"
                          disabled={busy || !sector || !canRemoveSector(id)}
                          onClick={() => setStatus(id, 'remove')}
                          aria-label={`Desalocar de ${name}`}
                        >
                          Desalocar
                        </UiButton>
                      ) : (
                        <UiButton
                          variant="ghost"
                          size="sm"
                          type="button"
                          disabled={busy}
                          onClick={() =>
                            status === 'add'
                              ? setStatus(id, 'remove')
                              : setStatus(id, 'add')
                          }
                          aria-label={`Desfazer ${name}`}
                        >
                          Desfazer
                        </UiButton>
                      )}
                    </li>
                  )
                })}
              </ul>
            )}

            <div className="space-y-2">
              <UiLabel
                htmlFor="access-sector-search"
                className="flex-col items-stretch gap-1.5 text-sm"
              >
                Adicionar setor
              </UiLabel>
              <div className="flex gap-2">
                <div className="relative min-w-0 flex-1">
                  <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
                  <UiInput
                    id="access-sector-search"
                    className="pl-9"
                    placeholder="Buscar setor ativo pelo nome ou sigla"
                    autoComplete="off"
                    disabled={busy}
                    value={query}
                    onChange={(event) => {
                      setQuery(event.target.value)
                      setPicked(null)
                    }}
                    onKeyDown={(event) => {
                      if (event.key !== 'Enter') return
                      event.preventDefault()
                      if (picked !== null) addPicked(picked)
                      else if (results.length === 1) addPicked(results[0].id)
                    }}
                  />
                </div>
                <UiButton
                  type="button"
                  variant="outline"
                  className="border-primary/40 text-primary"
                  disabled={busy || picked === null}
                  onClick={() => addPicked(picked)}
                >
                  <Plus /> Adicionar
                </UiButton>
              </div>
              {results.length > 0 && (
                <ul
                  role="listbox"
                  aria-label="Setores encontrados"
                  className="max-h-44 overflow-y-auto rounded-lg border"
                >
                  {results.map((sector) => (
                    <li
                      key={sector.id}
                      role="option"
                      aria-selected={picked === sector.id}
                    >
                      <button
                        type="button"
                        className={cn(
                          'flex w-full items-center justify-between gap-2 px-3 py-2 text-left text-sm hover:bg-muted/60',
                          picked === sector.id && 'bg-primary/10 font-medium',
                        )}
                        onClick={() => {
                          setPicked(sector.id)
                          setQuery(sector.name)
                        }}
                      >
                        <span>{sector.name}</span>
                        {sector.code && (
                          <span className="text-xs text-muted-foreground">
                            {sector.code}
                          </span>
                        )}
                      </button>
                    </li>
                  ))}
                </ul>
              )}
              {search !== '' && results.length === 0 && picked === null && (
                <p className="text-sm text-muted-foreground">
                  Nenhum setor ativo encontrado para esta busca.
                </p>
              )}
              {!hasAvailable && (
                <p className="text-sm text-muted-foreground">
                  Não há outros setores ativos disponíveis.
                </p>
              )}
            </div>
          </section>

          <section
            className={cn(
              'space-y-2 rounded-lg border p-4',
              user.active
                ? 'border-destructive/30 bg-destructive/5'
                : 'border-muted bg-muted/30',
            )}
          >
            {toggleNote ? (
              <>
                <h3 className="font-semibold">Situação da conta</h3>
                <p className="text-sm text-muted-foreground">{toggleNote}</p>
              </>
            ) : (
              <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div className="space-y-1">
                  <h3
                    className={cn(
                      'font-semibold',
                      user.active && 'text-destructive',
                    )}
                  >
                    {user.active ? 'Desativar conta' : 'Reativar conta'}
                  </h3>
                  <p className="text-sm text-muted-foreground">
                    {user.active
                      ? 'Bloqueia o acesso imediatamente. O histórico é preservado e a conta pode ser reativada.'
                      : 'Conta desativada: o usuário não consegue entrar no sistema.'}
                  </p>
                </div>
                <UiButton
                  variant="outline"
                  type="button"
                  disabled={busy}
                  className={cn(
                    'shrink-0',
                    user.active &&
                      'border-destructive/40 text-destructive hover:bg-destructive/10 hover:text-destructive',
                  )}
                  onClick={onToggleActive}
                >
                  {user.active ? 'Desativar usuário…' : 'Reativar usuário…'}
                </UiButton>
              </div>
            )}
          </section>
        </div>

        <footer className="flex shrink-0 flex-col gap-3 border-t bg-muted/40 px-6 py-4 sm:flex-row sm:items-center sm:justify-between">
          <p role="status" className="text-sm text-muted-foreground">
            {pending === 0
              ? 'Nenhuma alteração pendente'
              : `${pending} ${pending === 1 ? 'alteração pendente' : 'alterações pendentes'}`}
          </p>
          <div className="flex flex-col-reverse gap-2 sm:flex-row">
            <UiButton
              variant="outline"
              type="button"
              disabled={busy}
              onClick={onClose}
            >
              Cancelar
            </UiButton>
            <UiButton
              type="button"
              disabled={busy || pending === 0}
              onClick={save}
            >
              {busy ? 'Salvando...' : 'Salvar alterações'}
            </UiButton>
          </div>
        </footer>
      </DialogContent>
    </Dialog>
  )
}
