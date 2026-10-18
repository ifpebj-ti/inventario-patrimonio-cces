'use client'

import { StatusBadge, inventoryStatusView } from '@/components/ui/status-badge'
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip'
import { Checkbox } from '@/components/ui/checkbox'

import { Card as UiCard } from '@/components/ui/card'

import { Button as UiButton } from '@/components/ui/button'
import {
  TableCell as UiTableCell,
  TableHead as UiTableHead,
  TableRow as UiTableRow,
  Table as UiTable,
  TableHeader as UiTableHeader,
  TableBody as UiTableBody,
} from '@/components/ui/table'
import { Input as UiInput } from '@/components/ui/input'

import { TableProps } from './types'
import {
  ColumnDef,
  flexRender,
  getCoreRowModel,
  getFilteredRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  Row,
  SortingState,
  useReactTable,
} from '@tanstack/react-table'
import { useState } from 'react'
import { Item } from '@/commons/models/item'
import {
  Pagination,
  PaginationContent,
  PaginationItem,
} from '@/components/ui/pagination'
import {
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  ChevronUp,
  ChevronsUpDown,
  CheckCircle2,
  Clock3,
  Eye,
  EyeOff,
  FileSpreadsheet,
  Mail,
  Pencil,
  Trash2,
} from 'lucide-react'
import { InventoryResponse } from '@/services/inventory'

// O ícone inicia fechado e abre enquanto a ação de visualização está em foco.
const ViewActionEyeButton = ({
  onClick,
  title,
}: {
  onClick: () => void
  title: string
}) => {
  const [isHovered, setIsHovered] = useState(false)

  return (
    <UiButton
      variant="ghost"
      size="icon"
      type="button"
      onClick={onClick}
      onMouseEnter={() => setIsHovered(true)}
      onMouseLeave={() => setIsHovered(false)}
      className="text-muted-foreground"
      title={title}
      aria-label={title}
    >
      {isHovered ? <Eye /> : <EyeOff />}
    </UiButton>
  )
}

