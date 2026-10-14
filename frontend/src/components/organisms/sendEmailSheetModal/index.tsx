'use client'

import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog'
import { Button as UiButton } from '@/components/ui/button'

import { Label as UiLabel } from '@/components/ui/label'
import { Input as UiInput } from '@/components/ui/input'
import { Textarea as UiTextarea } from '@/components/ui/textarea'

import { useAuth } from '@/hooks/useAuth'
import { useState } from 'react'
import sendEmail from '@/validations/sendEmail'
import type { SendEmailSheetRequest } from '@/services/item'

type Props = {
  onClose: () => void
  onSendEmail?: (data: SendEmailSheetRequest) => Promise<void>
}
export function SendEmailSheetModal({ onClose, onSendEmail }: Props) {
  const { user } = useAuth()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [form, setForm] = useState({
    email: user?.email || '',
    subject: '',
    message: '',
  })
  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && !busy) onClose()
      }}
    >
      <DialogContent
        showCloseButton={false}
        className="flex max-h-[90vh] flex-col overflow-y-auto rounded-xl gap-0 p-6 sm:max-w-lg sm:p-8"
      >
        <DialogTitle className="sr-only">
          Enviar planilha por e-mail
        </DialogTitle>
        <h2 className="text-xl font-bold text-foreground">
          Enviar planilha por e-mail
        </h2>
        <p className="text-sm text-muted-foreground mt-2 mb-5">
          A planilha será enviada pela conta do Inventarium. Seu e-mail já está
          preenchido como destinatário; você pode alterá-lo.
        </p>
        <form
          onSubmit={async (event) => {
            event.preventDefault()
            if (busy || !onSendEmail) return
            const validation = sendEmail.safeParse(form)
            if (!validation.success) {
              setError(validation.error.issues[0].message)
              return
            }
            setBusy(true)
            setError('')
            try {
              await onSendEmail(validation.data)
              onClose()
            } catch (failure) {
              setError(
                failure instanceof Error
                  ? failure.message
                  : 'Não foi possível enviar.',
              )
            } finally {
              setBusy(false)
            }
          }}
          className="space-y-4"
        >
          <fieldset disabled={busy} className="space-y-4">
            <UiLabel className="flex-col items-stretch gap-1.5 text-sm font-medium text-foreground">
              Destinatário
              <UiInput
                type="email"
                required
                value={form.email}
                onChange={(event) =>
                  setForm({ ...form, email: event.target.value })
                }
              />
            </UiLabel>
            <UiLabel className="flex-col items-stretch gap-1.5 text-sm font-medium text-foreground">
              Assunto
              <UiInput
                required
                value={form.subject}
                onChange={(event) =>
                  setForm({ ...form, subject: event.target.value })
                }
              />
            </UiLabel>
            <UiLabel className="flex-col items-stretch gap-1.5 text-sm font-medium text-foreground">
              Descrição
              <UiTextarea
                rows={3}
                value={form.message}
                onChange={(event) =>
                  setForm({ ...form, message: event.target.value })
                }
              />
            </UiLabel>
          </fieldset>
          {error && (
            <p role="alert" className="text-sm text-destructive">
              {error}
            </p>
          )}
          <div className="flex justify-end gap-3 pt-3">
            <UiButton
              variant={'outline'}
              className="w-auto"
              type="button"
              disabled={busy}
              onClick={onClose}
            >
              Cancelar
            </UiButton>
            <UiButton
              variant="default"
              className="w-auto"
              type="submit"
              disabled={busy}
            >
              {busy ? 'Enviando...' : 'Enviar planilha'}
            </UiButton>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}
