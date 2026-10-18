'use client'

import * as React from 'react'
import { format } from 'date-fns'
import { CalendarIcon } from 'lucide-react'
import { cn } from 'cn'
import { Button as UiButton } from '@/components/ui/button'
import { Calendar } from '@/components/ui/calendar'
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover'

export type DatePickerProps = {
  value?: string
  onChange?: (value: string) => void
  placeholder?: string
  disabled?: boolean
  min?: string
  max?: string
  id?: string
  'aria-label'?: string
  className?: string
}

export function DatePicker({
  value,
  onChange,
  placeholder = 'Selecione uma data',
  disabled,
  min,
  max,
  id,
  className,
  ...props
}: DatePickerProps) {
  const [open, setOpen] = React.useState(false)
  const date = value ? new Date(value + 'T00:00:00') : undefined

  const minDate = min ? new Date(min + 'T00:00:00') : undefined
  const maxDate = max ? new Date(max + 'T00:00:00') : undefined

  const handleSelect = (selectedDate: Date | undefined) => {
    if (selectedDate) {
      const isoString = selectedDate.toISOString().split('T')[0]
      onChange?.(isoString)
      setOpen(false)
    }
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <UiButton
          id={id}
          type="button"
          variant="outline"
          disabled={disabled}
          className={cn(
            'w-full justify-start text-left font-normal',
            !value && 'text-muted-foreground',
            className,
          )}
          {...props}
        >
          <CalendarIcon className="mr-2 size-4" />
          {date ? format(date, 'dd/MM/yyyy') : placeholder}
        </UiButton>
      </PopoverTrigger>
      <PopoverContent className="w-auto p-0" align="start">
        <Calendar
          mode="single"
          selected={date}
          onSelect={handleSelect}
          disabled={(d) => {
            if (minDate && d < minDate) return true
            if (maxDate && d > maxDate) return true
            return false
          }}
        />
      </PopoverContent>
    </Popover>
  )
}
