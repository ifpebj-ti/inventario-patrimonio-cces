import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button as UiButton } from '@/components/ui/button'
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import type { SpreadsheetError } from '@/commons/models/spreadsheet'

// Define as propriedades que o modal de erros espera receber.
interface ModalErrorsProps {
  onClose: () => void // Função para fechar o modal.
  title?: string // Título opcional para o modal.
  errors: SpreadsheetError[] | null // Array de erros a serem exibidos.
}

// Componente de modal projetado especificamente para exibir uma lista de erros
// de validação de uma planilha.
export const ModalErrorsSheet = ({
  onClose,
  title,
  errors,
}: ModalErrorsProps) => {
  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open) onClose()
      }}
    >
      <DialogContent
        showCloseButton={false}
        aria-describedby={undefined}
        className="max-h-[90vh] overflow-y-auto rounded-xl p-5 sm:max-w-2xl sm:p-8"
      >
        <DialogHeader>
          <DialogTitle className="text-2xl font-semibold text-destructive">
            {title || 'Erros da planilha'}
          </DialogTitle>
        </DialogHeader>

        {/* Renderiza a lista de erros apenas se o array 'errors' existir e não estiver vazio. */}
        {errors && errors.length > 0 && (
          <div className="space-y-4">
            {/* Mapeia cada item de erro para criar um bloco de visualização. */}
            {errors.map((item, index) => (
              <Alert
                key={index}
                variant="destructive"
                className="border-destructive/25 bg-destructive/10"
              >
                <AlertTitle>
                  Código: <span className="font-normal">{item.code}</span>
                </AlertTitle>
                <AlertDescription>
                  <p className="text-muted-foreground">
                    Linha da planilha: {item.line}
                  </p>
                  {/* Mapeia a lista de mensagens de erro para o item atual. */}
                  <ul className="list-disc space-y-1 pl-5">
                    {item.errors.map((err, idx) => (
                      <li key={idx}>{err}</li>
                    ))}
                  </ul>
                </AlertDescription>
              </Alert>
            ))}
          </div>
        )}

        <DialogFooter className="sm:justify-center">
          <DialogClose asChild>
            <UiButton variant="outline" className="px-6">
              Fechar
            </UiButton>
          </DialogClose>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
