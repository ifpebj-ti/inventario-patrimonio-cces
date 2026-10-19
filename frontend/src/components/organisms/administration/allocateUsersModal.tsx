'use client'

import { useCallback, useEffect, useRef, useState } from 'react'
import { toast } from 'sonner'
import { Button as UiButton } from '@/components/ui/button'
import { Checkbox as UiCheckbox } from '@/components/ui/checkbox'
import { Input as UiInput } from '@/components/ui/input'
import { Label as UiLabel } from '@/components/ui/label'
import { SelectField } from '@/components/ui/select-field'
import type {
  AssignableUser,
  ManagedUser,
  Role,
  Sector,
} from '@/commons/models/administration'
import { useAdministrationAccess } from '@/contexts/AdministrationAccess'
import {
  administrationFailure,
  allocateUsers,
  listAssignableUsers,
} from '@/services/administration'
import { AdminModal, ErrorNotice, useOperation } from './shared'

// Cargo de menos permissões: é o padrão ao alocar (o administrador ou gestor ajusta).
export const leastPrivilegedRole = (roles: Role[]) =>
  [...roles].sort(
    (a, b) => a.permissionIds.length - b.permissionIds.length || a.id - b.id,
  )[0]

/**
 * Escolhe um ou mais usuários do sistema (os ativos que ainda não estão no setor) e um cargo, e
 * aloca todos de uma vez. `roles` já vem filtrado pelo que quem está alocando pode atribuir.
 */
export const AllocateUsersModal = ({
  sector,
  roles,
  onClose,
  onAllocated,
}: {
  sector: Sector
  roles: Role[]
  onClose: () => void
  onAllocated: (members: ManagedUser[]) => void
}) => {
  const { refresh } = useAdministrationAccess()
  const [candidates, setCandidates] = useState<AssignableUser[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [selected, setSelected] = useState<number[]>([])
  const [roleId, setRoleId] = useState<number | null>(
    leastPrivilegedRole(roles)?.id ?? null,
  )
  const { busy, run } = useOperation()
  const request = useRef(0)

  const load = useCallback(async () => {
    const current = ++request.current
    setLoading(true)
    try {
      const rows = await listAssignableUsers(sector.id)
      if (current !== request.current) return
      setCandidates(rows)
      // Tira da seleção quem não é mais candidato (alocado por outra pessoa, por exemplo).
      setSelected((ids) =>
        ids.filter((id) => rows.some((row) => row.id === id)),
      )
    } catch (failure) {
      if (current === request.current)
        setError(administrationFailure(failure).message)
    } finally {
      if (current === request.current) setLoading(false)
    }
  }, [sector.id])
  useEffect(() => {
    load()
    return () => {
      request.current++
    }
  }, [load])

  const term = search.trim().toLocaleLowerCase()
  const filtered = candidates.filter(
    (user) =>
      term === '' ||
      `${user.name} ${user.email}`.toLocaleLowerCase().includes(term),
  )
  const allFilteredSelected =
    filtered.length > 0 && filtered.every((user) => selected.includes(user.id))
  const toggle = (id: number, checked: boolean) =>
    setSelected((ids) =>
      checked
        ? ids.includes(id)
          ? ids
          : [...ids, id]
        : ids.filter((v) => v !== id),
    )
  const toggleAll = (checked: boolean) =>
    setSelected((ids) =>
      checked
        ? [...new Set([...ids, ...filtered.map((user) => user.id)])]
        : ids.filter((id) => !filtered.some((user) => user.id === id)),
    )

  const submit = () =>
    run(async () => {
      if (roleId === null || selected.length === 0) return
      setError(null)
      try {
        const members = await allocateUsers(sector.id, selected, roleId)
        toast.success(
          selected.length === 1
            ? '1 usuário alocado.'
            : `${selected.length} usuários alocados.`,
        )
        onAllocated(members)
      } catch (failure) {
        const problem = administrationFailure(failure)
        setError(problem.message)
        if (problem.status === 403) await refresh()
        if (problem.status === 404 || problem.status === 400) await load()
      }
    })

  const count = selected.length
  return (
    <AdminModal
      title={`Adicionar usuários a ${sector.name}`}
      busy={busy}
      onClose={onClose}
    >
      <ErrorNotice message={error} />
      <UiLabel className="flex-col items-stretch gap-1.5 text-sm">
        Buscar usuário
        <UiInput
          type="search"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder="Nome ou e-mail"
          disabled={busy}
        />
      </UiLabel>

      <div className="space-y-2">
        <div className="flex items-center justify-between gap-3 text-sm">
          <UiLabel className="gap-2 font-medium">
            <UiCheckbox
              checked={allFilteredSelected}
              disabled={busy || filtered.length === 0}
              onCheckedChange={(checked) => toggleAll(checked === true)}
              aria-label="Selecionar todos os filtrados"
            />
            Selecionar todos os filtrados
          </UiLabel>
          <span role="status" className="text-muted-foreground">
            {count === 0
              ? 'Nenhum selecionado'
              : `${count} ${count === 1 ? 'selecionado' : 'selecionados'}`}
          </span>
        </div>
        {loading ? (
          <p role="status" className="text-sm text-muted-foreground">
            Carregando usuários...
          </p>
        ) : filtered.length === 0 ? (
          <p className="rounded-lg border border-dashed p-3 text-sm text-muted-foreground">
            {candidates.length === 0
              ? 'Não há usuários ativos disponíveis para alocar neste setor.'
              : 'Nenhum usuário encontrado para esta busca.'}
          </p>
        ) : (
          <ul className="max-h-64 space-y-1 overflow-y-auto rounded-lg border p-1">
            {filtered.map((user) => (
              <li key={user.id}>
                <UiLabel className="items-start gap-3 rounded-md p-2 text-sm hover:bg-muted/50">
                  <UiCheckbox
                    checked={selected.includes(user.id)}
                    disabled={busy}
                    onCheckedChange={(checked) =>
                      toggle(user.id, checked === true)
                    }
                    aria-label={`Selecionar ${user.name}`}
                  />
                  <span className="min-w-0">
                    <span className="block truncate font-medium">
                      {user.name}
                    </span>
                    <span className="block break-all font-normal text-muted-foreground">
                      {user.email}
                    </span>
                  </span>
                </UiLabel>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="space-y-1.5">
        <UiLabel className="flex-col items-stretch gap-1.5 text-sm">
          Cargo no setor
          <SelectField
            aria-label="Cargo dos usuários"
            options={roles.map((role) => ({
              value: String(role.id),
              label: role.name,
            }))}
            value={roleId === null ? '' : String(roleId)}
            onValueChange={(value) => setRoleId(Number(value))}
            placeholder="Selecione o cargo"
            disabled={busy}
          />
        </UiLabel>
        <p className="text-xs text-muted-foreground">
          Todos os selecionados entram com este cargo. O cargo vale só neste
          setor.
        </p>
      </div>

      <div className="flex justify-end gap-2 border-t pt-4">
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
          disabled={busy || count === 0 || roleId === null}
          onClick={submit}
        >
          {busy
            ? 'Alocando...'
            : `Alocar ${count} ${count === 1 ? 'usuário' : 'usuários'}`}
        </UiButton>
      </div>
    </AdminModal>
  )
}
