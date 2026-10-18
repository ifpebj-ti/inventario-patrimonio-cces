import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { RefreshCw, TriangleAlert } from 'lucide-react'

type ErrorStateProps = {
  message: string
  onRetry?: () => void
  title?: string
}

// Estado de erro de carregamento: use no lugar do conteúdo que não pôde ser
// carregado, para que todas as telas mostrem a falha do mesmo jeito.
export function ErrorState({
  message,
  onRetry,
  title = 'Algo deu errado',
}: ErrorStateProps) {
  return (
    <Card
      role="alert"
      className="items-center gap-3 border-destructive/25 px-6 py-12 text-center"
    >
      <div className="flex size-14 items-center justify-center rounded-full bg-destructive/10 text-destructive">
        <TriangleAlert className="size-7" aria-hidden="true" />
      </div>
      <h2 className="text-lg font-semibold text-foreground">{title}</h2>
      <p className="max-w-md text-sm text-muted-foreground">{message}</p>
      {onRetry && (
        <Button
          type="button"
          variant="outline"
          onClick={onRetry}
          className="mt-2 w-full sm:w-auto"
        >
          <RefreshCw />
          Tentar novamente
        </Button>
      )}
    </Card>
  )
}
