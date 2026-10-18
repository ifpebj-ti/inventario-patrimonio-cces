'use client'

import { Badge as UiBadge } from '@/components/ui/badge'
import { Button as UiButton } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Table as UiTable,
  TableHeader as UiTableHeader,
  TableRow as UiTableRow,
  TableHead as UiTableHead,
  TableBody as UiTableBody,
  TableCell as UiTableCell,
} from '@/components/ui/table'

import React from 'react'
import { downloadCsvTemplate } from '@/commons/utils/downloadCsvTemplate'
import { Download, FileSpreadsheet } from 'lucide-react'

interface CsvTemplateModalProps {
  isOpen: boolean
  onClose: () => void
}

export const CsvTemplateModal: React.FC<CsvTemplateModalProps> = ({
  isOpen,
  onClose,
}) => {
  const columnsData = [
    {
      name: 'Código',
      required: true,
      type: 'Texto (até 30 caracteres)',
      description:
        'Identificador patrimonial único. Obrigatório no banco de dados e na validação.',
      example: '102345',
    },
    {
      name: 'Descrição',
      required: false,
      type: 'Texto livre',
      description: 'Nome e especificações do bem ou equipamento.',
      example: 'Notebook Dell Latitude 3420',
    },
    {
      name: 'Responsável',
      required: false,
      type: 'Texto livre',
      description: 'Pessoa, setor ou departamento que detém a carga do bem.',
      example: 'Coordenação de TI',
    },
    {
      name: 'Valor',
      required: false,
      type: 'Numérico em R$ (>= 0,00)',
      description:
        'Valor monetário em reais. Opcional (se omitido, o sistema assume 0,00).',
      example: '3500,00',
    },
    {
      name: 'Sala',
      required: false,
      type: 'Texto livre',
      description:
        'Localização física, laboratório ou sala onde o item se encontra.',
      example: 'Laboratório 02',
    },
    {
      name: 'Observações',
      required: false,
      type: 'Texto livre',
      description:
        'Anotações complementares sobre o estado ou situação do bem.',
      example: 'Em perfeito estado de uso',
    },
  ]

  return (
    <Dialog
      open={isOpen}
      onOpenChange={(open) => {
        if (!open) onClose()
      }}
    >
      <DialogContent className="max-h-[92vh] gap-5 overflow-y-auto rounded-xl p-5 sm:max-w-3xl sm:p-7">
        {/* Cabeçalho do Modal */}
        <DialogHeader className="flex-row items-start gap-3.5 pr-8 text-left">
          <div className="shrink-0 rounded-lg bg-primary/10 p-3 text-primary">
            <FileSpreadsheet className="size-6" />
          </div>
          <div>
            <DialogTitle className="text-2xl font-bold sm:text-3xl">
              Modelo de Planilha
            </DialogTitle>
            <DialogDescription className="mt-0.5 text-xs sm:text-sm">
              Consulte as colunas exigidas pelo sistema ou baixe o arquivo
              modelo pronto para preenchimento. O sistema aceita exclusivamente
              arquivos com extensão{' '}
              <UiBadge
                variant="outline"
                className="border-warning/25 bg-warning/10 font-mono font-bold text-warning"
              >
                .csv
              </UiBadge>
            </DialogDescription>
          </div>
        </DialogHeader>

        {/* Tabela de Colunas Esperadas */}
        <div>
          <h3 className="mb-2.5 text-sm font-bold text-foreground">
            Estrutura de Colunas Esperada
          </h3>

          <div className="overflow-hidden rounded-lg border shadow-xs">
            <UiTable className="text-xs sm:text-[13px]">
              <UiTableHeader className="bg-muted">
                <UiTableRow>
                  <UiTableHead className="px-3">Coluna</UiTableHead>
                  <UiTableHead className="px-3">Tipo / Formato</UiTableHead>
                  <UiTableHead className="px-3">Exemplo</UiTableHead>
                </UiTableRow>
              </UiTableHeader>
              <UiTableBody>
                {columnsData.map((col) => (
                  <UiTableRow key={col.name}>
                    <UiTableCell className="px-3 font-semibold">
                      {col.name}
                    </UiTableCell>
                    <UiTableCell className="px-3 text-xs text-muted-foreground">
                      {col.type}
                    </UiTableCell>
                    <UiTableCell className="px-3 font-mono text-[11px] text-muted-foreground">
                      {col.example}
                    </UiTableCell>
                  </UiTableRow>
                ))}
              </UiTableBody>
            </UiTable>
          </div>
        </div>

        {/* Rodapé com Ações */}
        <DialogFooter className="border-t pt-4 sm:justify-start">
          <UiButton
            type="button"
            onClick={downloadCsvTemplate}
            className="w-full text-xs sm:w-auto sm:text-sm"
          >
            <Download />
            Baixar Modelo de Planilha (.csv)
          </UiButton>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
