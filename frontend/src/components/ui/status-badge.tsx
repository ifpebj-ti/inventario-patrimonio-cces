'use client'

import * as React from 'react'
import { cn } from 'cn'
import { Badge } from '@/components/ui/badge'
import { chipToneClass, type Tone } from '@/lib/tone'
import type { ValidationStatus } from '@/commons/models/item'

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

// Situação do item: Verificado (success), Não localizado (warning) e Pendente (danger). Servidor
// antigo não manda validationStatus: vale isValid.
export const itemValidationView = {
  VALIDADO: { tone: 'success', label: 'Verificado' },
  NAO_LOCALIZADO: { tone: 'warning', label: 'Não localizado' },
  PENDENTE: { tone: 'danger', label: 'Pendente' },
} as const

export const validationStatusOf = (item: {
  isValid?: boolean
  validationStatus?: ValidationStatus
}): ValidationStatus =>
  item.validationStatus ?? (item.isValid ? 'VALIDADO' : 'PENDENTE')

// Status de inventário: Em andamento (primary), Finalizado (success), Atrasado (danger).
export const inventoryStatusView = {
  CONCLUIDO: { tone: 'success', label: 'Finalizado' },
  VENCIDO: { tone: 'danger', label: 'Atrasado' },
  EM_ANDAMENTO: { tone: 'primary', label: 'Em andamento' },
  OPEN: { tone: 'primary', label: 'Em andamento' },
} as const
