'use client'

import { useState } from 'react'
import Link from 'next/link'
import { Building2, X } from 'lucide-react'
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog'
import { Button as UiButton } from '@/components/ui/button'
import { Checkbox as UiCheckbox } from '@/components/ui/checkbox'
import { Label as UiLabel } from '@/components/ui/label'
import { StatusBadge } from '@/components/ui/status-badge'
import { cn } from '@/lib/utils'
import type { ManagedUser, Sector } from '@/commons/models/administration'
import { ErrorNotice } from './shared'

// O que o administrador escolheu e ainda não foi enviado: o papel de administrador (se mudou). Os
// setores e cargos do usuário não se alteram aqui: são geridos em Setores → Usuários associados.
export type AccessChanges = {
  administrator?: boolean
}

export const UserAccessModal = ({
  user,
  initials,
  sectors,
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
  sectors: Sector[]
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
  const [desiredAdministrator, setDesiredAdministrator] = useState(
    user.administrator,
  )
  const administratorChanged = desiredAdministrator !== user.administrator
  const pending = administratorChanged ? 1 : 0
  const allocations = [...user.allocations].sort(
    (a, b) => a.sectorId - b.sectorId,
  )

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
          </section>

          <section className="space-y-3 border-t pt-5">
            <div>
              <h3 className="font-semibold">
                Setores e cargos{' '}
                <span className="font-normal text-muted-foreground">
                  ({allocations.length})
                </span>
              </h3>
              <p className="text-sm text-muted-foreground">
                O cargo vale só no setor em que o usuário está alocado. Para
                alocar, trocar o cargo ou remover, use{' '}
                <Link
                  href="/sectors"
                  className="font-semibold text-primary underline"
                >
                  Setores → Usuários associados
                </Link>
                .
              </p>
            </div>
            {allocations.length === 0 ? (
              <p className="flex items-center gap-2 rounded-lg border border-dashed px-3 py-3 text-sm text-muted-foreground">
                <Building2 className="size-4 shrink-0" />
                Nenhum setor associado: sem acesso a nenhum inventário.
              </p>
            ) : (
              <ul className="space-y-2">
                {allocations.map((allocation) => {
                  const sector = sectors.find(
                    (row) => row.id === allocation.sectorId,
                  )
                  return (
                    <li
                      key={allocation.sectorId}
                      className="flex flex-wrap items-center justify-between gap-2 rounded-lg border p-3 text-sm"
                    >
                      <span className="min-w-0 truncate font-medium">
                        {sector?.name || `Setor #${allocation.sectorId}`}
                        {sector && !sector.active && (
                          <span className="ml-2 text-xs font-normal text-muted-foreground">
                            Inativo
                          </span>
                        )}
                      </span>
                      <StatusBadge tone="primary">
                        {allocation.roleName}
                      </StatusBadge>
                    </li>
                  )
                })}
              </ul>
            )}
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
              : '1 alteração pendente'}
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
              onClick={() =>
                onSave({
                  administrator: administratorChanged
                    ? desiredAdministrator
                    : undefined,
                })
              }
            >
              {busy ? 'Salvando...' : 'Salvar alterações'}
            </UiButton>
          </div>
        </footer>
      </DialogContent>
    </Dialog>
  )
}
