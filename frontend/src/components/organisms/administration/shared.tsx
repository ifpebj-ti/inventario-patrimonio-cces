'use client'

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from '@/components/ui/dialog'
import { Alert, AlertDescription } from '@/components/ui/alert'
import { Button as UiButton } from '@/components/ui/button'
import { ErrorState } from '@/components/ui/error-state'
import { TriangleAlert } from 'lucide-react'
import { cn } from '@/lib/utils'

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
}) => {
  if (!message) return null
  // Falha ao carregar a tela: mesmo estado de erro das demais páginas.
  if (retry) return <ErrorState message={message} onRetry={retry} />
  // Falha numa ação (ex.: dentro de um formulário): aviso compacto.
  return (
    <Alert
      variant="destructive"
      className="border-destructive/25 bg-destructive/10"
    >
      <TriangleAlert />
      <AlertDescription>
        <p>{message}</p>
      </AlertDescription>
    </Alert>
  )
}

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

// Mesmo padrão da confirmação: cabeçalho com o título e uma divisória, e só então o conteúdo. O cabeçalho
// fica fixo e o conteúdo rola quando é maior que a tela.
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
      className="flex max-h-[90vh] flex-col gap-0 overflow-hidden rounded-xl p-0 sm:max-w-2xl"
    >
      <header className="flex shrink-0 items-center justify-between gap-3 border-b px-6 py-4">
        <DialogTitle className="min-w-0 text-lg leading-snug">
          {title}
        </DialogTitle>
        <UiButton
          variant="outline"
          disabled={busy || inactive}
          onClick={onClose}
          aria-label="Fechar diálogo"
        >
          Fechar
        </UiButton>
      </header>
      <div inert={inactive} className="space-y-4 overflow-y-auto px-6 py-5">
        {children}
      </div>
    </DialogContent>
  </Dialog>
)

/**
 * Confirmação de uma ação. `tone="destructive"` é para o que tira acesso ou desfaz algo (desativar,
 * desalocar): botão vermelho. O foco inicial cai em "Cancelar" (o primeiro botão),
 * então um Enter distraído não confirma. Esc e clique fora também cancelam, por isso não há "Fechar".
 */
export const AdminConfirmation = ({
  isOpen,
  title,
  message,
  details,
  confirmLabel = 'Confirmar',
  tone = 'default',
  onClose,
  onConfirm,
  busy = false,
}: {
  isOpen: boolean
  title: string
  message: ReactNode
  // Consequências da ação, uma por linha, abaixo da pergunta.
  details?: string[]
  confirmLabel?: string
  tone?: 'default' | 'destructive'
  onClose: () => void
  onConfirm: () => void
  busy?: boolean
}) => {
  // Desmontada quando fechada (sem animação de saída), para o texto não "piscar" sem ação.
  if (!isOpen) return null
  const destructive = tone === 'destructive'
  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && !busy) onClose()
      }}
    >
      <DialogContent
        showCloseButton={false}
        className="gap-0 overflow-hidden rounded-xl p-0 sm:max-w-md"
      >
        <header className="border-b px-6 py-4">
          <DialogTitle className="text-lg leading-snug">{title}</DialogTitle>
        </header>
        <DialogDescription asChild>
          <div className="space-y-3 px-6 py-5 text-sm text-muted-foreground">
            <p className="text-base text-foreground">{message}</p>
            {details && details.length > 0 && (
              <ul className="list-disc space-y-1.5 pl-5">
                {details.map((detail) => (
                  <li key={detail}>{detail}</li>
                ))}
              </ul>
            )}
          </div>
        </DialogDescription>
        <div className="flex flex-col-reverse gap-2 border-t bg-muted/40 px-6 py-4 sm:flex-row sm:justify-end">
          <UiButton
            variant="outline"
            type="button"
            onClick={onClose}
            disabled={busy}
          >
            Cancelar
          </UiButton>
          <UiButton
            variant={destructive ? 'destructive' : 'default'}
            type="button"
            onClick={onConfirm}
            disabled={busy}
            // O app não tem tema escuro: sem isto o `dark:` do botão destrutivo o deixa desbotado
            // quando o sistema do usuário está em modo escuro.
            className={cn(
              destructive && 'dark:bg-destructive dark:hover:bg-destructive/90',
            )}
          >
            {busy ? 'Aguarde...' : confirmLabel}
          </UiButton>
        </div>
      </DialogContent>
    </Dialog>
  )
}

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
