'use client'

import { SelectField } from '@/components/ui/select-field'
import { StatusBadge } from '@/components/ui/status-badge'
import { ListFilters } from '@/components/organisms/listFilters'
import { profileLabel } from '@/lib/access-labels'
import { Button as UiButton } from '@/components/ui/button'
import { Label as UiLabel } from '@/components/ui/label'
import { Input as UiInput } from '@/components/ui/input'

import { useEffect, useRef, useState } from 'react'
import { toast } from 'sonner'
import type {
  ManagedUser,
  Profile,
  Sector,
  Permission,
} from '@/commons/models/administration'
import { useAuth } from '@/hooks/useAuth'
import { useAdministrationAccess } from '@/contexts/AdministrationAccess'
import {
  administrationFailure,
  assignUserProfile,
  listProfiles,
  listSectors,
  listUsers,
  listPermissions,
  setUserActive,
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

// Ativar/desativar sempre pede confirmação (reativar pela lista não). "save" só pede confirmação
// quando a pessoa altera o próprio perfil, pois pode perder o acesso administrativo.
type UserAction =
  | { type: 'active'; user: ManagedUser; active: boolean }
  | { type: 'save'; user: ManagedUser; changes: AccessChanges }

export const UsersScreen = () => {
  const { user: actor } = useAuth()
  const access = useAdministrationAccess()
  const [users, setUsers] = useState<ManagedUser[]>([])
  const [profiles, setProfiles] = useState<Profile[]>([])
  const [sectors, setSectors] = useState<Sector[]>([])
  const [permissions, setPermissions] = useState<Permission[]>([])
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
        filter === 'all' || filter === 'none' ? undefined : Number(filter),
        controller.signal,
      ),
      listProfiles(controller.signal),
      listSectors(controller.signal),
      listPermissions(controller.signal),
    ])
      .then(([userRows, profileRows, sectorRows, permissionRows]) => {
        if (request !== requestVersion.current || controller.signal.aborted)
          return
        setUsers(userRows)
        setProfiles(profileRows)
        setSectors(sectorRows)
        setPermissions(permissionRows)
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
  // Envia as alterações do modal em sequência (não há endpoint em lote): perfil, depois os setores a
  // incluir e os a remover. Se uma falhar, as anteriores já valeram: a tela mostra o que sobrou
  // pendente e o erro.
  const applyChanges = async (user: ManagedUser, changes: AccessChanges) => {
    setEditorError(null)
    let applied = 0
    let current = user
    try {
      if (changes.profileId !== undefined) {
        current = await assignUserProfile(user.id, changes.profileId)
        sync(current)
        applied++
      }
      for (const sector of changes.allocate) {
        current = await setUserSector(user.id, sector.id, true)
        sync(current)
        applied++
      }
      for (const sector of changes.deallocate) {
        current = await setUserSector(user.id, sector.id, false)
        sync(current)
        applied++
      }
      setConfirmation(null)
      toast.success('Alterações salvas.')
    } catch (failure) {
      const problem = administrationFailure(failure)
      const message =
        problem.status === 400 &&
        changes.profileId !== undefined &&
        applied === 0
          ? 'O perfil selecionado não está disponível. Atualize a lista antes de tentar novamente.'
          : problem.message
      setConfirmation(null)
      setEditorError(
        applied > 0
          ? `${message} Parte das alterações já foi salva; o que não foi continua pendente.`
          : message,
      )
      if (problem.status === 403) await access.refresh()
    } finally {
      if (current.id === actor?.id) await access.refresh()
    }
  }
  const save = (user: ManagedUser, changes: AccessChanges) => {
    if (user.id === actor?.id && changes.profileId !== undefined) {
      setConfirmation({ type: 'save', user, changes })
      return
    }
    run(() => applyChanges(user, changes))
  }
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
      if (confirmation.type === 'save')
        await applyChanges(confirmation.user, confirmation.changes)
      else await setActive(confirmation.user, confirmation.active)
    })
  // Reativar não pede confirmação (é inofensivo); desativar abre a confirmação.
  const quickToggle = (user: ManagedUser) => {
    if (user.active) {
      setConfirmation({ type: 'active', user, active: false })
      return
    }
    run(() => setActive(user, true))
  }
  const profileName = (id: number | null) =>
    id === null
      ? 'Sem perfil'
      : profileLabel(
          profiles.find((profile) => profile.id === id)?.name ||
            `Perfil #${id}`,
        )
  // Perfil de gestão = tem a permissão ADMIN ou MANAGE_SECTOR. O gestor não atribui nem altera
  // esses perfis; só o administrador.
  const managementPermissionIds = new Set(
    permissions
      .filter((p) => p.name === 'ADMIN' || p.name === 'MANAGE_SECTOR')
      .map((p) => p.id),
  )
  const isManagementProfile = (id: number | null) => {
    const profile = profiles.find((row) => row.id === id)
    return (
      !!profile &&
      profile.permissionIds.some((permissionId) =>
        managementPermissionIds.has(permissionId),
      )
    )
  }
  const assignableProfiles = access.isAdmin
    ? profiles
    : profiles.filter((profile) => !isManagementProfile(profile.id))
  const canManageUser = (target: ManagedUser) =>
    access.isAdmin ||
    (!isManagementProfile(target.profileId) &&
      target.sectorIds.some((id) => access.sectorIds.includes(id)))
  // Ativar e desativar valem para todos os setores do usuário: só o administrador faz isso, e
  // ninguém mexe na própria conta.
  const canToggleUser = (target: ManagedUser) =>
    access.isAdmin && target.id !== actor?.id
  const visible = users
    .filter(
      (user) =>
        (filter === 'none'
          ? user.profileId === null
          : filter === 'all' || user.profileId === Number(filter)) &&
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
  // Texto, consequências e tom de cada confirmação. Ações que tiram acesso ou desfazem algo
  // (desativar, desalocar, mexer no próprio perfil) ficam em vermelho.
  const confirmationCopy = (action: UserAction) => {
    const name = <strong className="font-semibold">{action.user.name}</strong>
    if (action.type === 'active') {
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
    // Só chega aqui quando a pessoa altera o próprio perfil.
    return {
      title: 'Salvar alterações',
      confirmLabel: 'Salvar',
      tone: 'destructive' as const,
      message: (
        <>
          Deseja salvar as alterações no seu próprio acesso
          {action.changes.profileId !== undefined && (
            <>
              , inclusive o perfil{' '}
              <strong className="font-semibold">
                {profileName(action.changes.profileId)}
              </strong>
            </>
          )}
          ?
        </>
      ),
      details: ['Você poderá perder seu acesso administrativo.'],
    }
  }

  return (
    <AdminPage
      title="Usuários"
      description="Gerencie o perfil de acesso e a participação de cada usuário em múltiplos setores."
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
                      ? 'Sem perfil'
                      : `Perfil: ${profiles.find((p) => String(p.id) === filter)?.name ?? filter}`,
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
          Perfil
          <SelectField
            aria-label="Filtrar por perfil"
            options={[
              { value: 'all', label: 'Todos os perfis' },
              { value: 'none', label: 'Sem perfil' },
              ...profiles.map((profile) => ({
                value: String(profile.id),
                label: profileLabel(profile.name),
              })),
            ]}
            value={filter}
            onValueChange={setFilter}
            placeholder="Todos os perfis"
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
                const sectorNames =
                  user.sectorIds.length === 0
                    ? []
                    : user.sectorIds.map(
                        (id) =>
                          sectors.find((sector) => sector.id === id)?.name ||
                          `Setor #${id}`,
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
                        {canManageUser(user) ? (
                          <UiButton
                            variant="outline"
                            type="button"
                            disabled={busy}
                            onClick={() => open(user)}
                          >
                            Gerenciar acesso
                          </UiButton>
                        ) : (
                          <p className="max-w-48 text-xs text-muted-foreground">
                            Somente o administrador altera este acesso.
                          </p>
                        )}
                      </div>
                    </div>
                    <div className="grid gap-3 border-t pt-4 sm:grid-cols-2">
                      <div className="space-y-1.5">
                        <p className="text-xs font-medium tracking-wide text-muted-foreground uppercase">
                          Perfil
                        </p>
                        <StatusBadge
                          tone={user.profileId === null ? 'neutral' : 'primary'}
                        >
                          {profileName(user.profileId)}
                        </StatusBadge>
                      </div>
                      <div className="space-y-1.5">
                        <p className="text-xs font-medium tracking-wide text-muted-foreground uppercase">
                          Setores ({sectorNames.length})
                        </p>
                        {sectorNames.length === 0 ? (
                          <p className="text-sm text-muted-foreground">
                            Nenhum setor associado.
                          </p>
                        ) : (
                          <div className="flex flex-wrap gap-1.5">
                            {sectorNames.map((name) => (
                              <StatusBadge key={name} tone="primary">
                                {name}
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
          profiles={assignableProfiles}
          sectors={sectors}
          isSelf={editing.id === actor?.id}
          canRemoveSector={(id) =>
            access.isAdmin || access.sectorIds.includes(id)
          }
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
