'use client'

import { SelectField } from '@/components/ui/select-field'
import { StatusBadge } from '@/components/ui/status-badge'
import { ListFilters } from '@/components/organisms/listFilters'
import { Button as UiButton } from '@/components/ui/button'
import { Label as UiLabel } from '@/components/ui/label'
import { Input as UiInput } from '@/components/ui/input'

import { useEffect, useRef, useState } from 'react'
import { toast } from 'sonner'
import type { ManagedUser, Role, Sector } from '@/commons/models/administration'
import { useAuth } from '@/hooks/useAuth'
import { useAdministrationAccess } from '@/contexts/AdministrationAccess'
import {
  administrationFailure,
  listRoles,
  listSectors,
  listUsers,
  setUserActive,
  setUserAdministrator,
  setUserSector,
} from '@/services/administration'
import {
  AdminConfirmation,
  AdminPage,
  ErrorNotice,
  useOperation,
} from './shared'
import { UserAccessModal, type AccessChanges } from './userAccessModal'

const initialsOf = (name: string) =>
  name
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('')

// Ativar/desativar sempre pede confirmação (reativar pela lista não).
type UserAction = { type: 'active'; user: ManagedUser; active: boolean }

export const UsersScreen = () => {
  const { user: actor } = useAuth()
  const access = useAdministrationAccess()
  const [users, setUsers] = useState<ManagedUser[]>([])
  const [roles, setRoles] = useState<Role[]>([])
  const [sectors, setSectors] = useState<Sector[]>([])
  const [sectorFilter, setSectorFilter] = useState('all')
  const [activeFilter, setActiveFilter] = useState('all')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [filter, setFilter] = useState('all')
  const [reload, setReload] = useState(0)
  const [editing, setEditing] = useState<ManagedUser | null>(null)
  const [editorError, setEditorError] = useState<string | null>(null)
  const [confirmation, setConfirmation] = useState<UserAction | null>(null)
  const { busy, run } = useOperation()
  const requestVersion = useRef(0)

  useEffect(() => {
    const controller = new AbortController()
    const request = ++requestVersion.current
    setLoading(true)
    setError(null)
    Promise.all([
      listUsers(
        filter === 'all' || filter === 'none' || filter === 'admin'
          ? undefined
          : Number(filter),
        controller.signal,
      ),
      listRoles(controller.signal),
      listSectors(controller.signal),
    ])
      .then(([userRows, roleRows, sectorRows]) => {
        if (request !== requestVersion.current || controller.signal.aborted)
          return
        setUsers(userRows)
        setRoles(roleRows)
        setSectors(sectorRows)
      })
      .catch((failure) => {
        if (!controller.signal.aborted)
          setError(administrationFailure(failure).message)
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => {
      controller.abort()
      requestVersion.current++
    }
  }, [filter, reload])

  const open = (user: ManagedUser) => {
    setEditing(user)
    setEditorError(null)
  }
  // Atualiza a lista e o modal (se aberto para o mesmo usuário) com o que o servidor devolveu.
  const sync = (updated: ManagedUser) => {
    setUsers((rows) =>
      rows.map((row) => (row.id === updated.id ? updated : row)),
    )
    setEditing((current) => (current?.id === updated.id ? updated : current))
  }
  // Envia as alterações do modal em sequência (não há endpoint em lote): o papel de administrador,
  // depois os setores a alocar ou com cargo trocado e os a remover. Se uma falhar, as anteriores já
  // valeram: a tela mostra o que sobrou pendente e o erro.
  const applyChanges = async (user: ManagedUser, changes: AccessChanges) => {
    setEditorError(null)
    let applied = 0
    let current = user
    try {
      if (changes.administrator !== undefined) {
        current = await setUserAdministrator(user.id, changes.administrator)
        sync(current)
        applied++
      }
      for (const { sector, roleId } of changes.upsert) {
        current = await setUserSector(user.id, sector.id, roleId)
        sync(current)
        applied++
      }
      for (const sector of changes.deallocate) {
        current = await setUserSector(user.id, sector.id, null)
        sync(current)
        applied++
      }
      toast.success('Alterações salvas.')
    } catch (failure) {
      const problem = administrationFailure(failure)
      setEditorError(
        applied > 0
          ? `${problem.message} Parte das alterações já foi salva; o que não foi continua pendente.`
          : problem.message,
      )
      if (problem.status === 403) await access.refresh()
    } finally {
      if (current.id === actor?.id) await access.refresh()
    }
  }
  const save = (user: ManagedUser, changes: AccessChanges) =>
    run(() => applyChanges(user, changes))
  // Ativa ou desativa (confirmado, ou direto ao reativar pela lista). O modal "Gerenciar acesso" pode
  // estar fechado: só ele mostra o erro; sem ele o erro vai para um toast.
  const setActive = async (user: ManagedUser, active: boolean) => {
    if (!access.canManageUsers) return
    setEditorError(null)
    try {
      const updated = await setUserActive(user.id, active)
      sync(updated)
      setConfirmation(null)
      toast.success(active ? 'Usuário reativado.' : 'Usuário desativado.')
      if (updated.id === actor?.id) await access.refresh()
    } catch (failure) {
      const problem = administrationFailure(failure)
      setConfirmation(null)
      if (editing) setEditorError(problem.message)
      else toast.error(problem.message)
      if (problem.status === 403) await access.refresh()
    }
  }
  const confirm = () =>
    run(async () => {
      if (!confirmation || !access.canManageUsers) return
      await setActive(confirmation.user, confirmation.active)
    })
  // Reativar não pede confirmação (é inofensivo); desativar abre a confirmação.
  const quickToggle = (user: ManagedUser) => {
    if (user.active) {
      setConfirmation({ type: 'active', user, active: false })
      return
    }
    run(() => setActive(user, true))
  }
  // Ativar e desativar valem para todos os setores do usuário: só o administrador faz isso, e
  // ninguém mexe na própria conta.
  const canToggleUser = (target: ManagedUser) =>
    access.isAdmin && target.id !== actor?.id
  const visible = users
    .filter(
      (user) =>
        (filter === 'none'
          ? !user.administrator && user.sectorIds.length === 0
          : filter === 'admin'
            ? user.administrator
            : true) &&
        `${user.name} ${user.email}`
          .toLocaleLowerCase()
          .includes(search.trim().toLocaleLowerCase()) &&
        (sectorFilter === 'all' ||
          (sectorFilter === 'none'
            ? user.sectorIds.length === 0
            : user.sectorIds.includes(Number(sectorFilter)))) &&
        (activeFilter === 'all' || user.active === (activeFilter === 'active')),
    )
    .sort((a, b) => a.name.localeCompare(b.name, 'pt-BR'))
  // Texto, consequências e tom de cada confirmação. Ações que tiram acesso (desativar) ficam em
  // vermelho.
  const confirmationCopy = (action: UserAction) => {
    const name = <strong className="font-semibold">{action.user.name}</strong>
    return action.active
      ? {
          title: 'Reativar usuário',
          confirmLabel: 'Reativar',
          tone: 'default' as const,
          message: <>Deseja reativar {name}?</>,
          details: ['O usuário volta a poder entrar no sistema.'],
        }
      : {
          title: 'Desativar usuário',
          confirmLabel: 'Desativar',
          tone: 'destructive' as const,
          message: <>Deseja desativar {name}?</>,
          details: [
            'O acesso é bloqueado imediatamente, inclusive a sessão que já estiver aberta.',
            'O histórico é preservado.',
            'Você pode reativar a conta quando quiser.',
          ],
        }
  }

  return (
    <AdminPage
      title="Usuários"
      description="Gerencie quem é administrador e o cargo de cada usuário em cada setor em que está alocado."
    >
      <ErrorNotice
        message={error}
        retry={() => setReload((value) => value + 1)}
      />
      <ListFilters
        title="Filtrar usuários"
        ariaLabel="Filtros dos usuários"
        active={[
          ...(search.trim()
            ? [{ label: `Busca: ${search.trim()}`, clear: () => setSearch('') }]
            : []),
          ...(filter !== 'all'
            ? [
                {
                  label:
                    filter === 'none'
                      ? 'Sem acesso'
                      : filter === 'admin'
                        ? 'Administradores'
                        : `Cargo: ${roles.find((r) => String(r.id) === filter)?.name ?? filter}`,
                  clear: () => setFilter('all'),
                },
              ]
            : []),
          ...(activeFilter !== 'all'
            ? [
                {
                  label: activeFilter === 'active' ? 'Ativos' : 'Inativos',
                  clear: () => setActiveFilter('all'),
                },
              ]
            : []),
          ...(sectorFilter !== 'all'
            ? [
                {
                  label:
                    sectorFilter === 'none'
                      ? 'Sem setor'
                      : `Setor: ${sectors.find((x) => String(x.id) === sectorFilter)?.name ?? sectorFilter}`,
                  clear: () => setSectorFilter('all'),
                },
              ]
            : []),
        ]}
      >
        <UiLabel className="flex-col items-stretch gap-1.5 text-xs font-medium text-muted-foreground sm:col-span-2 lg:col-span-1">
          Nome ou e-mail
          <UiInput
            type="search"
            aria-label="Buscar usuário"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Nome ou e-mail"
          />
        </UiLabel>
        <UiLabel className="flex-col items-stretch gap-1.5 text-xs font-medium text-muted-foreground">
          Cargo
          <SelectField
            aria-label="Filtrar por cargo"
            options={[
              { value: 'all', label: 'Todos os cargos' },
              { value: 'admin', label: 'Administradores' },
              { value: 'none', label: 'Sem acesso' },
              ...roles.map((role) => ({
                value: String(role.id),
                label: role.name,
              })),
            ]}
            value={filter}
            onValueChange={setFilter}
            placeholder="Todos os cargos"
          />
        </UiLabel>
        <UiLabel className="flex-col items-stretch gap-1.5 text-xs font-medium text-muted-foreground">
          Setor
          <SelectField
            aria-label="Filtrar por setor"
            options={[
              { value: 'all', label: 'Todos os setores' },
              { value: 'none', label: 'Sem setor' },
              ...sectors.map((sector) => ({
                value: String(sector.id),
                label: `${sector.name}${sector.active ? '' : ' · Inativo'}`,
              })),
            ]}
            value={sectorFilter}
            onValueChange={setSectorFilter}
            placeholder="Todos os setores"
          />
        </UiLabel>
        <UiLabel className="flex-col items-stretch gap-1.5 text-xs font-medium text-muted-foreground">
          Situação
          <SelectField
            aria-label="Filtrar por situação"
            options={[
              { value: 'all', label: 'Todos' },
              { value: 'active', label: 'Ativos' },
              { value: 'inactive', label: 'Inativos' },
            ]}
            value={activeFilter}
            onValueChange={setActiveFilter}
          />
        </UiLabel>
      </ListFilters>
      {loading ? (
        <p role="status">Carregando usuários...</p>
      ) : (
        !error && (
          <>
            <p className="text-sm text-muted-foreground">
              {visible.length} usuários encontrados
            </p>
            <div className="grid grid-cols-1 gap-3">
              {visible.map((user) => {
                const sectorLabels = [...user.allocations]
                  .sort((a, b) => a.sectorId - b.sectorId)
                  .map(
                    (allocation) =>
                      `${sectors.find((sector) => sector.id === allocation.sectorId)?.name || `Setor #${allocation.sectorId}`} · ${allocation.roleName}`,
                  )
                const initials = initialsOf(user.name)
                return (
                  <article
                    key={user.id}
                    className="flex flex-col gap-4 rounded-xl border bg-card p-5 text-card-foreground"
                  >
                    <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                      <div className="flex min-w-0 gap-4">
                        <div className="flex size-12 shrink-0 items-center justify-center rounded-full bg-primary/10 text-sm font-semibold text-primary">
                          {initials || '?'}
                        </div>
                        <div className="min-w-0 space-y-1">
                          <h2 className="truncate text-base font-semibold text-foreground">
                            {user.name}
                          </h2>
                          <p className="break-all text-sm text-muted-foreground">
                            {user.email}
                          </p>
                          <div className="flex flex-wrap gap-1.5 pt-1">
                            <StatusBadge
                              tone={user.active ? 'success' : 'neutral'}
                            >
                              {user.active ? 'Ativo' : 'Inativo'}
                            </StatusBadge>
                          </div>
                        </div>
                      </div>
                      <div className="flex shrink-0 flex-wrap items-center gap-2">
                        {canToggleUser(user) && (
                          <UiButton
                            variant="outline"
                            type="button"
                            disabled={busy}
                            aria-label={`${user.active ? 'Desativar' : 'Ativar'} ${user.name}`}
                            onClick={() => quickToggle(user)}
                          >
                            {user.active ? 'Desativar' : 'Ativar'}
                          </UiButton>
                        )}
                        <UiButton
                          variant="outline"
                          type="button"
                          disabled={busy}
                          onClick={() => open(user)}
                        >
                          Gerenciar acesso
                        </UiButton>
                      </div>
                    </div>
                    <div className="grid gap-3 border-t pt-4 sm:grid-cols-2">
                      <div className="space-y-1.5">
                        <p className="text-xs font-medium tracking-wide text-muted-foreground uppercase">
                          Papel
                        </p>
                        <StatusBadge
                          tone={user.administrator ? 'primary' : 'neutral'}
                        >
                          {user.administrator
                            ? 'Administrador'
                            : 'Usuário por setor'}
                        </StatusBadge>
                      </div>
                      <div className="space-y-1.5">
                        <p className="text-xs font-medium tracking-wide text-muted-foreground uppercase">
                          Setores e cargos ({sectorLabels.length})
                        </p>
                        {sectorLabels.length === 0 ? (
                          <p className="text-sm text-muted-foreground">
                            Nenhum setor associado.
                          </p>
                        ) : (
                          <div className="flex flex-wrap gap-1.5">
                            {sectorLabels.map((label) => (
                              <StatusBadge key={label} tone="primary">
                                {label}
                              </StatusBadge>
                            ))}
                          </div>
                        )}
                      </div>
                    </div>
                  </article>
                )
              })}
              {visible.length === 0 && (
                <p className="rounded-xl border bg-card p-6 text-muted-foreground">
                  Nenhum usuário encontrado para estes filtros.
                </p>
              )}
            </div>
          </>
        )
      )}
      {editing && (
        <UserAccessModal
          key={editing.id}
          user={editing}
          initials={initialsOf(editing.name)}
          roles={roles}
          sectors={sectors}
          isSelf={editing.id === actor?.id}
          canGrantAdministrator={access.isAdmin && editing.id !== actor?.id}
          toggleNote={
            canToggleUser(editing)
              ? null
              : !access.isAdmin
                ? 'Somente o administrador ativa ou desativa usuários.'
                : 'Você não pode desativar a própria conta.'
          }
          busy={busy}
          inactive={!!confirmation}
          error={editorError}
          onClose={() => setEditing(null)}
          onSave={(changes) => save(editing, changes)}
          onToggleActive={() =>
            setConfirmation({
              type: 'active',
              user: editing,
              active: !editing.active,
            })
          }
        />
      )}
      {confirmation && (
        <AdminConfirmation
          isOpen
          onClose={() => setConfirmation(null)}
          onConfirm={confirm}
          busy={busy}
          {...confirmationCopy(confirmation)}
        />
      )}
    </AdminPage>
  )
}