// Este é um componente de Tabela genérico e reutilizável.
// Ele recebe dados e funções de callback como props para ser altamente configurável.
export const Table = ({
  header,
  content = [],
  filterToolbar,
  appearance = 'default',
  selectable,
  selectedRows = [],
  onRowSelect,
  onRowDoubleClick,
  showExportButtons = false,
  onClearInventory,
  disableClear = false,
  hideClear = false,
  onDeleteSelected,
  onExportSelected,
  onExportSheet,
  onSendEmailSheet,
  onDeleteItem,
  onEditItem,
}: TableProps) => {
  // Estado para controlar a ordenação das colunas.
  const [sorting, setSorting] = useState<SortingState>([])
  // Estado para controlar o filtro global (busca em toda a tabela).
  const [globalFilter, setGlobalFilter] = useState('')

  // Função para adicionar ou remover o ID de uma linha na lista de linhas selecionadas.
  const toggleRowSelection = (rowId: number) => {
    // A função só executa se a prop 'onRowSelect' for fornecida.
    if (!onRowSelect) return

    // Verifica se a linha já está selecionada para decidir se remove ou adiciona.
    const newSelectedRows = selectedRows.includes(rowId)
      ? selectedRows.filter((id) => id !== rowId)
      : [...selectedRows, rowId]

    // Chama a função do componente pai para atualizar o estado das linhas selecionadas.
    onRowSelect(newSelectedRows)
  }

  // Função para selecionar ou deselecionar todas as linhas de uma vez.
  const toggleAllSelection = () => {
    if (!onRowSelect) return

    const allRowIds = content.map((row) => row.id)
    // Se todas já estão selecionadas, limpa a seleção. Senão, seleciona todas.
    if (selectedRows.length === allRowIds.length) {
      onRowSelect([])
    } else {
      onRowSelect(allRowIds)
    }
  }

  // Variáveis booleanas para controlar o estado do checkbox principal (no cabeçalho).
  const isAllSelected =
    selectedRows.length === content.length && content.length > 0
  const isIndeterminate =
    selectedRows.length > 0 && selectedRows.length < content.length

  // 'columns' é a definição de como cada coluna da tabela deve se comportar e ser renderizada.
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const columns: ColumnDef<any>[] = [
    // Renderiza a coluna de checkbox condicionalmente, apenas se a prop 'selectable' for true.
    ...(selectable
      ? [
          {
            id: 'select',
            // 'header' define o que é renderizado no cabeçalho da coluna.
            header: () => (
              <div className="w-10 text-center mx-auto">
                <Checkbox
                  checked={isIndeterminate ? 'indeterminate' : isAllSelected}
                  // 'ref' é usado aqui para controlar o estado "indeterminado" do checkbox.
                  onCheckedChange={toggleAllSelection}
                  aria-label="Selecionar todos os itens"
                />
              </div>
            ),
            // 'cell' define o que é renderizado em cada célula do corpo da tabela para esta coluna.
            cell: ({ row }: { row: Row<Item> }) => (
              <div className="w-10 text-center mx-auto">
                <Checkbox
                  checked={selectedRows.includes(row.original.id)}
                  onCheckedChange={() => toggleRowSelection(row.original.id)}
                  aria-label={`Selecionar linha ${row.original.id}`}
                />
              </div>
            ),
          },
        ]
      : []),

    // Mapeia o array 'header' (passado via props) para gerar as colunas de dados dinamicamente.
    ...header.map((item) => {
      // Permite uma renderização customizada para a coluna com a chave 'code'.
      if (item.key === 'code') {
        return {
          accessorKey: item.key,
          header: () => (
            <div className="w-24 shrink-0 text-left font-semibold">
              {item.headerText}
            </div>
          ),
          cell: ({ row }: { row: Row<Item> }) => (
            <div className="w-24 shrink-0">
              <span
                className={`font-semibold whitespace-nowrap ${
                  row.original.isValid ? 'text-success' : 'text-destructive'
                }`}
              >
                {row.original.code}
              </span>
            </div>
          ),
        }
      }

      // Descrição tem espaço controlado min-w-[200px] max-w-xs com truncate
      if (item.key === 'description') {
        return {
          accessorKey: item.key,
          header: () => (
            <div className="max-w-48 text-left font-semibold">
              {item.headerText}
            </div>
          ),
          cell: ({ row }: { row: Row<Item | InventoryResponse> }) => (
            <div
              className="max-w-48 truncate text-muted-foreground"
              title={row.original.description}
            >
              {row.original.description}
            </div>
          ),
        }
      }

      // Carga / Responsável: max-w-[160px] truncate
      if (item.key === 'responsible') {
        return {
          accessorKey: item.key,
          header: () => (
            <div className="max-w-[160px] text-left font-semibold">
              {item.headerText}
            </div>
          ),
          cell: ({ row }: { row: Row<Item> }) => (
            <div
              className="max-w-[160px] truncate text-muted-foreground"
              title={row.original.responsible}
            >
              {row.original.responsible || '-'}
            </div>
          ),
        }
      }

      if (item.key === 'status' && 'status' in (content[0] || {})) {
        return {
          accessorKey: item.key,
          header: () => (
            <div className="w-36 text-center font-semibold">
              {item.headerText}
            </div>
          ),
          cell: ({ row }: { row: Row<InventoryResponse> }) => {
            const view = inventoryStatusView[row.original.status]
            return (
              <div className="flex justify-center">
                <StatusBadge tone={view.tone} icon={<Clock3 />}>
                  {view.label}
                </StatusBadge>
              </div>
            )
          },
        }
      }

      // Status de validação do item
      if (item.key === 'status' || item.key === 'isValid') {
        return {
          accessorKey: item.key,
          header: () => (
            <div className="w-28 text-center font-semibold">
              {item.headerText}
            </div>
          ),
          cell: ({ row }: { row: Row<Item | InventoryResponse> }) => {
            if ('status' in row.original) {
              const view = inventoryStatusView[row.original.status]
              return (
                <div className="flex justify-center">
                  <StatusBadge tone={view.tone} icon={<Clock3 />}>
                    {view.label}
                  </StatusBadge>
                </div>
              )
            }
            const isValid = Boolean(row.original.isValid)
            return (
              <div className="flex justify-center">
                <StatusBadge
                  tone={isValid ? 'success' : 'danger'}
                  icon={isValid ? <CheckCircle2 /> : <Clock3 />}
                >
                  {isValid ? 'Verificado' : 'Pendente'}
                </StatusBadge>
              </div>
            )
          },
        }
      }

      // Preço / Valor: w-24 whitespace-nowrap text-right
      if (item.key === 'price') {
        return {
          accessorKey: item.key,
          header: () => (
            <div className="w-24 text-right font-semibold">
              {item.headerText}
            </div>
          ),
          cell: ({ row }: { row: Row<Item> }) => {
            const val = row.original.price
            let formatted = '-'
            if (typeof val === 'number') {
              formatted = val.toLocaleString('pt-BR', {
                style: 'currency',
                currency: 'BRL',
              })
            } else if (typeof val === 'string' && val.trim() !== '') {
              formatted = val
            }
            return (
              <div className="w-24 whitespace-nowrap text-right text-muted-foreground">
                {formatted}
              </div>
            )
          },
        }
      }

      // Sala / Locale: max-w-[140px] truncate
      if (item.key === 'locale') {
        return {
          accessorKey: item.key,
          header: () => (
            <div className="max-w-[140px] text-left font-semibold">
              {item.headerText}
            </div>
          ),
          cell: ({ row }: { row: Row<Item> }) => (
            <div
              className="max-w-[140px] truncate text-muted-foreground"
              title={row.original.locale}
            >
              {row.original.locale || '-'}
            </div>
          ),
        }
      }

      // Para todas as outras colunas, usa a configuração padrão.
      if (
        appearance === 'inventory' &&
        ['createdAt', 'updatedAt', 'plannedEndDate', 'completedAt'].includes(
          item.key,
        )
      ) {
        return {
          accessorKey: item.key,
          header: () => (
            <div className="text-center font-semibold whitespace-nowrap">
              {item.headerText}
            </div>
          ),
          cell: ({ getValue }: { getValue: () => unknown }) => {
            const value = getValue()
            if (typeof value !== 'string' || !value)
              return (
                <span className="block text-center whitespace-nowrap text-muted-foreground">
                  {item.key === 'plannedEndDate' ? 'Não informada' : '—'}
                </span>
              )
            const iso = value.slice(0, 10)
            const formatted = /^\d{4}-\d{2}-\d{2}$/.test(iso)
              ? iso.split('-').reverse().join('/')
              : value
            return (
              <span className="block text-center whitespace-nowrap tabular-nums">
                {formatted}
              </span>
            )
          },
        }
      }
      // Colunas de texto padrão (nome, setor etc.): largura limitada com reticências
      // para um texto longo nunca invadir a célula vizinha.
      return {
        accessorKey: item.key,
        header: item.headerText,
        cell: ({ getValue }: { getValue: () => unknown }) => {
          const value = getValue()
          const text =
            value === null || value === undefined || value === ''
              ? '—'
              : String(value)
          return (
            <div className="max-w-40 truncate" title={text}>
              {text}
            </div>
          )
        },
      }
    }),

    // Adiciona a coluna de "Ações" condicionalmente, se as props 'onDeleteItem', 'onEditItem' ou 'onRowDoubleClick' forem fornecidas.
    ...(onDeleteItem || onEditItem || onRowDoubleClick
      ? [
          {
            id: 'actions',
            header: () => (
              <div className="w-24 shrink-0 text-center font-semibold mx-auto">
                Ações
              </div>
            ),
            cell: ({ row }: { row: Row<InventoryResponse | Item> }) => {
              const item = row.original
              const displayName = 'name' in item ? item.name : item.code

              return (
                <div className="w-24 shrink-0 flex justify-center items-center gap-1 mx-auto whitespace-nowrap">
                  {/* Botão de Visualizar (Olho), ao lado da lixeira */}
                  {onRowDoubleClick && (
                    <ViewActionEyeButton
                      onClick={() => handleRowDoubleClick(item)}
                      title={`Visualizar ${displayName}`}
                    />
                  )}
                  {/* Botão de Editar, renderizado apenas se 'onEditItem' for passado. */}
                  {onEditItem && (
                    <UiButton
                      variant="ghost"
                      size="icon"
                      onClick={() => onEditItem(item as InventoryResponse)}
                      className="text-muted-foreground"
                      title={`Editar ${displayName}`}
                      aria-label={`Editar ${displayName}`}
                    >
                      <Pencil />
                    </UiButton>
                  )}
                  {/* Botão de Deletar, renderizado apenas se 'onDeleteItem' for passado. */}
                  {onDeleteItem && (
                    <UiButton
                      variant="ghost"
                      size="icon"
                      onClick={() => onDeleteItem(item)}
                      className="text-muted-foreground hover:bg-destructive/10 hover:text-destructive"
                      title={`Deletar ${displayName}`}
                      aria-label={`Deletar ${displayName}`}
                    >
                      <Trash2 />
                    </UiButton>
                  )}
                </div>
              )
            },
          },
        ]
      : []),
  ]

  // Hook principal do TanStack Table: inicializa a instância da tabela com os dados, colunas e estado.
  const table = useReactTable({
    columns,
    data: content,
    state: {
      globalFilter,
      sorting,
    },
    onSortingChange: setSorting,
    getCoreRowModel: getCoreRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    initialState: {
      pagination: {
        pageIndex: 0,
        pageSize: 50,
      },
    },
  })

  // Função que lida com o duplo clique em uma linha.
  const handleRowDoubleClick = (rowData: Item | InventoryResponse) => {
    if (onRowDoubleClick) {
      onRowDoubleClick(rowData)
    }
  }

  return (
    <div
      className={`w-full ${appearance === 'inventory' ? 'inventory-table-card' : ''}`}
    >
      {/* Barra de ferramentas acima da tabela: filtro + botões de exportação rigorosamente em linha horizontal */}
      <UiCard className="flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3 px-3 sm:px-4 py-2.5 rounded-b-none shadow-none w-full">
        {/* Input para o filtro global */}
        {filterToolbar ? (
          <div className="w-full">{filterToolbar}</div>
        ) : (
          <div className="flex-1 max-w-full sm:max-w-xs md:max-w-sm">
            <UiInput
              className="w-full m-0"
              placeholder="Filtrar..."
              value={globalFilter}
              onChange={(e) => setGlobalFilter(e.target.value)}
            />
          </div>
        )}
        {showExportButtons && (
          <div className="flex flex-row items-center justify-start sm:justify-end gap-2 shrink-0">
            <Tooltip>
              <TooltipTrigger asChild>
                <UiButton
                  variant={'success'}
                  size="icon"
                  className="size-10 shrink-0"
                  type="button"
                  onClick={onExportSheet}
                  aria-label="Exportar planilha Excel"
                >
                  <FileSpreadsheet />
                </UiButton>
              </TooltipTrigger>
              <TooltipContent>Exportar planilha Excel</TooltipContent>
            </Tooltip>
            {!hideClear && (
              <Tooltip>
                <TooltipTrigger asChild>
                  <UiButton
                    variant={
                      content.length > 0 && !disableClear
                        ? 'destructive'
                        : 'secondary'
                    }
                    size="icon"
                    className="size-10 shrink-0"
                    type="button"
                    disabled={content.length === 0 || disableClear}
                    onClick={
                      content.length > 0
                        ? onClearInventory ||
                          onDeleteSelected ||
                          onExportSelected
                        : undefined
                    }
                    aria-label={
                      content.length > 0
                        ? 'Limpar todos os itens do inventário'
                        : 'Inventário já está vazio'
                    }
                  >
                    <Trash2 />
                  </UiButton>
                </TooltipTrigger>
                <TooltipContent>
                  {content.length > 0
                    ? 'Limpar todos os itens do inventário'
                    : 'Inventário já está vazio'}
                </TooltipContent>
              </Tooltip>
            )}
            <Tooltip>
              <TooltipTrigger asChild>
                <UiButton
                  variant={'default'}
                  size="icon"
                  className="size-10 shrink-0"
                  type="button"
                  onClick={onSendEmailSheet}
                  aria-label="Enviar planilha por e-mail"
                >
                  <Mail />
                </UiButton>
              </TooltipTrigger>
              <TooltipContent>Enviar planilha por e-mail</TooltipContent>
            </Tooltip>
          </div>
        )}
      </UiCard>

      {/* Container principal da tabela/cards com sombra e bordas. */}
      <UiCard className="gap-0 py-0 rounded-t-none border-t-0 overflow-hidden w-full">
        {/* 1. VISUALIZAÇÃO MOBILE (< 640px): Cards Acessíveis */}
        <div className="sm:hidden flex flex-col divide-y">
          {table.getRowModel().rows.length === 0 ? (
            <div className="p-8 text-center text-muted-foreground text-sm">
              Nenhum registro encontrado.
            </div>
          ) : (
            table.getRowModel().rows.map((row) => {
              const item = row.original
              const isSelected = selectedRows.includes(item.id)
              const isEquipment = 'code' in item && Boolean(item.code)
              const displayName = isEquipment
                ? item.code
                : 'name' in item
                  ? item.name
                  : `#${item.id}`

              return (
                <div
                  key={item.id}
                  className={`p-4 flex flex-col gap-2.5 transition-colors ${
                    isSelected ? 'bg-primary/10' : 'hover:bg-muted/50'
                  }`}
                >
                  {/* Topo do Card: Checkbox + Código/Nome com Badge + Ações */}
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex items-center gap-2.5 flex-1 min-w-0">
                      {selectable && (
                        <Checkbox
                          checked={isSelected}
                          onCheckedChange={() => toggleRowSelection(item.id)}
                          className="size-5 shrink-0"
                          aria-label={`Selecionar item ${displayName}`}
                        />
                      )}
                      <div
                        className="flex min-w-0 items-center gap-2 cursor-pointer"
                        onClick={() => handleRowDoubleClick(item)}
                      >
                        {isEquipment ? (
                          <>
                            <span
                              className={`font-bold text-base truncate ${
                                'isValid' in item && item.isValid !== undefined
                                  ? item.isValid
                                    ? 'text-success'
                                    : 'text-destructive'
                                  : 'text-foreground'
                              }`}
                            >
                              {item.code}
                            </span>
                            {'isValid' in item &&
                              item.isValid !== undefined && (
                                <StatusBadge
                                  tone={item.isValid ? 'success' : 'danger'}
                                  icon={
                                    item.isValid ? <CheckCircle2 /> : <Clock3 />
                                  }
                                  className="shrink-0"
                                >
                                  {item.isValid ? 'Verificado' : 'Pendente'}
                                </StatusBadge>
                              )}
                          </>
                        ) : (
                          <span
                            className="min-w-0 truncate text-base font-bold text-foreground"
                            title={'name' in item ? item.name : undefined}
                          >
                            {'name' in item ? item.name : `#${item.id}`}
                          </span>
                        )}
                      </div>
                    </div>

                    {/* Botões de Ação do Card: Editar e Excluir (visualizar é o botão "Ver detalhes") com touch target >= 44x44px */}
                    <div className="flex items-center gap-1 shrink-0">
                      {onEditItem && 'name' in item && (
                        <UiButton
                          variant="ghost"
                          size="icon"
                          type="button"
                          onClick={() => onEditItem(item as InventoryResponse)}
                          className="text-muted-foreground"
                          title={`Editar ${displayName}`}
                          aria-label={`Editar ${displayName}`}
                        >
                          <Pencil />
                        </UiButton>
                      )}
                      {onDeleteItem && (
                        <UiButton
                          variant="ghost"
                          size="icon"
                          type="button"
                          onClick={() => onDeleteItem(item)}
                          className="text-muted-foreground hover:bg-destructive/10 hover:text-destructive"
                          title={`Deletar ${displayName}`}
                          aria-label={`Deletar ${displayName}`}
                        >
                          <Trash2 />
                        </UiButton>
                      )}
                    </div>
                  </div>

                  {/* Descrição do Item */}
                  {'description' in item && item.description && (
                    <p
                      className="text-xs text-muted-foreground line-clamp-3 leading-relaxed cursor-pointer"
                      onClick={() => handleRowDoubleClick(item)}
                    >
                      {item.description}
                    </p>
                  )}

                  {/* Campos secundários organizados em chave-valor */}
                  <div
                    className="grid grid-cols-1 gap-1 text-xs text-muted-foreground cursor-pointer pt-1"
                    onClick={() => handleRowDoubleClick(item)}
                  >
                    {header.map((col) => {
                      if (
                        col.key === 'name' ||
                        col.key === 'code' ||
                        col.key === 'description'
                      )
                        return null
                      // eslint-disable-next-line @typescript-eslint/no-explicit-any
                      const val = (item as any)[col.key]
                      if (val === undefined || val === null || val === '')
                        return null

                      let formattedVal = String(val)
                      if (col.key === 'price' && typeof val === 'number') {
                        formattedVal = val.toLocaleString('pt-BR', {
                          style: 'currency',
                          currency: 'BRL',
                        })
                      }

                      // Status de inventário: mesmo selo colorido da tabela.
                      const statusView =
                        col.key === 'status'
                          ? inventoryStatusView[
                              val as keyof typeof inventoryStatusView
                            ]
                          : undefined

                      return (
                        <div
                          key={col.key}
                          className="flex items-center justify-between gap-2 border-b border-border/50 py-1"
                        >
                          <span className="font-semibold text-muted-foreground shrink-0">
                            {col.headerText}:
                          </span>
                          {statusView ? (
                            <StatusBadge
                              tone={statusView.tone}
                              icon={<Clock3 />}
                            >
                              {statusView.label}
                            </StatusBadge>
                          ) : (
                            <span className="text-foreground text-right truncate max-w-[220px]">
                              {formattedVal}
                            </span>
                          )}
                        </div>
                      )
                    })}
                  </div>

                  {/* Botão de navegação tátil rápida "Ver detalhes" */}
                  {onRowDoubleClick && (
                    <UiButton
                      type="button"
                      onClick={() => handleRowDoubleClick(item)}
                      className="mt-1 w-full text-xs"
                    >
                      <span>Ver detalhes</span>
                      <ChevronRight />
                    </UiButton>
                  )}
                </div>
              )
            })
          )}
        </div>

        {/* 2. VISUALIZAÇÃO TABLET E DESKTOP (>= 640px): Tabela Tradicional com overflow horizontal se necessário */}
        <div className="hidden sm:block w-full overflow-x-auto">
          <UiTable className="table-auto">
            <UiTableHeader className="bg-muted/50">
              <UiTableRow key={'headerRow'}>
                {table.getHeaderGroups()[0].headers.map((item) => {
                  return (
                    <UiTableHead
                      key={item.id}
                      scope="col"
                      className="px-2 py-3 text-center text-xs font-semibold whitespace-nowrap text-foreground sm:px-3 sm:text-sm"
                    >
                      {item.column.getCanSort() ? (
                        <UiButton
                          variant="ghost"
                          size="sm"
                          className="min-w-max font-semibold"
                          onClick={item.column.getToggleSortingHandler()}
                        >
                          {flexRender(
                            item.column.columnDef.header,
                            item.getContext(),
                          )}
                          {item.column.getIsSorted() === 'asc' ? (
                            <ChevronUp />
                          ) : item.column.getIsSorted() === 'desc' ? (
                            <ChevronDown />
                          ) : (
                            <ChevronsUpDown className="opacity-50" />
                          )}
                        </UiButton>
                      ) : (
                        flexRender(
                          item.column.columnDef.header,
                          item.getContext(),
                        )
                      )}
                    </UiTableHead>
                  )
                })}
              </UiTableRow>
            </UiTableHeader>
            <UiTableBody>
              {table.getRowModel().rows.map((item) => (
                <UiTableRow
                  key={item.original.id}
                  onDoubleClick={() => handleRowDoubleClick(item.original)}
                >
                  {item.getVisibleCells().map((cell) => (
                    <UiTableCell key={cell.id}>
                      {flexRender(
                        cell.column.columnDef.cell,
                        cell.getContext(),
                      )}
                    </UiTableCell>
                  ))}
                </UiTableRow>
              ))}
            </UiTableBody>
          </UiTable>
        </div>

        {/* Controles de paginação no rodapé (compartilhados por ambas as visualizações) */}
        <Pagination className="table-pagination border-t p-4">
          <PaginationContent className="gap-8 sm:gap-12">
            {table.getCanPreviousPage() && (
              <PaginationItem>
                <UiButton
                  variant="ghost"
                  size="icon"
                  onClick={() => table.previousPage()}
                  aria-label="Página anterior"
                  title="Página anterior"
                >
                  <ChevronLeft className="size-6" />
                </UiButton>
              </PaginationItem>
            )}
            <PaginationItem
              aria-label={`Página ${table.getState().pagination.pageIndex + 1}`}
              title={`Página ${table.getState().pagination.pageIndex + 1}`}
              className="flex min-w-11 items-center justify-center text-sm font-medium text-foreground sm:text-base"
            >
              {table.getState().pagination.pageIndex + 1}
            </PaginationItem>
            {table.getCanNextPage() && (
              <PaginationItem>
                <UiButton
                  variant="ghost"
                  size="icon"
                  onClick={() => table.nextPage()}
                  aria-label="Próxima página"
                  title="Próxima página"
                >
                  <ChevronRight className="size-6" />
                </UiButton>
              </PaginationItem>
            )}
          </PaginationContent>
        </Pagination>
      </UiCard>
    </div>
  )
}
