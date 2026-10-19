'use client'

import { useEffect, useState } from 'react'
import { UserPlus, X } from 'lucide-react'
import { toast } from 'sonner'
import { Button as UiButton } from '@/components/ui/button'
import { SelectField } from '@/components/ui/select-field'
import { StatusBadge } from '@/components/ui/status-badge'
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet'
import type { ManagedUser, Role, Sector } from '@/commons/models/administration'
import { useAdministrationAccess } from '@/contexts/AdministrationAccess'
import {
  administrationFailure,
  listRoles,
  listSectorUsers,
  setUserSector,
} from '@/services/administration'
import { AdminConfirmation, ErrorNotice, useOperation } from './shared'
import { AllocateUsersModal } from './allocateUsersModal'

type Group = { roleId: number; roleName: string; users: ManagedUser[] }

const MemberRow = ({
  user,
  roleId,
  options,
  editable,
  busy,
  onRoleChange,
  onRemove,
}: {
  user: ManagedUser
  roleId: number
  roleName: string
  options: { value: string; label: string }[]
  editable: boolean
  busy: boolean
  onRoleChange: (value: string) => void
  onRemove: () => void
}) => (
  <li className="flex flex-col gap-2 rounded-lg border p-3">
    <div className="flex items-start justify-between gap-2">
      <div className="min-w-0">
        <p className="flex flex-wrap items-center gap-2 font-medium">
          {user.name}
          {!user.active && <StatusBadge tone="neutral">Inativo</StatusBadge>}
          {user.administrator && (
            <StatusBadge tone="primary">Administrador</StatusBadge>
          )}
        </p>
        <p className="text-sm break-all text-muted-foreground">{user.email}</p>
      </div>
      {editable && (
        <button
          type="button"
          className="flex size-8 shrink-0 items-center justify-center rounded-full text-muted-foreground hover:bg-destructive/10 hover:text-destructive disabled:pointer-events-none disabled:opacity-40"
          disabled={busy}
          onClick={onRemove}
          aria-label={`Remover ${user.name} do setor`}
        >
          <X className="size-4" />
        </button>
      )}
    </div>
    {editable && (
      <SelectField
        aria-label={`Cargo de ${user.name}`}
        options={options}
        value={String(roleId)}
        onValueChange={onRoleChange}
        disabled={busy}
      />
    )}
  </li>
)

/**
 * Barra lateral dos usuários de um setor, agrupados por cargo. Quem gerencia o setor (administrador
 * ou gestor dele; a tela só abre para eles) adiciona usuários, troca o cargo e remove.
 */
