'use client'

import { useState } from 'react'
import { Building2, RotateCcw, TriangleAlert, X } from 'lucide-react'
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog'
import { Button as UiButton } from '@/components/ui/button'
import { Label as UiLabel } from '@/components/ui/label'
import { StatusBadge } from '@/components/ui/status-badge'
import { Checkbox as UiCheckbox } from '@/components/ui/checkbox'
import { SelectField } from '@/components/ui/select-field'
import { cn } from '@/lib/utils'
import type { ManagedUser, Role, Sector } from '@/commons/models/administration'
import { ErrorNotice } from './shared'
import { SectorPicker } from './sectorPicker'

// O que o administrador escolheu e ainda não foi enviado: o papel de administrador (se mudou), os
// setores a alocar ou com cargo trocado (upsert) e os setores a remover. É enviado de uma vez por
// "Salvar alterações".
export type AccessChanges = {
  administrator?: boolean
  upsert: { sector: Sector; roleId: number }[]
  deallocate: Sector[]
}

type SectorRow = { id: number; status: 'keep' | 'remove' | 'add' }

export const UserAccessModal = ({
  user,
  initials,
  roles,
  sectors,
  isSelf,
  canGrantAdministrator,
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
  roles: Role[]
  sectors: Sector[]
  isSelf: boolean
  // Só o administrador concede o papel, e nunca na própria conta.
  canGrantAdministrator: boolean
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
  const originalRoles: Record<number, number> = Object.fromEntries(
    user.allocations.map((allocation) => [
      allocation.sectorId,
      allocation.roleId,
    ]),
  )
  const [desiredAdministrator, setDesiredAdministrator] = useState(
    user.administrator,
  )
  const [desiredRoles, setDesiredRoles] =
    useState<Record<number, number>>(originalRoles)
  const desiredSectors = Object.keys(desiredRoles).map(Number)

  const sectorById = (id: number) => sectors.find((row) => row.id === id)
  const administratorChanged = desiredAdministrator !== user.administrator
  const upsert = desiredSectors
    .filter(
      (id) =>
        !user.sectorIds.includes(id) || desiredRoles[id] !== originalRoles[id],
    )
    .map((id) => ({ sector: sectorById(id), roleId: desiredRoles[id] }))
    .filter(
      (entry): entry is { sector: Sector; roleId: number } => !!entry.sector,
    )
  const deallocate = user.sectorIds
    .filter((id) => !desiredSectors.includes(id))
    .map(sectorById)
    .filter((sector): sector is Sector => !!sector)
  const pending =
    (administratorChanged ? 1 : 0) + upsert.length + deallocate.length

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

  // Cargo de menos permissões: é o padrão ao incluir um setor (o admin ajusta na linha).
  const defaultRole = [...roles].sort(
    (a, b) => a.permissionIds.length - b.permissionIds.length || a.id - b.id,
  )[0]
  const roleOptions = (current?: number) =>
    roles
      .map((role) => ({ value: String(role.id), label: role.name }))
      .concat(
        current && !roles.some((role) => role.id === current)
          ? [{ value: String(current), label: `Cargo #${current}` }]
          : [],
      )

  // Setores que ainda podem ser adicionados: ativos e fora da lista (nem pendentes de inclusão).
  const available = sectors.filter(
    (sector) =>
      sector.active &&
      !user.sectorIds.includes(sector.id) &&
      !desiredSectors.includes(sector.id),
  )
  const addSector = (sector: Sector) => {
    if (!defaultRole) return
    setDesiredRoles((current) =>
      sector.id in current
        ? current
        : { ...current, [sector.id]: defaultRole.id },
    )
  }
  const setRole = (id: number, roleId: number) =>
    setDesiredRoles((current) => ({ ...current, [id]: roleId }))
  const setStatus = (id: number, status: SectorRow['status']) =>
    setDesiredRoles((current) => {
      if (status === 'remove') {
        const rest = { ...current }
        delete rest[id]
        return rest
      }
      return id in current
        ? current
        : { ...current, [id]: originalRoles[id] ?? defaultRole?.id }
    })

  const save = () =>
    onSave({
      administrator: administratorChanged ? desiredAdministrator : undefined,
      upsert,
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
        className="flex max-h-[92vh] flex-col gap-0 overflow-hidden rounded-xl p-0 sm:max-w-[40rem]"
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
              <h3 className="font-semibold">Administrador</h3>
              <p className="text-sm text-muted-foreground">
                O administrador tem acesso total, em todos os setores, sem
                depender de cargo.
              </p>
            </div>
            <UiLabel
              className={cn(
                'items-start gap-3 rounded-lg border p-3 text-sm',
                desiredAdministrator && 'border-primary bg-primary/5',
              )}
            >
              <UiCheckbox
                checked={desiredAdministrator}
                disabled={busy || !canGrantAdministrator}
                onCheckedChange={(checked) =>
                  setDesiredAdministrator(checked === true)
                }
                aria-label="Administrador"
              />
              <span className="min-w-0 space-y-0.5">
                <span className="flex flex-wrap items-center gap-2 font-medium">
                  Administrador
                  {user.administrator && (
                    <StatusBadge tone="primary">Atual</StatusBadge>
                  )}
                </span>
                <span className="block font-normal text-muted-foreground">
                  {canGrantAdministrator
                    ? 'Vê e gerencia tudo: setores, usuários, cargos e inventários.'
                    : 'Você não pode alterar o seu próprio papel de administrador.'}
                </span>
              </span>
            </UiLabel>
            {isSelf && desiredSectors.length !== user.sectorIds.length && (
              <p className="flex items-start gap-2 text-sm text-warning">
                <TriangleAlert className="mt-0.5 size-4 shrink-0" />
                Você está alterando as suas próprias alocações.
              </p>
            )}
          </section>

          <section className="space-y-3 border-t pt-5">
            <div>
              <h3 className="font-semibold">
                Setores e cargos{' '}
                <span className="font-normal text-muted-foreground">
                  ({desiredSectors.length})
                </span>
              </h3>
              <p className="text-sm text-muted-foreground">
                O cargo vale só no setor em que o usuário está alocado: ele pode
                ser gestor em um setor e só consulta em outro.
              </p>
            </div>
            {rows.length === 0 ? (
              <p className="flex items-center gap-2 rounded-lg border border-dashed px-3 py-3 text-sm text-muted-foreground">
                <Building2 className="size-4 shrink-0" />
                Nenhum setor associado: sem acesso a nenhum inventário.
              </p>
            ) : (
              <ul className="space-y-2">
                {rows.map(({ id, status }) => {
                  const sector = sectorById(id)
                  const name = sector?.name || `Setor #${id}`
                  const roleId = desiredRoles[id] ?? originalRoles[id]
                  const roleChanged =
                    status === 'keep' && desiredRoles[id] !== originalRoles[id]
                  return (
                    <li
                      key={id}
                      className={cn(
                        'flex flex-col gap-2 rounded-lg border p-3 sm:flex-row sm:items-center',
                        status === 'remove'
                          ? 'border-destructive/30 bg-destructive/5 text-muted-foreground'
                          : 'border-primary/30 bg-primary/5 text-foreground',
                      )}
                    >
                      <div className="flex min-w-0 flex-1 flex-wrap items-center gap-2">
                        <span
                          className={cn(
                            'min-w-0 truncate font-medium',
                            status === 'remove' && 'line-through',
                          )}
                        >
                          {name}
                        </span>
                        {sector && !sector.active && (
                          <span className="text-xs text-muted-foreground">
                            Inativo
                          </span>
                        )}
                        {status === 'add' && (
                          <span className="text-xs font-medium text-primary">
                            Novo
                          </span>
                        )}
                        {roleChanged && (
                          <span className="text-xs font-medium text-primary">
                            Cargo alterado
                          </span>
                        )}
                        {status === 'remove' && (
                          <span className="text-xs font-medium text-destructive">
                            Será removido
                          </span>
                        )}
                      </div>
                      <div className="flex items-center gap-2">
                        <div className="w-full sm:w-52">
                          <SelectField
                            aria-label={`Cargo em ${name}`}
                            options={roleOptions(roleId)}
                            value={roleId ? String(roleId) : ''}
                            onValueChange={(value) =>
                              setRole(id, Number(value))
                            }
                            placeholder="Selecione o cargo"
                            disabled={busy || status === 'remove'}
                          />
                        </div>
                        {status === 'keep' ? (
                          <button
                            type="button"
                            className="flex size-8 shrink-0 items-center justify-center rounded-full text-muted-foreground hover:bg-destructive/10 hover:text-destructive disabled:pointer-events-none disabled:opacity-40"
                            disabled={busy || !sector}
                            onClick={() => setStatus(id, 'remove')}
                            aria-label={`Desalocar de ${name}`}
                          >
                            <X className="size-4" />
                          </button>
                        ) : (
                          <button
                            type="button"
                            className="flex size-8 shrink-0 items-center justify-center rounded-full text-muted-foreground hover:bg-muted hover:text-foreground disabled:pointer-events-none disabled:opacity-40"
                            disabled={busy}
                            onClick={() =>
                              setStatus(id, status === 'add' ? 'remove' : 'add')
                            }
                            aria-label={`Desfazer ${name}`}
                          >
                            {status === 'add' ? (
                              <X className="size-4" />
                            ) : (
                              <RotateCcw className="size-4" />
                            )}
                          </button>
                        )}
                      </div>
                    </li>
                  )
                })}
              </ul>
            )}

            <div className="space-y-1.5">
              <UiLabel
                htmlFor="access-sector-search"
                className="flex-col items-stretch gap-1.5 text-sm"
              >
                Adicionar setor
              </UiLabel>
              <SectorPicker
                id="access-sector-search"
                available={available}
                disabled={busy}
                onPick={addSector}
              />
              <p className="text-xs text-muted-foreground">
                O setor entra com o cargo de menos permissões; ajuste o cargo na
                linha dele.
              </p>
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
