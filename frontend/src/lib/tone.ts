// Tons semânticos do sistema, mapeados para as variantes do shadcn/ui.
// Cores fixas: primary (#058EC8), destructive (#DC2626), success (#16A34A) e warning.
export type Tone = 'neutral' | 'primary' | 'success' | 'warning' | 'danger'

// Variante do Badge do shadcn/ui para cada tom.
export const badgeVariant = {
  neutral: 'secondary',
  primary: 'default',
  success: 'success',
  warning: 'warning',
  danger: 'destructive',
} as const

// Estilo suave (Chip): fundo e borda com transparência do mesmo tom.
export const chipToneClass: Record<Tone, string> = {
  neutral: 'border-border bg-muted text-muted-foreground',
  primary: 'border-primary/25 bg-primary/10 text-primary',
  success: 'border-success/25 bg-success/10 text-success',
  warning: 'border-warning/25 bg-warning/10 text-warning',
  danger: 'border-destructive/25 bg-destructive/10 text-destructive',
}
