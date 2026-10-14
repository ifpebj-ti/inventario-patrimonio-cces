'use client'

import * as React from 'react'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

export type SelectFieldOption = { value: string; label: string }

type SelectFieldProps = {
  options: SelectFieldOption[]
  value: string
  onValueChange: (value: string) => void
  placeholder?: string
  disabled?: boolean
  id?: string
  'aria-label'?: string
  className?: string
}

// Campo de seleção único do sistema: use sempre este componente quando o
// usuário só precisa escolher uma opção de uma lista.
export function SelectField({
  options,
  value,
  onValueChange,
  placeholder = 'Selecione...',
  disabled,
  id,
  className,
  ...props
}: SelectFieldProps) {
  return (
    <Select value={value} onValueChange={onValueChange} disabled={disabled}>
      <SelectTrigger
        id={id}
        aria-label={props['aria-label']}
        className={className ?? 'w-full'}
      >
        <SelectValue placeholder={placeholder} />
      </SelectTrigger>
      <SelectContent>
        {options.map((option) => (
          <SelectItem key={option.value} value={option.value}>
            {option.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  )
}
