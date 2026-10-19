'use client'

import { Plus } from 'lucide-react'
import { permissionLabel } from '@/lib/access-labels'

import { cn } from '@/lib/utils'
import { chipToneClass } from '@/lib/tone'
import { Badge as UiBadge } from '@/components/ui/badge'
import { Button as UiButton } from '@/components/ui/button'
import { Card as UiCard } from '@/components/ui/card'
import { Checkbox as UiCheckbox } from '@/components/ui/checkbox'
import { Label as UiLabel } from '@/components/ui/label'
import { Input as UiInput } from '@/components/ui/input'
import { Textarea as UiTextarea } from '@/components/ui/textarea'
import {
  Table as UiTable,
  TableHeader as UiTableHeader,
  TableRow as UiTableRow,
  TableHead as UiTableHead,
  TableBody as UiTableBody,
  TableCell as UiTableCell,
} from '@/components/ui/table'

import { useCallback, useEffect, useState } from 'react'
import { toast } from 'sonner'
import {
  roleFormSchema,
  type Permission,
  type Role,
} from '@/commons/models/administration'
import { useAdministrationAccess } from '@/contexts/AdministrationAccess'
import {
  administrationFailure,
  deleteRole,
  listPermissions,
  listRoles,
  saveRole,
} from '@/services/administration'
import {
  AdminConfirmation,
  AdminModal,
  AdminModalFooter,
  AdminModalSection,
  AdminPage,
  ErrorNotice,
  useOperation,
} from './shared'

type RoleForm = { name: string; description: string; permissionIds: number[] }

const usage = (role: Role) =>
  role.allocationCount === 0
    ? 'Sem uso'
    : `${role.userCount} ${role.userCount === 1 ? 'usuário' : 'usuários'} em ${role.allocationCount} ${role.allocationCount === 1 ? 'alocação' : 'alocações'}`

const sameIds = (a: number[], b: number[]) =>
  a.length === b.length && a.every((id) => b.includes(id))

