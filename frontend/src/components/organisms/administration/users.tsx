'use client'

import { SelectField } from '@/components/ui/select-field'
import { StatusBadge } from '@/components/ui/status-badge'
import { ListFilters } from '@/components/organisms/listFilters'
import { Plus, Trash2, UserCheck, UserX } from 'lucide-react'
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
  deleteUser,
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
import { CreateUserModal } from './createUser'

type UserAction =
  | { type: 'profile'; user: ManagedUser; profileId: number | null }
  | { type: 'sector'; user: ManagedUser; sector: Sector; allocated: boolean }
  | { type: 'active'; user: ManagedUser; active: boolean }
  | { type: 'delete'; user: ManagedUser }

export const UsersScreen = () => {
  const { user: actor } = useAuth()
  const access = useAdministrationAccess()
  const [users, setUsers] = useState<ManagedUser[]>([])
  const [profiles, setProfiles] = useState<Profile[]>([])
  const [sectors, setSectors] = useState<Sector[]>([])
  const [permissions, setPermissions] = useState<Permission[]>([])
  const [creating, setCreating] = useState(false)
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
  const confirm = () =>
    run(async () => {
      if (!confirmation || !access.canManageUsers) return
      const action = confirmation
      setEditorError(null)
      try {
        if (action.type === 'delete') {
          await deleteUser(action.user.id)
          setUsers((rows) => rows.filter((row) => row.id !== action.user.id))
          setEditing(null)
          setConfirmation(null)
          toast.success('Usuário excluído.')
          return
        }
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
        setEditing(updated)
        setProfileId(
          updated.profileId === null ? '' : String(updated.profileId),
        )
        setSectorId('')
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
        setConfirmation(null)
        setEditorError(
          problem.status === 400 && action.type === 'profile'
            ? 'O perfil selecionado não está disponível. Atualize a lista antes de tentar novamente.'
            : problem.message,
        )
        if (problem.status === 403) await access.refresh()
      }
    })
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
  // Desativar e excluir afetam todos os setores do usuário: o gestor só remove quem atua
  // exclusivamente nos setores dele; ninguém remove a própria conta.
  const canRemoveUser = (target: ManagedUser) =>
    target.id !== actor?.id &&
    (access.isAdmin ||
      (canManageUser(target) &&
        target.sectorIds.every((id) => access.sectorIds.includes(id))))
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

  return (
    <AdminPage
      title="Usuários"
      description="Gerencie o perfil de acesso e a participação de cada usuário em múltiplos setores."
      action={
        access.canManageUsers ? (
          <UiButton
            type="button"
            disabled={loading || busy || !!error}
            onClick={() => setCreating(true)}
          >
            <Plus /> Novo usuário
          </UiButton>
        ) : undefined
      }
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
                            {user.pendingGoogleLogin && (
                              <StatusBadge tone="warning">
                                Aguardando primeiro acesso
                              </StatusBadge>
                            )}
                          </div>
                        </div>
                      </div>
                      {canManageUser(user) ? (
                        <UiButton
                          variant="outline"
                          type="button"
                          disabled={busy}
                          onClick={() => open(user)}
                          className="shrink-0"
                        >
                          Gerenciar acesso
                        </UiButton>
                      ) : (
                        <p className="max-w-48 shrink-0 text-xs text-muted-foreground">
                          Somente o administrador altera este acesso.
                        </p>
                      )}
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
            {canRemoveUser(editing) ? (
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
                <UiButton
                  variant="outline"
                  type="button"
                  disabled={busy}
                  className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                  onClick={() =>
                    setConfirmation({ type: 'delete', user: editing })
                  }
                >
                  <Trash2 /> Excluir usuário
                </UiButton>
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">
                {editing.id === actor?.id
                  ? 'Você não pode desativar nem excluir a própria conta.'
                  : 'Este usuário também atua em outros setores: só o administrador pode desativar ou excluir.'}
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
      {creating && (
        <CreateUserModal
          profiles={assignableProfiles}
          permissions={permissions}
          sectors={sectors}
          onClose={() => setCreating(false)}
          onCreated={() => {
            setCreating(false)
            setReload((value) => value + 1)
            toast.success('Usuário cadastrado com perfil e setores associados.')
          }}
        />
      )}
      <AdminConfirmation
        isOpen={!!confirmation}
        onClose={() => setConfirmation(null)}
        onConfirm={confirm}
        busy={busy}
        title={
          confirmation?.type === 'profile'
            ? 'Alterar perfil do usuário'
            : confirmation?.type === 'active'
              ? confirmation.active
                ? 'Reativar usuário'
                : 'Desativar usuário'
              : confirmation?.type === 'delete'
                ? 'Excluir usuário'
                : confirmation?.allocated
                  ? 'Alocar usuário'
                  : 'Desalocar usuário'
        }
        message={
          confirmation?.type === 'active'
            ? confirmation.active
              ? `Deseja reativar ${confirmation.user.name}? O usuário volta a poder entrar no sistema.`
              : `Deseja desativar ${confirmation.user.name}? O acesso é bloqueado imediatamente, mas o histórico é preservado.`
            : confirmation?.type === 'delete'
              ? `Deseja excluir ${confirmation.user.name}? A conta e os vínculos com setores são removidos permanentemente. Quem criou inventários não pode ser excluído: desative a conta.`
              : confirmation?.type === 'profile'
                ? `Deseja atribuir "${profileName(confirmation.profileId)}" a ${confirmation.user.name}? ${confirmation.user.id === actor?.id ? 'Você poderá perder seu acesso administrativo.' : 'Os acessos do usuário serão alterados.'}`
                : `Deseja ${confirmation?.allocated ? 'alocar' : 'desalocar'} ${confirmation?.user.name} ${confirmation?.allocated ? 'no' : 'do'} setor "${confirmation?.sector.name}"? Os outros setores serão preservados.`
        }
      />
    </AdminPage>
  )
}
