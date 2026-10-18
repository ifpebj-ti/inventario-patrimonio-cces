'use client'

import { SelectField } from '@/components/ui/select-field'
import { StatusBadge } from '@/components/ui/status-badge'
import { ListFilters } from '@/components/organisms/listFilters'
import { UserCheck, UserX } from 'lucide-react'
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
  AdminModal,
  AdminPage,
  ErrorNotice,
  useOperation,
} from './shared'

type UserAction =
  | { type: 'profile'; user: ManagedUser; profileId: number | null }
  | { type: 'sector'; user: ManagedUser; sector: Sector; allocated: boolean }
  | { type: 'active'; user: ManagedUser; active: boolean }

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
  const [profileId, setProfileId] = useState('')
  const [sectorId, setSectorId] = useState('')
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
    setProfileId(user.profileId === null ? '' : String(user.profileId))
    setSectorId('')
    setEditorError(null)
  }
  // Executa a ação confirmada (ou direta, no caso de reativar pela lista). O modal "Gerenciar acesso" pode
  // estar fechado: só ele reflete o resultado e os erros; sem ele o erro vai para um toast.
  const perform = async (action: UserAction) => {
    if (!access.canManageUsers) return
    setEditorError(null)
    try {
      const updated =
        action.type === 'active'
          ? await setUserActive(action.user.id, action.active)
          : action.type === 'profile'
            ? await assignUserProfile(action.user.id, action.profileId)
            : await setUserSector(
                action.user.id,
                action.sector.id,
                action.allocated,
              )
      setUsers((rows) =>
        rows.map((row) => (row.id === updated.id ? updated : row)),
      )
      if (editing?.id === updated.id) {
        setEditing(updated)
        setProfileId(
          updated.profileId === null ? '' : String(updated.profileId),
        )
        setSectorId('')
      }
      setConfirmation(null)
      toast.success(
        action.type === 'profile'
          ? 'Perfil do usuário atualizado.'
          : action.type === 'active'
            ? action.active
              ? 'Usuário reativado.'
              : 'Usuário desativado.'
            : 'Associação ao setor atualizada.',
      )
      if (updated.id === actor?.id) await access.refresh()
    } catch (failure) {
      const problem = administrationFailure(failure)
      const message =
        problem.status === 400 && action.type === 'profile'
          ? 'O perfil selecionado não está disponível. Atualize a lista antes de tentar novamente.'
          : problem.message
      setConfirmation(null)
      if (editing) setEditorError(message)
      else toast.error(message)
      if (problem.status === 403) await access.refresh()
    }
  }
  const confirm = () =>
    run(async () => {
      if (confirmation) await perform(confirmation)
    })
  // Reativar não pede confirmação (é inofensivo); desativar abre a confirmação.
  const quickToggle = (user: ManagedUser) => {
    if (user.active) {
      setConfirmation({ type: 'active', user, active: false })
      return
    }
    run(() => perform({ type: 'active', user, active: true }))
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
  const availableSectors = sectors.filter(
    (sector) => sector.active && !editing?.sectorIds.includes(sector.id),
  )
  const changedProfile =
    !!editing && profileId !== '' && Number(profileId) !== editing.profileId

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
    if (action.type === 'profile') {
      const self = action.user.id === actor?.id
      return {
        title: 'Alterar perfil do usuário',
        confirmLabel: 'Alterar perfil',
        tone: self ? ('destructive' as const) : ('default' as const),
        message: (
          <>
            Deseja atribuir o perfil{' '}
            <strong className="font-semibold">
              {profileName(action.profileId)}
            </strong>{' '}
            a {name}?
          </>
        ),
        details: [
          self
            ? 'Você poderá perder seu acesso administrativo.'
            : 'Os acessos do usuário serão alterados.',
        ],
      }
    }
    const sectorName = (
      <strong className="font-semibold">{action.sector.name}</strong>
    )
    return action.allocated
      ? {
          title: 'Alocar usuário',
          confirmLabel: 'Alocar',
          tone: 'default' as const,
          message: (
            <>
              Deseja alocar {name} no setor {sectorName}?
            </>
          ),
          details: ['Os outros setores do usuário serão preservados.'],
        }
      : {
          title: 'Desalocar usuário',
          confirmLabel: 'Desalocar',
          tone: 'destructive' as const,
          message: (
            <>
              Deseja desalocar {name} do setor {sectorName}?
            </>
          ),
          details: ['Os outros setores do usuário serão preservados.'],
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
                const initials = user.name
                  .split(' ')
                  .filter(Boolean)
                  .slice(0, 2)
                  .map((part) => part[0]?.toUpperCase())
                  .join('')
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
        <AdminModal
          title={`Acesso de ${editing.name}`}
          busy={busy}
          inactive={!!confirmation}
          onClose={() => setEditing(null)}
        >
          <ErrorNotice message={editorError} />
          <p className="text-sm text-muted-foreground break-all">
            {editing.email}
          </p>
          <section className="space-y-3">
            <h3 className="font-semibold">Perfil de acesso</h3>
            <UiLabel className="flex-col items-stretch gap-1.5 text-sm">
              Perfil do usuário
              <SelectField
                aria-label="Perfil do usuário"
                options={assignableProfiles.map((profile) => ({
                  value: String(profile.id),
                  label: profileLabel(profile.name),
                }))}
                value={profileId}
                onValueChange={setProfileId}
                placeholder="Selecione o perfil"
                disabled={busy}
              />
            </UiLabel>
            <UiButton
              type="button"
              disabled={busy || !changedProfile}
              onClick={() =>
                setConfirmation({
                  type: 'profile',
                  user: editing,
                  profileId: Number(profileId),
                })
              }
            >
              Confirmar alteração de perfil
            </UiButton>
            {editing.id === actor?.id && (
              <p className="text-sm text-warning">
                Alterar seu próprio perfil pode remover seu acesso às telas
                administrativas.
              </p>
            )}
          </section>
          <section className="space-y-3 border-t border-muted pt-4">
            <h3 className="font-semibold">Situação da conta</h3>
            <p className="text-sm text-muted-foreground">
              {editing.active
                ? 'Conta ativa. Desativar bloqueia o acesso imediatamente e preserva o histórico.'
                : 'Conta desativada: o usuário não consegue entrar no sistema.'}
            </p>
            {canToggleUser(editing) ? (
              <div className="flex flex-wrap gap-2">
                <UiButton
                  variant="outline"
                  type="button"
                  disabled={busy}
                  onClick={() =>
                    setConfirmation({
                      type: 'active',
                      user: editing,
                      active: !editing.active,
                    })
                  }
                >
                  {editing.active ? <UserX /> : <UserCheck />}
                  {editing.active ? 'Desativar usuário' : 'Reativar usuário'}
                </UiButton>
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">
                {!access.isAdmin
                  ? 'Somente o administrador ativa ou desativa usuários.'
                  : 'Você não pode desativar a própria conta.'}
              </p>
            )}
          </section>
          <section className="space-y-3 border-t border-muted pt-4">
            <h3 className="font-semibold">Setores associados</h3>
            <ul className="space-y-2">
              {editing.sectorIds.map((id) => {
                const sector = sectors.find((row) => row.id === id)
                return (
                  <li
                    key={id}
                    className="flex items-center justify-between gap-2 text-sm"
                  >
                    <span>
                      {sector?.name || `Setor #${id}`}
                      {sector && !sector.active && ' · Inativo'}
                    </span>
                    <UiButton
                      variant="outline"
                      type="button"
                      disabled={
                        busy ||
                        !sector ||
                        (!access.isAdmin && !access.sectorIds.includes(id))
                      }
                      onClick={() =>
                        sector &&
                        setConfirmation({
                          type: 'sector',
                          user: editing,
                          sector,
                          allocated: false,
                        })
                      }
                      aria-label={`Desalocar de ${sector?.name || id}`}
                    >
                      Desalocar
                    </UiButton>
                  </li>
                )
              })}
              {editing.sectorIds.length === 0 && (
                <li className="text-sm text-muted-foreground">
                  Nenhum setor associado.
                </li>
              )}
            </ul>
            <UiLabel className="flex-col items-stretch gap-1.5 text-sm">
              Adicionar setor
              <SelectField
                aria-label="Adicionar setor"
                options={availableSectors.map((sector) => ({
                  value: String(sector.id),
                  label: sector.name,
                }))}
                value={sectorId}
                onValueChange={setSectorId}
                placeholder="Selecione um setor ativo"
                disabled={busy}
              />
            </UiLabel>
            <UiButton
              type="button"
              disabled={busy || !sectorId}
              onClick={() => {
                const sector = availableSectors.find(
                  (row) => row.id === Number(sectorId),
                )
                if (sector)
                  setConfirmation({
                    type: 'sector',
                    user: editing,
                    sector,
                    allocated: true,
                  })
              }}
            >
              Alocar no setor
            </UiButton>
            {availableSectors.length === 0 && (
              <p className="text-sm text-muted-foreground">
                Não há outros setores ativos disponíveis.
              </p>
            )}
          </section>
        </AdminModal>
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
