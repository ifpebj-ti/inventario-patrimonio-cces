'use client'

import { TableCell } from '@/components/atoms/tableCell'
import { TableHeaderCell } from '@/components/atoms/tableHeaderCell'
import { TableRow } from '@/components/molecules/tableRow'
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
import Input from '@/components/atoms/input'
import { useState } from 'react'
import { Item } from '@/commons/models/item'
import { Button } from '@/components/atoms/button'
import { RiPencilFill } from 'react-icons/ri'
import { FaEnvelope, FaFileExcel, FaTrash } from 'react-icons/fa'
import { IoIosArrowBack, IoIosArrowForward } from 'react-icons/io'
import { MdChecklist, MdLibraryBooks } from 'react-icons/md'
import { InventoryResponse } from '@/services/inventory'

// Este é um componente de Tabela genérico e reutilizável.
// Ele recebe dados e funções de callback como props para ser altamente configurável.
export const Table = ({
  header,
  content = [],
  selectable,
  selectedRows = [],
  onRowSelect,
  onRowDoubleClick,
  showExportButtons = false,
  onExportSelected,
  onExportAll,
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
                <input
                  type="checkbox"
                  checked={isAllSelected}
                  // 'ref' é usado aqui para controlar o estado "indeterminado" do checkbox.
                  ref={(ref) => {
                    if (ref) ref.indeterminate = isIndeterminate
                  }}
                  onChange={toggleAllSelection}
                  className="form-checkbox h-4 w-4 cursor-pointer"
                  aria-label="Selecionar todos os itens"
                />
              </div>
            ),
            // 'cell' define o que é renderizado em cada célula do corpo da tabela para esta coluna.
            cell: ({ row }: { row: Row<Item> }) => (
              <div className="w-10 text-center mx-auto">
                <input
                  type="checkbox"
                  checked={selectedRows.includes(row.original.id)}
                  onChange={() => toggleRowSelection(row.original.id)}
                  className="form-checkbox h-4 w-4 cursor-pointer"
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
                  row.original.isValid ? 'text-emerald-600' : 'text-red-500'
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
            <div className="min-w-[200px] max-w-xs text-left font-semibold">
              {item.headerText}
            </div>
          ),
          cell: ({ row }: { row: Row<any> }) => (
            <div
              className="min-w-[200px] max-w-xs truncate text-slate-700"
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
          cell: ({ row }: { row: Row<any> }) => (
            <div
              className="max-w-[160px] truncate text-slate-700"
              title={row.original.responsible}
            >
              {row.original.responsible || '-'}
            </div>
          ),
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
          cell: ({ row }: { row: Row<any> }) => {
            const val = row.original.price
            const formatted =
              typeof val === 'number'
                ? val.toLocaleString('pt-BR', {
                    style: 'currency',
                    currency: 'BRL',
                  })
                : '-'
            return (
              <div className="w-24 whitespace-nowrap text-right text-slate-700">
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
          cell: ({ row }: { row: Row<any> }) => (
            <div
              className="max-w-[140px] truncate text-slate-700"
              title={row.original.locale}
            >
              {row.original.locale || '-'}
            </div>
          ),
        }
      }

      // Para todas as outras colunas, usa a configuração padrão.
      return {
        accessorKey: item.key,
        header: item.headerText,
      }
    }),

    // Adiciona a coluna de "Ações" condicionalmente, se as props 'onDeleteItem' ou 'onEditItem' forem fornecidas.
    ...(onDeleteItem || onEditItem
      ? [
          {
            id: 'actions',
            header: () => (
              <div className="w-16 shrink-0 text-center font-semibold mx-auto">
                Ações
              </div>
            ),
            cell: ({ row }: { row: Row<InventoryResponse | Item> }) => {
              const item = row.original
              const displayName = 'name' in item ? item.name : item.code

              return (
                <div className="w-16 shrink-0 flex justify-center items-center gap-1 mx-auto whitespace-nowrap">
                  {/* Botão de Editar, renderizado apenas se 'onEditItem' for passado. */}
                  {onEditItem && (
                    <button
                      onClick={() => onEditItem(item as InventoryResponse)}
                      className="p-2 min-h-[44px] min-w-[44px] flex items-center justify-center text-slate-500 rounded-full hover:bg-blue-100 hover:text-blue-600 transition-colors cursor-pointer"
                      title={`Editar ${displayName}`}
                      aria-label={`Editar ${displayName}`}
                    >
                      <RiPencilFill size={18} />
                    </button>
                  )}
                  {/* Botão de Deletar, renderizado apenas se 'onDeleteItem' for passado. */}
                  {onDeleteItem && (
                    <button
                      onClick={() => onDeleteItem(item)}
                      className="p-2 min-h-[44px] min-w-[44px] flex items-center justify-center text-slate-500 rounded-full hover:bg-red-100 hover:text-red-600 transition-colors cursor-pointer"
                      title={`Deletar ${displayName}`}
                      aria-label={`Deletar ${displayName}`}
                    >
                      <FaTrash size={16} />
                    </button>
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
    <div className="w-full">
      {/* Barra de ferramentas acima da tabela: filtro + botões de exportação rigorosamente em linha horizontal */}
      <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3 px-3 sm:px-4 py-2.5 border border-slate-200 rounded-t-2xl w-full bg-white">
        {/* Input para o filtro global */}
        <div className="flex-1 max-w-full sm:max-w-xs md:max-w-sm">
          <Input
            variant={2}
            placeholder="Filtrar..."
            value={globalFilter}
            onChange={(e) => setGlobalFilter(e.target.value)}
            width="w-full"
            className="m-0"
          />
        </div>
        {/* Botões de exportação, sempre em linha horizontal sem quebrar e sem scroll indesejado */}
        {showExportButtons && (
          <div className="flex flex-row items-center justify-start sm:justify-end gap-2 shrink-0">
            <Button
              type="button"
              variant={5}
              onClick={onExportSheet}
              icon={<FaFileExcel />}
              tooltip="Exportar planilha Excel"
              aria-label="Exportar planilha Excel"
              width="w-auto"
            />
            <Button
              type="button"
              variant={selectedRows.length > 0 ? 6 : 4}
              onClick={selectedRows.length > 0 ? onExportSelected : undefined}
              icon={<MdChecklist />}
              tooltip={
                selectedRows.length > 0
                  ? 'Exportar etiquetas dos itens selecionados'
                  : 'Selecione ao menos um item para exportar'
              }
              aria-label={
                selectedRows.length > 0
                  ? 'Exportar etiquetas dos itens selecionados'
                  : 'Selecione ao menos um item para exportar'
              }
              width="w-auto"
            />
            <Button
              type="button"
              variant={6}
              onClick={onExportAll}
              icon={<MdLibraryBooks />}
              tooltip="Exportar todas as etiquetas"
              aria-label="Exportar todas as etiquetas"
              width="w-auto"
            />
            <Button
              type="button"
              variant={7}
              onClick={onSendEmailSheet}
              icon={<FaEnvelope />}
              tooltip="Enviar planilha por e-mail"
              aria-label="Enviar planilha por e-mail"
              width="w-auto"
            />
          </div>
        )}
      </div>

      {/* Container principal da tabela/cards com sombra e bordas. */}
      <div className="shadow-xl rounded-b-2xl overflow-hidden border-x border-b border-slate-200 w-full bg-white">
        {/* 1. VISUALIZAÇÃO MOBILE (< 640px): Cards Acessíveis */}
        <div className="sm:hidden flex flex-col divide-y divide-slate-100 bg-white">
          {table.getRowModel().rows.length === 0 ? (
            <div className="p-8 text-center text-slate-500 text-sm">
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
                    isSelected ? 'bg-blue-50/60' : 'hover:bg-slate-50'
                  }`}
                >
                  {/* Topo do Card: Checkbox + Código/Nome com Badge + Ações */}
                  <div className="flex items-start justify-between gap-3">
                    <div className="flex items-center gap-2.5 flex-1 min-w-0">
                      {selectable && (
                        <input
                          type="checkbox"
                          checked={isSelected}
                          onChange={() => toggleRowSelection(item.id)}
                          className="w-5 h-5 rounded text-blue-500 focus:ring-blue-400 shrink-0 cursor-pointer"
                          aria-label={`Selecionar item ${displayName}`}
                        />
                      )}
                      <div
                        className="flex items-center gap-2 truncate cursor-pointer"
                        onClick={() => handleRowDoubleClick(item)}
                      >
                        {isEquipment ? (
                          <>
                            <span className="font-bold text-base text-emerald-600 truncate">
                              {item.code}
                            </span>
                            {'isValid' in item &&
                              item.isValid !== undefined && (
                                <span
                                  className={`text-[11px] font-semibold px-2 py-0.5 rounded-full shrink-0 ${
                                    item.isValid
                                      ? 'bg-emerald-100 text-emerald-700'
                                      : 'bg-amber-100 text-amber-700'
                                  }`}
                                >
                                  {item.isValid ? 'Verificado' : 'Pendente'}
                                </span>
                              )}
                          </>
                        ) : (
                          <span className="font-bold text-base text-slate-800 truncate">
                            {'name' in item ? item.name : `#${item.id}`}
                          </span>
                        )}
                      </div>
                    </div>

                    {/* Botões de Ação do Card: Editar e Excluir com touch target >= 44x44px */}
                    <div className="flex items-center gap-1 shrink-0">
                      {onEditItem && 'name' in item && (
                        <button
                          type="button"
                          onClick={() => onEditItem(item as InventoryResponse)}
                          className="min-h-[44px] min-w-[44px] p-2 text-slate-500 rounded-xl hover:bg-blue-50 hover:text-blue-500 transition-colors flex items-center justify-center cursor-pointer"
                          title={`Editar ${displayName}`}
                          aria-label={`Editar ${displayName}`}
                        >
                          <RiPencilFill size={18} />
                        </button>
                      )}
                      {onDeleteItem && (
                        <button
                          type="button"
                          onClick={() => onDeleteItem(item)}
                          className="min-h-[44px] min-w-[44px] p-2 text-slate-500 rounded-xl hover:bg-red-50 hover:text-red-500 transition-colors flex items-center justify-center cursor-pointer"
                          title={`Deletar ${displayName}`}
                          aria-label={`Deletar ${displayName}`}
                        >
                          <FaTrash size={16} />
                        </button>
                      )}
                    </div>
                  </div>

                  {/* Descrição do Item */}
                  {'description' in item && item.description && (
                    <p
                      className="text-xs text-slate-700 line-clamp-3 leading-relaxed cursor-pointer"
                      onClick={() => handleRowDoubleClick(item)}
                    >
                      {item.description}
                    </p>
                  )}

                  {/* Campos secundários organizados em chave-valor */}
                  <div
                    className="grid grid-cols-1 gap-1 text-xs text-slate-600 cursor-pointer pt-1"
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

                      return (
                        <div
                          key={col.key}
                          className="flex items-baseline justify-between gap-2 border-b border-slate-50 py-1"
                        >
                          <span className="font-semibold text-slate-500 shrink-0">
                            {col.headerText}:
                          </span>
                          <span className="text-slate-800 text-right truncate max-w-[220px]">
                            {formattedVal}
                          </span>
                        </div>
                      )
                    })}
                  </div>

                  {/* Botão de navegação tátil rápida "Ver detalhes" */}
                  {onRowDoubleClick && (
                    <button
                      type="button"
                      onClick={() => handleRowDoubleClick(item)}
                      className="mt-1 w-full min-h-[44px] py-2 px-3 text-xs font-semibold text-blue-500 bg-blue-50 hover:bg-blue-100 rounded-xl transition-colors flex items-center justify-center gap-1.5 cursor-pointer"
                    >
                      <span>Ver detalhes</span>
                      <IoIosArrowForward className="w-3.5 h-3.5" />
                    </button>
                  )}
                </div>
              )
            })
          )}
        </div>

        {/* 2. VISUALIZAÇÃO TABLET E DESKTOP (>= 640px): Tabela Tradicional com overflow horizontal se necessário */}
        <div className="hidden sm:block w-full overflow-x-auto">
          <table className="table-auto w-full bg-white">
            <thead className="border-b border-slate-200 bg-slate-50/50">
              <TableRow key={'headerRow'}>
                {table.getHeaderGroups()[0].headers.map((item) => {
                  const canSort = item.column.getCanSort()

                  return (
                    <TableHeaderCell
                      text={flexRender(
                        item.column.columnDef.header,
                        item.getContext(),
                      )}
                      key={item.id}
                      onClick={
                        canSort ? () => item.column.toggleSorting() : undefined
                      }
                      canSort={item.column.getCanSort()}
                    />
                  )
                })}
              </TableRow>
            </thead>
            <tbody className="divide-y divide-slate-200">
              {table.getRowModel().rows.map((item) => (
                <TableRow
                  key={item.original.id}
                  className="hover:bg-slate-50 transition-colors"
                  onDoubleClick={() => handleRowDoubleClick(item.original)}
                >
                  {item.getVisibleCells().map((cell) => (
                    <TableCell
                      key={cell.id}
                      text={flexRender(
                        cell.column.columnDef.cell,
                        cell.getContext(),
                      )}
                    />
                  ))}
                </TableRow>
              ))}
            </tbody>
          </table>
        </div>

        {/* Controles de paginação no rodapé (compartilhados por ambas as visualizações) */}
        <div className="bg-white text-center flex justify-center items-center p-4 border-t border-slate-300 flex-row gap-8 sm:gap-12">
          {table.getCanPreviousPage() && (
            <button
              onClick={() => table.previousPage()}
              aria-label="Página anterior"
              title="Página anterior"
              className="inline-flex items-center p-2 rounded-xl hover:bg-slate-100 transition-colors min-h-[44px] min-w-[44px] justify-center cursor-pointer"
            >
              <IoIosArrowBack className="text-2xl text-slate-700" />
            </button>
          )}
          <span
            aria-label={`Página ${table.getState().pagination.pageIndex + 1}`}
            title={`Página ${table.getState().pagination.pageIndex + 1}`}
            className="text-sm sm:text-base font-medium text-slate-700 min-w-[44px] flex items-center justify-center"
          >
            {table.getState().pagination.pageIndex + 1}
          </span>
          {table.getCanNextPage() && (
            <button
              onClick={() => table.nextPage()}
              aria-label="Próxima página"
              title="Próxima página"
              className="inline-flex items-center p-2 rounded-xl hover:bg-slate-100 transition-colors min-h-[44px] min-w-[44px] justify-center cursor-pointer"
            >
              <IoIosArrowForward className="text-2xl text-slate-700" />
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
