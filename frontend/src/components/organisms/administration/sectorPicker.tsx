'use client'

import { useEffect, useId, useRef, useState } from 'react'
import { ChevronDown, Search } from 'lucide-react'
import { Input as UiInput } from '@/components/ui/input'
import { cn } from '@/lib/utils'
import type { Sector } from '@/commons/models/administration'

/**
 * Seleção de setor com busca: ao focar ou clicar com o campo vazio mostra todos os setores
 * disponíveis; digitar filtra por nome ou sigla; clicar num setor o adiciona na hora e a lista segue
 * aberta para adicionar outros. Setor ativo e ainda não associado é o que `available` deve conter.
 */
export const SectorPicker = ({
  id,
  available,
  disabled,
  onPick,
}: {
  id: string
  available: Sector[]
  disabled?: boolean
  onPick: (sector: Sector) => void
}) => {
  const listId = useId()
  const container = useRef<HTMLDivElement>(null)
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [active, setActive] = useState(0)

  const search = query.trim().toLocaleLowerCase()
  const matches = available.filter(
    (sector) =>
      search === '' ||
      `${sector.name} ${sector.code ?? ''}`
        .toLocaleLowerCase()
        .includes(search),
  )

  // Fecha ao clicar fora.
  useEffect(() => {
    if (!open) return
    const close = (event: MouseEvent) => {
      if (!container.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [open])

  // Mantém a lista visível dentro do modal, que rola.
  useEffect(() => {
    if (open) container.current?.scrollIntoView({ block: 'nearest' })
  }, [open, matches.length])

  const pick = (sector: Sector) => {
    onPick(sector)
    setQuery('')
    setActive(0)
  }

  return (
    <div ref={container} className="relative">
      <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
      <UiInput
        id={id}
        role="combobox"
        aria-expanded={open}
        aria-controls={listId}
        aria-autocomplete="list"
        aria-activedescendant={
          open && matches[active]
            ? `${listId}-${matches[active].id}`
            : undefined
        }
        className="pr-9 pl-9"
        placeholder="Buscar por nome ou sigla — ou role a lista"
        autoComplete="off"
        disabled={disabled}
        value={query}
        onFocus={() => setOpen(true)}
        onClick={() => setOpen(true)}
        onChange={(event) => {
          setQuery(event.target.value)
          setActive(0)
          setOpen(true)
        }}
        onKeyDown={(event) => {
          if (event.key === 'ArrowDown') {
            event.preventDefault()
            setOpen(true)
            setActive((index) => Math.min(index + 1, matches.length - 1))
          } else if (event.key === 'ArrowUp') {
            event.preventDefault()
            setActive((index) => Math.max(index - 1, 0))
          } else if (event.key === 'Enter') {
            event.preventDefault()
            if (open && matches[active]) pick(matches[active])
          } else if (event.key === 'Escape') {
            setOpen(false)
          } else if (event.key === 'Tab') {
            setOpen(false)
          }
        }}
      />
      <ChevronDown
        aria-hidden="true"
        className={cn(
          'pointer-events-none absolute top-1/2 right-3 size-4 -translate-y-1/2 text-muted-foreground transition-transform',
          open && 'rotate-180',
        )}
      />
      {open && (
        <div className="absolute top-full right-0 left-0 z-20 mt-1 overflow-hidden rounded-lg border bg-popover shadow-md">
          <div className="flex items-center justify-between border-b px-3 py-2 text-xs text-muted-foreground">
            <span>
              {matches.length}{' '}
              {matches.length === 1
                ? 'setor disponível'
                : 'setores disponíveis'}
            </span>
            <span>Clique para adicionar</span>
          </div>
          {matches.length === 0 ? (
            <p className="px-3 py-3 text-sm text-muted-foreground">
              {available.length === 0
                ? 'Não há outros setores ativos disponíveis.'
                : 'Nenhum setor ativo encontrado para esta busca.'}
            </p>
          ) : (
            <ul
              id={listId}
              role="listbox"
              aria-label="Setores disponíveis"
              className="max-h-56 overflow-y-auto"
            >
              {matches.map((sector, index) => (
                <li
                  key={sector.id}
                  id={`${listId}-${sector.id}`}
                  role="option"
                  aria-selected={index === active}
                >
                  <button
                    type="button"
                    tabIndex={-1}
                    // Evita que o campo perca o foco (e a lista feche) antes do clique.
                    onMouseDown={(event) => event.preventDefault()}
                    onMouseEnter={() => setActive(index)}
                    onClick={() => pick(sector)}
                    className={cn(
                      'flex w-full items-center justify-between gap-3 px-3 py-2.5 text-left text-sm',
                      index === active ? 'bg-muted' : 'hover:bg-muted/60',
                    )}
                  >
                    <span>{sector.name}</span>
                    {sector.code && (
                      <span className="rounded bg-muted px-1.5 py-0.5 text-xs font-medium text-muted-foreground">
                        {sector.code}
                      </span>
                    )}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}
