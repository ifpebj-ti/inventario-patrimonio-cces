'use client'

import { SelectField } from '@/components/ui/select-field'
import { Plus, CheckCircle2, Clock3 } from 'lucide-react'
import { ListFilters } from '@/components/organisms/listFilters'
import { StatusBadge } from '@/components/ui/status-badge'
import { Label as UiLabel } from '@/components/ui/label'
import { Input as UiInput } from '@/components/ui/input'
import { Button as UiButton } from '@/components/ui/button'

import { useCallback, useEffect, useRef, useState } from 'react'
import { toast } from 'sonner'
import {
  sectorFormSchema,
  type ManagedUser,
  type Sector,
} from '@/commons/models/administration'
import { useAdministrationAccess } from '@/contexts/AdministrationAccess'
import {
  administrationFailure,
  listSectors,
  listSectorUsers,
  saveSector,
  setSectorActive,
} from '@/services/administration'
import {
  AdminConfirmation,
  AdminModal,
  AdminPage,
  ErrorNotice,
  useOperation,
} from './shared'

export const SectorsScreen = () => {
  const { isAdmin, refresh } = useAdministrationAccess()
  const [sectors, setSectors] = useState<Sector[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [filter, setFilter] = useState('all')
  const [editing, setEditing] = useState<Sector | 'new' | null>(null)
  const [form, setForm] = useState({ name: '', code: '' })
  const [formError, setFormError] = useState<string | null>(null)
  const [activation, setActivation] = useState<Sector | null>(null)
  const [viewing, setViewing] = useState<Sector | null>(null)
  const [members, setMembers] = useState<ManagedUser[]>([])
  const [membersLoading, setMembersLoading] = useState(false)
  const [membersError, setMembersError] = useState<string | null>(null)
  const memberRequest = useRef(0)
  const { busy, run } = useOperation()

  const load = useCallback(async (signal?: AbortSignal) => {
    setLoading(true)
    setError(null)
    try {
      const data = await listSectors(signal)
      if (!signal?.aborted) setSectors(data)
    } catch (failure) {
      if (!signal?.aborted) setError(administrationFailure(failure).message)
    } finally {
      if (!signal?.aborted) setLoading(false)
    }
  }, [])
  useEffect(() => {
    const controller = new AbortController()
    load(controller.signal)
    return () => controller.abort()
  }, [load])
  useEffect(
    () => () => {
      memberRequest.current++
    },
    [],
  )

  const openEditor = (sector: Sector | 'new') => {
    setEditing(sector)
    setForm(
      sector === 'new'
        ? { name: '', code: '' }
        : { name: sector.name, code: sector.code || '' },
    )
    setFormError(null)
  }
  const showMembers = async (sector: Sector) => {
    const request = ++memberRequest.current
    setViewing(sector)
    setMembers([])
    setMembersError(null)
    setMembersLoading(true)
    try {
      const data = await listSectorUsers(sector.id)
      if (request === memberRequest.current) setMembers(data)
    } catch (failure) {
      if (request === memberRequest.current)
        setMembersError(administrationFailure(failure).message)
    } finally {
      if (request === memberRequest.current) setMembersLoading(false)
    }
  }
  const save = () =>
    run(async () => {
      if (!isAdmin || !editing) return
      const parsed = sectorFormSchema.safeParse(form)
      if (!parsed.success) {
        setFormError(parsed.error.issues[0].message)
        return
      }
      if (
        sectors.some(
          (sector) =>
            sector.name === parsed.data.name &&
            (editing === 'new' || sector.id !== editing.id),
        )
      ) {
        setFormError('Já existe um setor com este nome.')
        return
      }
      setFormError(null)
      try {
        const result = await saveSector(
          parsed.data,
          editing === 'new' ? undefined : editing.id,
        )
        setSectors((rows) => [
          ...rows.filter((row) => row.id !== result.id),
          result,
        ])
        setEditing(null)
        toast.success('Setor salvo com sucesso.')
      } catch (failure) {
        const problem = administrationFailure(failure)
        setFormError(problem.message)
        if (problem.status === 403) await refresh()
      }
    })
  const changeActive = () =>
    run(async () => {
      if (!isAdmin || !activation) return
      setError(null)
      try {
        const result = await setSectorActive(activation.id, !activation.active)
        setSectors((rows) =>
          rows.map((row) => (row.id === result.id ? result : row)),
        )
        setActivation(null)
        toast.success(
          result.active
            ? 'Setor ativado.'
            : 'Setor desativado. As alocações existentes foram preservadas.',
        )
      } catch (failure) {
        const problem = administrationFailure(failure)
        setActivation(null)
        setError(problem.message)
        if (problem.status === 403) await refresh()
      }
    })
  const visible = sectors
    .filter(
      (sector) =>
        (filter === 'all' || sector.active === (filter === 'active')) &&
        `${sector.name} ${sector.code || ''}`
          .toLocaleLowerCase()
          .includes(search.toLocaleLowerCase().trim()),
    )
    .sort((a, b) => a.name.localeCompare(b.name, 'pt-BR'))

  return (
    <AdminPage
      title="Setores"
      description="Consulte os setores e seus usuários. Setores são ativados ou desativados, preservando suas associações."
      action={
        isAdmin ? (
          <UiButton
            type="button"
            disabled={busy || loading || !!error}
            onClick={() => openEditor('new')}
          >
            <Plus /> Novo setor
          </UiButton>
        ) : undefined
      }
    >
      <ErrorNotice
        message={error}
        retry={() => {
          load()
        }}
      />
      <ListFilters
        title="Filtrar setores"
        ariaLabel="Filtros dos setores"
        active={[
          ...(search.trim()
            ? [{ label: `Busca: ${search.trim()}`, clear: () => setSearch('') }]
            : []),
          ...(filter !== 'all'
            ? [
                {
                  label: filter === 'active' ? 'Ativos' : 'Inativos',
                  clear: () => setFilter('all'),
                },
              ]
            : []),
        ]}
      >
        <UiLabel className="flex-col items-stretch gap-1.5 text-xs font-medium text-muted-foreground sm:col-span-2 lg:col-span-2">
          Nome ou código
          <UiInput
            type="search"
            aria-label="Buscar setor"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Nome ou código"
          />
        </UiLabel>
        <UiLabel className="flex-col items-stretch gap-1.5 text-xs font-medium text-muted-foreground">
          Situação
          <SelectField
            aria-label="Situação"
            options={[
              { value: 'all', label: 'Todos' },
              { value: 'active', label: 'Ativos' },
              { value: 'inactive', label: 'Inativos' },
            ]}
            value={filter}
            onValueChange={(value) => setFilter(value as typeof filter)}
          />
        </UiLabel>
      </ListFilters>
      {loading ? (
        <p role="status">Carregando setores...</p>
      ) : (
        !error && (
          <>
            <p className="text-sm text-muted-foreground">
              {visible.length} setores encontrados
            </p>
            <div className="grid gap-3">
              {visible.map((sector) => (
                <article
                  key={sector.id}
                  className="rounded-xl border bg-card p-5 text-card-foreground flex flex-col sm:flex-row sm:items-center justify-between gap-4"
                >
                  <div>
                    <h2 className="font-semibold text-foreground">
                      {sector.name}
                    </h2>
                    <p className="text-sm text-muted-foreground">
                      Código: {sector.code || 'Não informado'}
                    </p>
                    <StatusBadge
                      tone={sector.active ? 'success' : 'neutral'}
                      icon={sector.active ? <CheckCircle2 /> : <Clock3 />}
                      className="mt-2"
                    >
                      {sector.active ? 'Ativo' : 'Inativo'}
                    </StatusBadge>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <UiButton
                      variant="outline"
                      type="button"
                      onClick={() => {
                        showMembers(sector)
                      }}
                    >
                      Usuários associados
                    </UiButton>
                    {isAdmin && (
                      <>
                        <UiButton
                          variant="outline"
                          type="button"
                          disabled={busy}
                          onClick={() => openEditor(sector)}
                        >
                          Editar
                        </UiButton>
                        <UiButton
                          variant="outline"
                          type="button"
                          disabled={busy}
                          onClick={() => setActivation(sector)}
                        >
                          {sector.active ? 'Desativar' : 'Ativar'}
                        </UiButton>
                      </>
                    )}
                  </div>
                </article>
              ))}
              {visible.length === 0 && (
                <p className="rounded-xl border bg-card p-6 text-muted-foreground">
                  Nenhum setor encontrado para estes filtros.
                </p>
              )}
            </div>
          </>
        )
      )}
      {editing && (
        <AdminModal
          title={editing === 'new' ? 'Novo setor' : 'Editar setor'}
          busy={busy}
          onClose={() => setEditing(null)}
        >
          <form
            onSubmit={(event) => {
              event.preventDefault()
              save()
            }}
            className="space-y-4"
          >
            <ErrorNotice message={formError} />
            <UiLabel className="flex-col items-stretch gap-1.5 text-sm">
              Nome do setor
              <UiInput
                autoFocus
                value={form.name}
                onChange={(event) =>
                  setForm({ ...form, name: event.target.value })
                }
                maxLength={255}
                required
                disabled={busy}
              />
            </UiLabel>
            <UiLabel className="flex-col items-stretch gap-1.5 text-sm">
              Código (opcional)
              <UiInput
                value={form.code}
                onChange={(event) =>
                  setForm({ ...form, code: event.target.value })
                }
                maxLength={100}
                disabled={busy}
              />
            </UiLabel>
            <div className="flex justify-end gap-2">
              <UiButton
                variant="outline"
                type="button"
                disabled={busy}
                onClick={() => setEditing(null)}
              >
                Cancelar
              </UiButton>
              <UiButton type="submit" disabled={busy}>
                {busy ? 'Salvando...' : 'Salvar setor'}
              </UiButton>
            </div>
          </form>
        </AdminModal>
      )}
      <AdminConfirmation
        isOpen={!!activation}
        onClose={() => setActivation(null)}
        onConfirm={changeActive}
        busy={busy}
        title={activation?.active ? 'Desativar setor' : 'Ativar setor'}
        message={`Deseja ${activation?.active ? 'desativar' : 'ativar'} o setor "${activation?.name}"? As alocações existentes serão mantidas.`}
      />
      {viewing && (
        <AdminModal
          title={`Usuários de ${viewing.name}`}
          onClose={() => {
            memberRequest.current++
            setViewing(null)
          }}
        >
          <ErrorNotice
            message={membersError}
            retry={() => {
              showMembers(viewing)
            }}
          />
          {membersLoading ? (
            <p role="status">Carregando usuários...</p>
          ) : (
            !membersError && (
              <ul className="space-y-2">
                {members.map((member) => (
                  <li key={member.id} className="border-b border-muted py-2">
                    <p className="font-medium">{member.name}</p>
                    <p className="text-sm text-muted-foreground break-all">
                      {member.email}
                    </p>
                  </li>
                ))}
                {members.length === 0 && (
                  <li>Nenhum usuário associado a este setor.</li>
                )}
              </ul>
            )
          )}
        </AdminModal>
      )}
    </AdminPage>
  )
}