export const RolesScreen = () => {
  const { isAdmin, refresh } = useAdministrationAccess()
  const [roles, setRoles] = useState<Role[]>([])
  const [permissions, setPermissions] = useState<Permission[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [editing, setEditing] = useState<Role | 'new' | null>(null)
  const [form, setForm] = useState<RoleForm>({
    name: '',
    description: '',
    permissionIds: [],
  })
  const [formError, setFormError] = useState<string | null>(null)
  // Alteração de permissões de um cargo em uso: pede confirmação antes de salvar.
  const [pendingSave, setPendingSave] = useState<RoleForm | null>(null)
  const [removal, setRemoval] = useState<Role | null>(null)
  const { busy, run } = useOperation()

  const load = useCallback(async (signal?: AbortSignal) => {
    setLoading(true)
    setError(null)
    try {
      const [roleRows, permissionRows] = await Promise.all([
        listRoles(signal),
        listPermissions(signal),
      ])
      if (!signal?.aborted) {
        setRoles(roleRows)
        setPermissions(permissionRows)
      }
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

  const openEditor = (role: Role | 'new') => {
    setEditing(role)
    setForm(
      role === 'new'
        ? { name: '', description: '', permissionIds: [] }
        : {
            name: role.name,
            description: role.description || '',
            permissionIds: role.permissionIds,
          },
    )
    setFormError(null)
  }
  const toggle = (id: number, checked: boolean) =>
    setForm((current) => ({
      ...current,
      permissionIds: checked
        ? [...current.permissionIds, id]
        : current.permissionIds.filter((value) => value !== id),
    }))

  const persist = (data: RoleForm) =>
    run(async () => {
      if (!isAdmin || !editing) return
      setPendingSave(null)
      try {
        const result = await saveRole(
          data,
          editing === 'new' ? undefined : editing.id,
        )
        setRoles((rows) => [
          ...rows.filter((row) => row.id !== result.id),
          result,
        ])
        setEditing(null)
        toast.success('Cargo salvo com sucesso.')
      } catch (failure) {
        const problem = administrationFailure(failure)
        setFormError(problem.message)
        if (problem.status === 403) await refresh()
      }
    })
  const submit = () => {
    if (!isAdmin || !editing) return
    const parsed = roleFormSchema.safeParse(form)
    if (!parsed.success) {
      setFormError(parsed.error.issues[0].message)
      return
    }
    if (
      roles.some(
        (role) =>
          role.name === parsed.data.name &&
          (editing === 'new' || role.id !== editing.id),
      )
    ) {
      setFormError('Já existe um cargo com este nome.')
      return
    }
    setFormError(null)
    if (
      editing !== 'new' &&
      editing.allocationCount > 0 &&
      !sameIds(editing.permissionIds, parsed.data.permissionIds)
    ) {
      setPendingSave(parsed.data)
      return
    }
    persist(parsed.data)
  }
  const removeRole = () =>
    run(async () => {
      if (!isAdmin || !removal) return
      try {
        await deleteRole(removal.id)
        setRoles((rows) => rows.filter((row) => row.id !== removal.id))
        setRemoval(null)
        toast.success('Cargo excluído.')
      } catch (failure) {
        const problem = administrationFailure(
          failure,
          'O cargo está em uso ou possui permissões e não pode ser excluído.',
        )
        setRemoval(null)
        toast.error(problem.message)
        if (problem.status === 403) await refresh()
        if (problem.status === 404 || problem.status === 409) await load()
      }
    })

  // Rodapé do modal de cargo: ao editar, quantos campos mudaram; ao criar, o que já está marcado.
  const editorChanges =
    editing && editing !== 'new'
      ? (form.name !== editing.name ? 1 : 0) +
        (form.description !== (editing.description || '') ? 1 : 0) +
        (sameIds(form.permissionIds, editing.permissionIds) ? 0 : 1)
      : 0
  const editorStatus =
    editing === 'new'
      ? form.name.trim()
        ? form.permissionIds.length === 0
          ? 'Nenhuma permissão selecionada'
          : `${form.permissionIds.length} ${form.permissionIds.length === 1 ? 'permissão selecionada' : 'permissões selecionadas'}`
        : 'Informe o nome do cargo'
      : editorChanges === 0
        ? 'Nenhuma alteração pendente'
        : `${editorChanges} ${editorChanges === 1 ? 'alteração pendente' : 'alterações pendentes'}`
  const visible = roles
    .filter((role) => {
      const assigned = permissions.filter((permission) =>
        role.permissionIds.includes(permission.id),
      )
      return `${role.name} ${role.description || ''} ${assigned.map((permission) => `${permissionLabel(permission.name)} ${permission.description || ''}`).join(' ')}`
        .toLocaleLowerCase('pt-BR')
        .includes(search.trim().toLocaleLowerCase('pt-BR'))
    })
    .sort((a, b) => a.name.localeCompare(b.name, 'pt-BR'))

  return (
    <AdminPage
      title="Cargos e permissões"
      description="Crie os cargos e escolha as permissões de cada um. O cargo do usuário vale por setor: é escolhido ao alocá-lo em um setor, em Usuários."
      action={
        isAdmin ? (
          <UiButton
            type="button"
            disabled={busy || loading || !!error}
            onClick={() => openEditor('new')}
          >
            <Plus /> Novo cargo
          </UiButton>
        ) : undefined
      }
    >
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
        <UiLabel className="flex-1 text-sm">
          Buscar cargo ou permissão
          <UiInput
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Nome, descrição ou permissão"
          />
        </UiLabel>
      </div>
      <ErrorNotice message={error} retry={() => load()} />
      {loading ? (
        <p role="status">Carregando cargos e permissões...</p>
      ) : (
        !error && (
          <>
            <p className="text-sm text-muted-foreground">
              {roles.length} cargos · {permissions.length} permissões
              disponíveis
            </p>
            <UiCard className="gap-0 overflow-x-auto py-0">
              <UiTable className="w-full text-left text-sm">
                <caption className="sr-only">Cargos e suas permissões</caption>
                <UiTableHeader className="bg-muted/50">
                  <UiTableRow>
                    <UiTableHead scope="col" className="p-4">
                      Cargo
                    </UiTableHead>
                    <UiTableHead scope="col" className="p-4">
                      Permissões associadas
                    </UiTableHead>
                    <UiTableHead scope="col" className="p-4">
                      Uso
                    </UiTableHead>
                    <UiTableHead scope="col" className="p-4">
                      Ações
                    </UiTableHead>
                  </UiTableRow>
                </UiTableHeader>
                <UiTableBody>
                  {visible.map((role) => (
                    <UiTableRow key={role.id} className="align-top">
                      <UiTableHead scope="row" className="p-4">
                        <span className="block font-semibold text-foreground">
                          {role.name}
                        </span>
                        <span className="block font-normal text-muted-foreground">
                          {role.description || 'Sem descrição'}
                        </span>
                      </UiTableHead>
                      <UiTableCell className="p-4">
                        {role.permissionIds.length ? (
                          <ul className="space-y-3">
                            {role.permissionIds.map((id) => {
                              const permission = permissions.find(
                                (row) => row.id === id,
                              )
                              return (
                                <li key={id}>
                                  <UiBadge
                                    variant="outline"
                                    className={cn(chipToneClass.primary)}
                                  >
                                    {permission
                                      ? permissionLabel(permission.name)
                                      : `Permissão #${id}`}
                                  </UiBadge>
                                  {permission?.description && (
                                    <p className="mt-1 text-muted-foreground">
                                      {permission.description}
                                    </p>
                                  )}
                                </li>
                              )
                            })}
                          </ul>
                        ) : (
                          <span className="text-muted-foreground">
                            Nenhuma permissão atribuída
                          </span>
                        )}
                      </UiTableCell>
                      <UiTableCell className="p-4 text-muted-foreground">
                        {usage(role)}
                      </UiTableCell>
                      <UiTableCell className="p-4">
                        {isAdmin && (
                          <div className="flex flex-wrap gap-2">
                            <UiButton
                              variant="outline"
                              type="button"
                              disabled={busy}
                              aria-label={`Editar cargo ${role.name}`}
                              onClick={() => openEditor(role)}
                            >
                              Editar
                            </UiButton>
                            {role.deletable && (
                              <UiButton
                                variant="outline"
                                type="button"
                                disabled={busy}
                                aria-label={`Excluir cargo ${role.name}`}
                                onClick={() => setRemoval(role)}
                              >
                                Excluir
                              </UiButton>
                            )}
                          </div>
                        )}
                      </UiTableCell>
                    </UiTableRow>
                  ))}
                </UiTableBody>
              </UiTable>
              {!visible.length && (
                <p className="p-6 text-muted-foreground">
                  Nenhum cargo encontrado para esta busca.
                </p>
              )}
            </UiCard>
          </>
        )
      )}
      {editing && (
        <AdminModal
          title={editing === 'new' ? 'Novo cargo' : 'Editar cargo'}
          busy={busy}
          inactive={!!pendingSave}
          onClose={() => setEditing(null)}
          footer={
            <AdminModalFooter status={editorStatus}>
              <UiButton
                variant="outline"
                type="button"
                disabled={busy}
                onClick={() => setEditing(null)}
              >
                Cancelar
              </UiButton>
              <UiButton
                type="submit"
                form="role-form"
                disabled={busy || (editing !== 'new' && editorChanges === 0)}
              >
                {busy ? 'Salvando...' : 'Salvar cargo'}
              </UiButton>
            </AdminModalFooter>
          }
        >
          <ErrorNotice message={formError} />
          {editing !== 'new' && editing.allocationCount > 0 && (
            <p className="rounded-md border bg-muted/40 p-3 text-sm text-muted-foreground">
              Este cargo é usado por {usage(editing)}. Mudar as permissões vale
              na hora para todos eles.
            </p>
          )}
          <form
            id="role-form"
            onSubmit={(event) => {
              event.preventDefault()
              submit()
            }}
            className="space-y-6"
          >
            <AdminModalSection
              title="Dados do cargo"
              description="O nome aparece ao alocar um usuário em um setor."
            >
              <UiLabel className="flex-col items-stretch gap-1.5 text-sm">
                Nome do cargo
                <UiInput
                  autoFocus
                  value={form.name}
                  onChange={(event) =>
                    setForm({ ...form, name: event.target.value })
                  }
                  maxLength={100}
                  required
                  disabled={busy}
                />
              </UiLabel>
              <UiLabel className="flex-col items-stretch gap-1.5 text-sm">
                Descrição (opcional)
                <UiTextarea
                  value={form.description}
                  onChange={(event) =>
                    setForm({ ...form, description: event.target.value })
                  }
                  maxLength={255}
                  disabled={busy}
                />
              </UiLabel>
            </AdminModalSection>
            <AdminModalSection
              divided
              title="Permissões"
              description="O que o cargo pode fazer no setor em que o usuário estiver alocado. Sem nenhuma permissão, o cargo é somente leitura."
            >
              <fieldset className="space-y-2" disabled={busy}>
                <legend className="sr-only">Permissões do cargo</legend>
                {permissions.map((permission) => (
                  <UiLabel
                    key={permission.id}
                    className="items-start gap-3 rounded-lg border p-3 text-sm"
                  >
                    <UiCheckbox
                      checked={form.permissionIds.includes(permission.id)}
                      onCheckedChange={(checked) =>
                        toggle(permission.id, checked === true)
                      }
                      aria-label={permissionLabel(permission.name)}
                    />
                    <span>
                      <span className="block font-medium">
                        {permissionLabel(permission.name)}
                      </span>
                      {permission.description && (
                        <span className="block font-normal text-muted-foreground">
                          {permission.description}
                        </span>
                      )}
                    </span>
                  </UiLabel>
                ))}
              </fieldset>
            </AdminModalSection>
          </form>
        </AdminModal>
      )}
      <AdminConfirmation
        isOpen={!!pendingSave}
        onClose={() => setPendingSave(null)}
        onConfirm={() => pendingSave && persist(pendingSave)}
        busy={busy}
        title="Alterar permissões do cargo"
        confirmLabel="Salvar"
        tone="destructive"
        message={
          <>
            Deseja alterar as permissões do cargo{' '}
            <strong className="font-semibold">
              {editing && editing !== 'new' ? editing.name : ''}
            </strong>
            ?
          </>
        }
        details={[
          editing && editing !== 'new'
            ? `Ele é usado por ${usage(editing)}.`
            : '',
          'A mudança vale na hora para todos eles, inclusive se tirar uma permissão.',
        ].filter(Boolean)}
      />
      <AdminConfirmation
        isOpen={!!removal}
        onClose={() => setRemoval(null)}
        onConfirm={removeRole}
        busy={busy}
        title="Excluir cargo"
        confirmLabel="Excluir"
        tone="destructive"
        message={
          <>
            Deseja excluir o cargo{' '}
            <strong className="font-semibold">{removal?.name}</strong>?
          </>
        }
        details={[
          'Esta ação não pode ser desfeita.',
          'Só é possível excluir cargo sem usuários e sem permissões.',
        ]}
      />
    </AdminPage>
  )
}
