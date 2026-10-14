'use client'

import { SelectField } from '@/components/ui/select-field'
import { profileLabel, permissionLabel } from '@/lib/access-labels'

import { cn } from '@/lib/utils'
import { chipToneClass } from '@/lib/tone'
import { Badge as UiBadge } from '@/components/ui/badge'
import { Checkbox } from '@/components/ui/checkbox'

import { Label as UiLabel } from '@/components/ui/label'
import { Input as UiInput } from '@/components/ui/input'
import { Button as UiButton } from '@/components/ui/button'

import { useState, type FormEvent } from 'react'
import {
  userFormSchema,
  type Permission,
  type Profile,
  type Sector,
} from '@/commons/models/administration'
import { createUser, administrationFailure } from '@/services/administration'
import { useAdministrationAccess } from '@/contexts/AdministrationAccess'
import { AdminModal, ErrorNotice, useOperation } from './shared'

export const CreateUserModal = ({
  profiles,
  permissions,
  sectors,
  onClose,
  onCreated,
}: {
  profiles: Profile[]
  permissions: Permission[]
  sectors: Sector[]
  onClose: () => void
  onCreated: () => void
}) => {
  const access = useAdministrationAccess()
  const { busy, run } = useOperation()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [profileId, setProfileId] = useState('')
  const [sectorIds, setSectorIds] = useState<number[]>([])
  const [error, setError] = useState<string | null>(null)
  const selectedProfile = profiles.find((p) => p.id === Number(profileId))
  const activeSectors = sectors.filter((sector) => sector.active)
  const submit = (event: FormEvent) => {
    event.preventDefault()
    run(async () => {
      if (!access.canManageUsers) return
      setError(null)
      if (!profileId) {
        setError('Selecione o perfil de acesso.')
        return
      }
      // O gestor só cadastra usuários dentro dos seus setores.
      if (!access.isAdmin && sectorIds.length === 0) {
        setError('Selecione ao menos um setor para o novo usuário.')
        return
      }
      const data = userFormSchema.safeParse({
        name,
        email,
        profileId: Number(profileId),
        sectorIds,
      })
      if (!data.success) {
        setError(data.error.issues[0].message)
        return
      }
      try {
        await createUser(data.data)
        onCreated()
      } catch (failure) {
        const problem = administrationFailure(failure)
        setError(problem.message)
        if (problem.status === 403) await access.refresh()
      }
    })
  }
  return (
    <AdminModal title="Novo usuário" busy={busy} onClose={onClose}>
      <p className="text-sm text-muted-foreground">
        Cadastre o e-mail que será usado para entrar com Google. O perfil e os
        setores já estarão associados na primeira entrada.
      </p>
      <ErrorNotice message={error} />
      <form onSubmit={submit} className="space-y-4">
        <UiLabel className="flex-col items-stretch gap-1.5 text-sm">
          E-mail do usuário
          <UiInput
            type="email"
            required
            maxLength={255}
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            disabled={busy}
            autoComplete="email"
          />
        </UiLabel>
        <UiLabel className="flex-col items-stretch gap-1.5 text-sm">
          Nome (opcional)
          <UiInput
            value={name}
            maxLength={100}
            onChange={(event) => setName(event.target.value)}
            disabled={busy}
            autoComplete="name"
          />
        </UiLabel>
        <UiLabel className="flex-col items-stretch gap-1.5 text-sm">
          Perfil de acesso
          <SelectField
            aria-label="Perfil de acesso"
            options={profiles.map((profile) => ({
              value: String(profile.id),
              label: profileLabel(profile.name),
            }))}
            value={profileId}
            onValueChange={setProfileId}
            placeholder="Selecione o perfil"
            disabled={busy}
          />
        </UiLabel>
        <section
          aria-label="Permissões do perfil"
          className="rounded-xl bg-muted p-4 space-y-2"
        >
          <h3 className="font-semibold">
            Permissões disponíveis ({permissions.length})
          </h3>
          <p className="text-sm text-muted-foreground">
            As permissões são fixas e definidas pelo perfil selecionado.{' '}
            {selectedProfile?.permissionIds.length || 0} permissões serão
            atribuídas ao usuário.
          </p>
          <ul className="space-y-3">
            {permissions.map((permission) => {
              const assigned =
                selectedProfile?.permissionIds.includes(permission.id) || false
              return (
                <li key={permission.id} className="text-sm">
                  <div className="flex items-center gap-2">
                    <strong>{permissionLabel(permission.name)}</strong>
                    <UiBadge
                      variant="outline"
                      className={cn(
                        chipToneClass[assigned ? 'primary' : 'neutral'],
                      )}
                    >
                      {assigned ? 'Incluída' : 'Não incluída'}
                    </UiBadge>
                  </div>
                  {permission.description && (
                    <p className="mt-1 text-muted-foreground">
                      {permission.description}
                    </p>
                  )}
                </li>
              )
            })}
          </ul>
        </section>
        <fieldset className="space-y-2" disabled={busy}>
          <legend className="font-semibold">Associar aos setores</legend>
          <p className="text-sm text-muted-foreground">
            Selecione um ou mais setores ativos já cadastrados.
          </p>
          <div className="grid gap-2 sm:grid-cols-2">
            {activeSectors.map((sector) => (
              <UiLabel
                key={sector.id}
                className="flex items-center gap-2 text-sm"
              >
                <Checkbox
                  checked={sectorIds.includes(sector.id)}
                  onCheckedChange={(checked) =>
                    setSectorIds((ids) =>
                      checked
                        ? [...ids, sector.id]
                        : ids.filter((id) => id !== sector.id),
                    )
                  }
                />
                {sector.name}
              </UiLabel>
            ))}
          </div>
          {!activeSectors.length && (
            <p className="text-sm text-muted-foreground">
              {access.isAdmin
                ? 'Nenhum setor ativo disponível para associar.'
                : 'Você ainda não está vinculado a nenhum setor ativo. Peça a um administrador para vinculá-lo a um setor antes de cadastrar usuários.'}
            </p>
          )}
        </fieldset>
        <div className="flex justify-end gap-3">
          <UiButton
            variant="outline"
            type="button"
            disabled={busy}
            onClick={onClose}
          >
            Cancelar
          </UiButton>
          <UiButton
            type="submit"
            disabled={busy || !email.trim() || !profileId}
          >
            {busy ? 'Cadastrando...' : 'Cadastrar usuário'}
          </UiButton>
        </div>
      </form>
    </AdminModal>
  )
}
