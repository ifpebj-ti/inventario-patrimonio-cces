'use client'

import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Info } from 'lucide-react'
import { profileLabel, permissionLabel } from '@/lib/access-labels'

import { cn } from '@/lib/utils'
import { chipToneClass } from '@/lib/tone'
import { Badge as UiBadge } from '@/components/ui/badge'
import { Card as UiCard } from '@/components/ui/card'

import { Label as UiLabel } from '@/components/ui/label'
import { Input as UiInput } from '@/components/ui/input'
import {
  Table as UiTable,
  TableHeader as UiTableHeader,
  TableRow as UiTableRow,
  TableHead as UiTableHead,
  TableBody as UiTableBody,
  TableCell as UiTableCell,
} from '@/components/ui/table'

import Link from 'next/link'
import { useCallback, useEffect, useState } from 'react'
import type { Permission, Profile } from '@/commons/models/administration'
import {
  administrationFailure,
  listPermissions,
  listProfiles,
} from '@/services/administration'
import { AdminPage, ErrorNotice } from './shared'

export const ProfilesScreen = () => {
  const [profiles, setProfiles] = useState<Profile[]>([])
  const [permissions, setPermissions] = useState<Permission[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const load = useCallback(async (signal?: AbortSignal) => {
    setLoading(true)
    setError(null)
    try {
      const [profileRows, permissionRows] = await Promise.all([
        listProfiles(signal),
        listPermissions(signal),
      ])
      if (!signal?.aborted) {
        setProfiles(profileRows)
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
  const visible = profiles.filter((profile) => {
    const assigned = permissions.filter((permission) =>
      profile.permissionIds.includes(permission.id),
    )
    return `${profileLabel(profile.name)} ${profile.description || ''} ${assigned.map((permission) => `${permission.name} ${permission.description || ''}`).join(' ')}`
      .toLocaleLowerCase('pt-BR')
      .includes(search.trim().toLocaleLowerCase('pt-BR'))
  })
  return (
    <AdminPage
      title="Perfis e permissões"
      description="Consulte os perfis de acesso e as permissões definidas pela política de segurança do sistema."
    >
      <Alert className="border-primary/25 bg-primary/10">
        <Info />
        <AlertTitle>Somente para consulta</AlertTitle>
        <AlertDescription>
          Os perfis e permissões são definidos pelo sistema e não podem ser
          alterados aqui. Para associar usuários aos perfis e setores, use{' '}
          <Link href="/users" className="font-semibold text-primary underline">
            Gerenciar usuários
          </Link>
          .
        </AlertDescription>
      </Alert>
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
        <UiLabel className="flex-1 text-sm">
          Buscar perfil ou permissão
          <UiInput
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Nome, descrição ou permissão"
          />
        </UiLabel>
      </div>
      <ErrorNotice message={error} retry={() => load()} />
      {loading ? (
        <p role="status">Carregando perfis e permissões...</p>
      ) : (
        !error && (
          <>
            <p className="text-sm text-muted-foreground">
              {profiles.length} perfis · {permissions.length} permissões
              disponíveis
            </p>
            <UiCard className="gap-0 overflow-x-auto py-0">
              <UiTable className="w-full text-left text-sm">
                <caption className="sr-only">
                  Perfis de acesso e suas permissões
                </caption>
                <UiTableHeader className="bg-muted/50">
                  <UiTableRow>
                    <UiTableHead scope="col" className="p-4">
                      Perfil
                    </UiTableHead>
                    <UiTableHead scope="col" className="p-4">
                      Descrição
                    </UiTableHead>
                    <UiTableHead scope="col" className="p-4">
                      Permissões associadas
                    </UiTableHead>
                  </UiTableRow>
                </UiTableHeader>
                <UiTableBody>
                  {visible.map((profile) => (
                    <UiTableRow key={profile.id} className="align-top">
                      <UiTableHead
                        scope="row"
                        className="p-4 font-semibold text-foreground"
                      >
                        {profileLabel(profile.name)}
                      </UiTableHead>
                      <UiTableCell className="p-4 text-muted-foreground">
                        {profile.description || 'Sem descrição'}
                      </UiTableCell>
                      <UiTableCell className="p-4">
                        {profile.permissionIds.length ? (
                          <ul className="space-y-3">
                            {profile.permissionIds.map((id) => {
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
                    </UiTableRow>
                  ))}
                </UiTableBody>
              </UiTable>
              {!visible.length && (
                <p className="p-6 text-muted-foreground">
                  Nenhum perfil encontrado para esta busca.
                </p>
              )}
            </UiCard>
          </>
        )
      )}
    </AdminPage>
  )
}
