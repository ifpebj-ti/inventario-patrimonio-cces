'use client'

import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button as UiButton } from '@/components/ui/button'

import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { useAdministrationAccess } from '@/contexts/AdministrationAccess'
import { useAuth } from '@/hooks/useAuth'
import { homeRoute } from '@/lib/home-route'

export const ErrorNotice = ({
  message,
  retry,
}: {
  message: string | null
  retry?: () => void
}) =>
  message ? (
    <Alert
      variant="destructive"
      className="border-destructive/25 bg-destructive/10"
    >
      <AlertDescription>
        <p>{message}</p>
        {retry && (
          <UiButton
            variant="link"
            type="button"
            onClick={retry}
            className="h-auto p-0 text-destructive"
          >
            Tentar novamente
          </UiButton>
        )}
      </AlertDescription>
    </Alert>
  ) : null

export const AccessGate = ({
  admin = false,
  users = false,
  redirectToHome = false,
  children,
}: {
  admin?: boolean
  // Telas de usuários: admin e gestor de setor.
  users?: boolean
  // Em vez de mostrar "Acesso restrito", leva o usuário à sua tela inicial.
  redirectToHome?: boolean
  children: ReactNode
}) => {
  const access = useAdministrationAccess()
  const { user } = useAuth()
  const router = useRouter()
  const home = homeRoute(user?.permissions)
  const allowed = admin
    ? access.isAdmin
    : users
      ? access.canManageUsers
      : access.canReadSectors
  const shouldRedirect =
    redirectToHome && !access.loading && !access.error && !allowed
  useEffect(() => {
    if (shouldRedirect) router.replace(home)
  }, [shouldRedirect, home, router])
  if (shouldRedirect) return null
  if (access.loading && !allowed)
    return (
      <p role="status" className="p-6">
        Verificando acesso...
      </p>
    )
  if (access.error)
    return (
      <div className="p-6">
        <ErrorNotice
          message={access.error}
          retry={() => {
            access.refresh()
          }}
        />
      </div>
    )
  if (!allowed)
    return (
      <div className="page-shell space-y-3">
        <h1 className="page-heading">Acesso restrito</h1>
        <p>Você não tem permissão para acessar esta tela.</p>
        <UiButton variant="link" asChild className="h-auto p-0">
          <Link href={home}>
            {home === '/dashboard' ? 'Voltar ao painel' : 'Ir para Inventários'}
          </Link>
        </UiButton>
      </div>
    )
  return (
    <div inert={access.loading}>
      {access.loading && (
        <p role="status" className="px-6 pt-4">
          Atualizando acesso...
        </p>
      )}
      {children}
    </div>
  )
}

export const AdminPage = ({
  title,
  description,
  action,
  children,
}: {
  title: string
  description: string
  action?: ReactNode
  children: ReactNode
}) => (
  <div className="page-shell space-y-5">
    <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h1 className="page-heading">{title}</h1>
        <p className="page-description">{description}</p>
      </div>
      {action && <div className="flex shrink-0 justify-end">{action}</div>}
    </header>
    {children}
  </div>
)

export const AdminModal = ({
  title,
  children,
  onClose,
  busy = false,
  inactive = false,
}: {
  title: string
  children: ReactNode
  onClose: () => void
  busy?: boolean
  inactive?: boolean
}) => (
  <Dialog
    open
    onOpenChange={(open) => {
      if (!open && !(busy || inactive)) onClose()
    }}
  >
    <DialogContent
      showCloseButton={false}
      className="flex max-h-[90vh] flex-col overflow-y-auto rounded-xl gap-4 sm:max-w-2xl"
    >
      <DialogTitle className="sr-only">{title}</DialogTitle>
      <div className="flex justify-between gap-3">
        <h2 className="text-xl font-semibold">{title}</h2>
        <UiButton
          variant="outline"
          disabled={busy || inactive}
          onClick={onClose}
          aria-label="Fechar diálogo"
        >
          Fechar
        </UiButton>
      </div>
      <div inert={inactive}>{children}</div>
    </DialogContent>
  </Dialog>
)

export const AdminConfirmation = ({
  isOpen,
  title,
  message,
  onClose,
  onConfirm,
  busy = false,
}: {
  isOpen: boolean
  title: string
  message: string
  onClose: () => void
  onConfirm: () => void
  busy?: boolean
}) =>
  isOpen ? (
    <AdminModal title={title} onClose={onClose} busy={busy}>
      <p className="text-sm text-muted-foreground">{message}</p>
      <div className="flex justify-end gap-3">
        <UiButton
          variant="outline"
          type="button"
          onClick={onClose}
          disabled={busy}
        >
          Cancelar
        </UiButton>
        <UiButton type="button" onClick={onConfirm} disabled={busy}>
          {busy ? 'Aguarde...' : 'Confirmar'}
        </UiButton>
      </div>
    </AdminModal>
  ) : null

export const useOperation = () => {
  const [busy, setBusy] = useState(false)
  const lock = useRef(false)
  const run = async (operation: () => Promise<void>) => {
    if (lock.current) return
    lock.current = true
    setBusy(true)
    try {
      await operation()
    } finally {
      lock.current = false
      setBusy(false)
    }
  }
  return { busy, run }
}
