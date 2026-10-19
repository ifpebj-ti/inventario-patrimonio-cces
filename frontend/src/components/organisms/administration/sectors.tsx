'use client'

import { SelectField } from '@/components/ui/select-field'
import { Plus, CheckCircle2, Clock3 } from 'lucide-react'
import { ListFilters } from '@/components/organisms/listFilters'
import { StatusBadge } from '@/components/ui/status-badge'
import { Label as UiLabel } from '@/components/ui/label'
import { Input as UiInput } from '@/components/ui/input'
import { Button as UiButton } from '@/components/ui/button'

import { useCallback, useEffect, useState } from 'react'
import { toast } from 'sonner'
import { sectorFormSchema, type Sector } from '@/commons/models/administration'
import { useAdministrationAccess } from '@/contexts/AdministrationAccess'
import {
  administrationFailure,
  deleteSector,
  listSectors,
  saveSector,
  setSectorActive,
} from '@/services/administration'
import { SectorMembersSheet } from './sectorMembersSheet'
import {
  AdminConfirmation,
  AdminModal,
  AdminPage,
  ErrorNotice,
  useOperation,
} from './shared'

export const SectorsScreen = () => {
  const { isAdmin, managedSectorIds, refresh } = useAdministrationAccess()
  const [sectors, setSectors] = useState<Sector[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [filter, setFilter] = useState('all')
  const [editing, setEditing] = useState<Sector | 'new' | null>(null)
  const [form, setForm] = useState({ name: '', code: '' })
  const [formError, setFormError] = useState<string | null>(null)
  const [activation, setActivation] = useState<Sector | null>(null)
  const [removal, setRemoval] = useState<Sector | null>(null)
  // Setor cuja barra lateral de usuários está aberta.
  const [viewing, setViewing] = useState<Sector | null>(null)
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
  // Atualiza a lista em silêncio (sem a tela de carregando): usada depois de mexer nos usuários do
  // setor pela barra lateral, porque isso muda se o setor ainda pode ser excluído.
  const refreshSectors = useCallback(async () => {
    try {
      setSectors(await listSectors())
    } catch {
      // A lista segue como estava; o próximo carregamento mostra o erro, se persistir.
    }
  }, [])

  const openEditor = (sector: Sector | 'new') => {
    setEditing(sector)
    setForm(
      sector === 'new'
        ? { name: '', code: '' }
        : { name: sector.name, code: sector.code || '' },
    )
    setFormError(null)
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
  const removeSector = () =>
    run(async () => {
      if (!isAdmin || !removal) return
      try {
        await deleteSector(removal.id)
        setSectors((rows) => rows.filter((row) => row.id !== removal.id))
        setRemoval(null)
        toast.success('Setor excluído.')
      } catch (failure) {
        const problem = administrationFailure(
          failure,
          'O setor possui usuários ou inventários associados e não pode ser excluído. Desative-o em vez disso.',
        )
        setRemoval(null)
        // Aviso em toast, para a lista continuar visível enquanto é atualizada.
        toast.error(problem.message)
        if (problem.status === 403) await refresh()
        // 404/409: a lista local está desatualizada (setor removido ou com novo vínculo).
        if (problem.status === 404 || problem.status === 409) await load()
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
      description="Consulte os setores e gerencie quem está em cada um, com o cargo de cada usuário. Setores com usuários ou inventários associados só podem ser ativados ou desativados."
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
                    {/* Só quem gerencia o setor vê e altera os usuários dele. */}
                    {(isAdmin || managedSectorIds.includes(sector.id)) && (
                      <UiButton
                        variant="outline"
                        type="button"
                        onClick={() => setViewing(sector)}
                      >
                        Usuários associados
                      </UiButton>
                    )}
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
                        {sector.deletable && (
                          <UiButton
                            variant="outline"
                            type="button"
                            disabled={busy}
                            onClick={() => setRemoval(sector)}
                          >
                            Excluir
                          </UiButton>
                        )}
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
        confirmLabel={activation?.active ? 'Desativar' : 'Ativar'}
        tone={activation?.active ? 'destructive' : 'default'}
        message={
          <>
            Deseja {activation?.active ? 'desativar' : 'ativar'} o setor{' '}
            <strong className="font-semibold">{activation?.name}</strong>?
          </>
        }
        details={['As alocações existentes serão mantidas.']}
      />
      <AdminConfirmation
        isOpen={!!removal}
        onClose={() => setRemoval(null)}
        onConfirm={removeSector}
        busy={busy}
        title="Excluir setor"
        confirmLabel="Excluir"
        tone="destructive"
        message={
          <>
            Deseja excluir o setor{' '}
            <strong className="font-semibold">{removal?.name}</strong>?
          </>
        }
        details={[
          'Esta ação não pode ser desfeita.',
          'Só é possível excluir setor sem usuários e sem inventários associados.',
        ]}
      />
      {viewing && (
        <SectorMembersSheet
          sector={viewing}
          onClose={() => setViewing(null)}
          onChanged={refreshSectors}
        />
      )}
    </AdminPage>
  )
}