export const SectorMembersSheet = ({
  sector,
  onClose,
  onChanged,
}: {
  sector: Sector
  onClose: () => void
  // Chamado depois de alocar, trocar cargo ou remover (a lista de setores muda `deletable`).
  onChanged: () => void
}) => {
  const { isAdmin, refresh } = useAdministrationAccess()
  const [members, setMembers] = useState<ManagedUser[]>([])
  const [roles, setRoles] = useState<Role[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [allocating, setAllocating] = useState(false)
  const [removal, setRemoval] = useState<ManagedUser | null>(null)
  const { busy, run } = useOperation()
  // Mudar a chave recarrega a lista; uma resposta que chega depois de trocar de setor ou de
  // recarregar é descartada pelo `cancelled`.
  const [reloadKey, setReloadKey] = useState(0)
  const reload = () => setReloadKey((key) => key + 1)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    Promise.all([listSectorUsers(sector.id), listRoles()])
      .then(([users, roleRows]) => {
        if (cancelled) return
        setMembers(users)
        setRoles(roleRows)
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

  const roleIn = (user: ManagedUser) =>
    user.allocations.find((allocation) => allocation.sectorId === sector.id)
  const isManagement = (roleId: number) =>
    roles.find((role) => role.id === roleId)?.management ?? false
  // O gestor não atribui cargo de gestão nem mexe em outro gestor ou administrador (a API recusaria).
  const assignable = isAdmin ? roles : roles.filter((role) => !role.management)
  const canEdit = (user: ManagedUser) => {
    const allocation = roleIn(user)
    return (
      !!allocation &&
      (isAdmin || (!user.administrator && !isManagement(allocation.roleId)))
    )
  }

  const groups: Group[] = []
  for (const user of members) {
    const allocation = roleIn(user)
    if (!allocation) continue
    let group = groups.find((row) => row.roleId === allocation.roleId)
    if (!group) {
      group = {
        roleId: allocation.roleId,
        roleName: allocation.roleName,
        users: [],
      }
      groups.push(group)
    }
    group.users.push(user)
  }
  const order = (roleId: number) => {
    const index = roles.findIndex((role) => role.id === roleId)
    return index === -1 ? roles.length : index
  }
  groups.sort((a, b) => order(a.roleId) - order(b.roleId))
  groups.forEach((group) =>
    group.users.sort((a, b) => a.name.localeCompare(b.name, 'pt-BR')),
  )

  const fail = async (failure: unknown) => {
    const problem = administrationFailure(failure)
    toast.error(problem.message)
    if (problem.status === 403) await refresh()
    if (problem.status === 404 || problem.status === 409) reload()
  }
  const changeRole = (user: ManagedUser, roleId: number) =>
    run(async () => {
      try {
        const updated = await setUserSector(user.id, sector.id, roleId)
        setMembers((rows) =>
          rows.map((row) => (row.id === updated.id ? updated : row)),
        )
        toast.success('Cargo alterado.')
        onChanged()
      } catch (failure) {
        await fail(failure)
      }
    })
  const onRoleChange = (user: ManagedUser, current: number, value: string) => {
    if (Number(value) !== current) changeRole(user, Number(value))
  }
  // Cargos que o seletor oferece; o atual entra mesmo que quem edita não possa atribuí-lo.
  const roleOptions = (group: Group) =>
    assignable
      .map((role) => ({ value: String(role.id), label: role.name }))
      .concat(
        assignable.some((role) => role.id === group.roleId)
          ? []
          : [{ value: String(group.roleId), label: group.roleName }],
      )
  const remove = () =>
    run(async () => {
      if (!removal) return
      try {
        await setUserSector(removal.id, sector.id, null)
        setMembers((rows) => rows.filter((row) => row.id !== removal.id))
        setRemoval(null)
        toast.success('Usuário removido do setor.')
        onChanged()
      } catch (failure) {
        setRemoval(null)
        await fail(failure)
      }
    })

  const total = groups.reduce((sum, group) => sum + group.users.length, 0)
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
              Usuários de {sector.name}
            </SheetTitle>
            <SheetDescription>
              {total} {total === 1 ? 'usuário' : 'usuários'}, agrupados por
              cargo.
            </SheetDescription>
            <UiButton
              type="button"
              className="mt-2 w-fit"
              disabled={busy || loading || !!error}
              onClick={() => setAllocating(true)}
            >
              <UserPlus /> Adicionar usuários
            </UiButton>
          </SheetHeader>
          <div className="flex-1 space-y-5 overflow-y-auto px-4 py-4">
            <ErrorNotice message={error} retry={reload} />
            {loading ? (
              <p role="status">Carregando usuários...</p>
            ) : (
              !error &&
              (groups.length === 0 ? (
                <p className="text-sm text-muted-foreground">
                  Nenhum usuário associado a este setor.
                </p>
              ) : (
                groups.map((group) => (
                  <section key={group.roleId} className="space-y-2">
                    <h3 className="text-sm font-semibold">
                      {group.roleName}{' '}
                      <span className="font-normal text-muted-foreground">
                        ({group.users.length})
                      </span>
                    </h3>
                    <ul className="space-y-2">
                      {group.users.map((user) => (
                        <MemberRow
                          key={user.id}
                          user={user}
                          roleName={group.roleName}
                          roleId={group.roleId}
                          options={roleOptions(group)}
                          editable={canEdit(user)}
                          busy={busy}
                          onRoleChange={(value) =>
                            onRoleChange(user, group.roleId, value)
                          }
                          onRemove={() => setRemoval(user)}
                        />
                      ))}
                    </ul>
                  </section>
                ))
              ))
            )}
          </div>
        </SheetContent>
      </Sheet>
      {allocating && (
        <AllocateUsersModal
          sector={sector}
          roles={assignable}
          onClose={() => setAllocating(false)}
          onAllocated={(updated) => {
            setMembers(updated)
            setAllocating(false)
            onChanged()
          }}
        />
      )}
      <AdminConfirmation
        isOpen={!!removal}
        onClose={() => setRemoval(null)}
        onConfirm={remove}
        busy={busy}
        title="Remover do setor"
        confirmLabel="Remover"
        tone="destructive"
        message={
          <>
            Deseja remover{' '}
            <strong className="font-semibold">{removal?.name}</strong> do setor{' '}
            <strong className="font-semibold">{sector.name}</strong>?
          </>
        }
        details={[
          'O usuário perde o acesso a este setor e aos inventários dele.',
          'Os outros setores do usuário não mudam.',
        ]}
      />
    </>
  )
}
