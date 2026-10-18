'use client'

import * as React from 'react'
import { cn } from 'cn'
import { Badge } from '@/components/ui/badge'
import { chipToneClass, type Tone } from '@/lib/tone'

// Indicador de status único do sistema: mesmo formato e mesmas cores em
// inventários, itens, setores e usuários. Use sempre este componente para status.
export function StatusBadge({
  tone,
  children,
  icon,
  className,
}: {
  tone: Tone
  children: React.ReactNode
  icon?: React.ReactNode
  className?: string
}) {
  return (
    <Badge
      variant="outline"
      className={cn(
        'w-fit gap-1.5 rounded-md font-medium whitespace-nowrap',
        chipToneClass[tone],
        className,
      )}
    >
      {icon}
      {children}
    </Badge>
  )
}

// Status de inventário: Em andamento (primary), Finalizado (success), Atrasado (danger).
export const inventoryStatusView = {
  CONCLUIDO: { tone: 'success', label: 'Finalizado' },
  VENCIDO: { tone: 'danger', label: 'Atrasado' },
  EM_ANDAMENTO: { tone: 'primary', label: 'Em andamento' },
  OPEN: { tone: 'primary', label: 'Em andamento' },
} as const
