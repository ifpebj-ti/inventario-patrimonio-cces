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
              <input
                type="checkbox"
                checked={isAllSelected}
                // 'ref' é usado aqui para controlar o estado "indeterminado" do checkbox.
                ref={(ref) => {
                  if (ref) ref.indeterminate = isIndeterminate
                }}
                onChange={toggleAllSelection}
                className="form-checkbox h-4 w-4"
              />
            ),
            // 'cell' define o que é renderizado em cada célula do corpo da tabela para esta coluna.
            cell: ({ row }: { row: Row<Item> }) => (
              <input
                type="checkbox"
                checked={selectedRows.includes(row.original.id)}
                onChange={() => toggleRowSelection(row.original.id)}
                className="form-checkbox h-4 w-4"
              />
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
          header: item.headerText,
          cell: ({ row }: { row: Row<Item> }) => (
            // Aplica uma cor diferente ao texto com base na propriedade 'isValid' do item.
            <span
              className={
                row.original.isValid
                  ? 'text-green-500 font-medium'
                  : 'text-red-500 font-medium'
              }
            >
              {row.original.code}
            </span>
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
            header: () => <div className="text-center">Ações</div>,
            cell: ({ row }: { row: Row<InventoryResponse | Item> }) => {
              const item = row.original
              const displayName = 'name' in item ? item.name : item.code

              return (
                <div className="flex justify-center items-center gap-2">
                  {/* Botão de Editar, renderizado apenas se 'onEditItem' for passado. */}
                  {onEditItem && (
                    <button
                      onClick={() => onEditItem(item)}
                      className="p-2 text-slate-500 rounded-full hover:bg-blue-100 hover:text-blue-600 transition-colors cursor-pointer"
                      title={`Editar ${displayName}`}
                      aria-label={`Editar ${displayName}`}
                    >
                      <RiPencilFill />
                    </button>
                  )}
                  {/* Botão de Deletar, renderizado apenas se 'onDeleteItem' for passado. */}
                  {onDeleteItem && (
                    <button
                      onClick={() => onDeleteItem(item)}
                      className="p-2 text-slate-500 rounded-full hover:bg-red-100 hover:text-red-600 transition-colors cursor-pointer"
                      title={`Deletar ${displayName}`}
                      aria-label={`Deletar ${displayName}`}
                    >
                      <FaTrash />
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
  const handleRowDoubleClick = (rowData: Item) => {
    if (onRowDoubleClick) {
      onRowDoubleClick(rowData)
    }
  }

  return (
    <div className="p-4 sm:p-6 lg:p-8 max-w-[90rem] mx-auto">
      {/* Barra de ferramentas acima da tabela, com filtro e botões de exportação. */}
      <div className="flex justify-between items-center border-slate-200 border-[1] rounded-t-2xl w-full">
        {/* Input para o filtro global, que busca em todas as colunas. */}
        <Input
          variant={2}
          placeholder="Filtrar..."
          value={globalFilter}
          onChange={(e) => setGlobalFilter(e.target.value)}
        />
        {/* Botões de exportação, renderizados apenas se 'showExportButtons' for true. */}
        {showExportButtons && (
          <div className="flex mr-4 gap-2">
            <Button
              type="button"
              variant={5}
              onClick={onExportSheet}
              icon={<FaFileExcel />}
              tooltip="Exportar planilha Excel"
              aria-label="Exportar planilha Excel"
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
            />
            <Button
              type="button"
              variant={6}
              onClick={onExportAll}
              icon={<MdLibraryBooks />}
              tooltip="Exportar todas as etiquetas"
              aria-label="Exportar todas as etiquetas"
            />
            <Button
              type="button"
              variant={7}
              onClick={onSendEmailSheet}
              icon={<FaEnvelope />}
              tooltip="Enviar planilha por e-mail"
              aria-label="Enviar planilha por e-mail"
            />
          </div>
        )}
      </div>
      {/* Container principal da tabela com sombra e bordas. */}
      <div className="shadow-xl rounded-[4] overflow-hidden border border-slate-200">
        {/* 'overflow-x-auto' cria uma barra de rolagem horizontal se a tabela for mais larga que a tela. */}
        <div className="overflow-x-auto">
          <table className="min-w-full bg-white">
            <thead className="border-b-[1] border-slate-300">
              {/* Mapeia os grupos de cabeçalho (geralmente apenas um) para renderizar o <thead>. */}
              <TableRow key={'headerRow'}>
                {table.getHeaderGroups()[0].headers.map((item) => {
                  const canSort = item.column.getCanSort()

                  return (
                    <TableHeaderCell
                      // 'flexRender' é um utilitário do TanStack para renderizar o conteúdo do header.
                      text={flexRender(
                        item.column.columnDef.header,
                        item.getContext(),
                      )}
                      key={item.id}
                      // Adiciona a função de ordenação ao clique se a coluna for ordenável.
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
              {/* Mapeia as linhas do modelo da tabela para renderizar o <tbody>. */}
              {table.getRowModel().rows.map((item) => (
                <TableRow
                  key={item.original.id}
                  className="hover:bg-slate-100"
                  onDoubleClick={() => handleRowDoubleClick(item.original)}
                >
                  {/* Para cada linha, mapeia as células visíveis. */}
                  {item.getVisibleCells().map((cell) => (
                    <TableCell
                      key={cell.id}
                      // 'flexRender' novamente, agora para renderizar o conteúdo da célula.
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
        {/* Controles de paginação no rodapé da tabela. */}
        <div className="bg-white text-center flex justify-center items-center p-4 border-t-1 border-slate-300 flex-row gap-12">
          {/* Botão de voltar, visível apenas se houver uma página anterior. */}
          {table.getCanPreviousPage() && (
            <button
              onClick={() => table.previousPage()}
              aria-label="Página anterior"
              title="Página anterior"
              className="inline-flex items-center"
            >
              <IoIosArrowBack className="text-3xl text-slate-700 cursor-pointer" />
            </button>
          )}
          {/* Mostra o número da página atual. */}
          <span
            aria-label={`Página ${table.getState().pagination.pageIndex + 1}`}
            title={`Página ${table.getState().pagination.pageIndex + 1}`}
          >
            {table.getState().pagination.pageIndex + 1}
          </span>
          {/* Botão de avançar, visível apenas se houver uma próxima página. */}
          {table.getCanNextPage() && (
            <button
              onClick={() => table.nextPage()}
              aria-label="Próxima página"
              title="Próxima página"
              className="inline-flex items-center"
            >
              <IoIosArrowForward className="text-3xl text-slate-700 cursor-pointer" />
            </button>
          )}
        </div>
      </div>
    </div>
  )
}
