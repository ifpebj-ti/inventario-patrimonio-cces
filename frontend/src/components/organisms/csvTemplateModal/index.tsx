'use client'

import React from 'react'
import { downloadCsvTemplate } from '@/commons/utils/downloadCsvTemplate'
import { TbFileSpreadsheet, TbDownload, TbX } from 'react-icons/tb'

interface CsvTemplateModalProps {
  isOpen: boolean
  onClose: () => void
}

export const CsvTemplateModal: React.FC<CsvTemplateModalProps> = ({
  isOpen,
  onClose,
}) => {
  if (!isOpen) return null

  const handleBackdropClick = (e: React.MouseEvent) => {
    if (e.target === e.currentTarget) {
      onClose()
    }
  }

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
    <>
      {/* Backdrop */}
      <div
        className="fixed inset-0 bg-black/40 backdrop-blur-xs z-60 transition-opacity"
        onClick={handleBackdropClick}
      />

      {/* Container Centralizado */}
      <div className="fixed inset-0 flex justify-center items-center z-60 p-4 sm:p-6 overflow-y-auto">
        <div className="bg-white rounded-2xl sm:rounded-3xl shadow-2xl border border-slate-100 p-5 sm:p-7 relative w-full max-w-3xl max-h-[92vh] flex flex-col gap-5 overflow-y-auto font-['Montserrat',sans-serif]">
          {/* Botão Fechar */}
          <button
            onClick={onClose}
            className="absolute top-4 right-4 p-2 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-full transition-colors cursor-pointer"
            aria-label="Fechar modal"
          >
            <TbX className="w-5 h-5" />
          </button>

          {/* Cabeçalho do Modal */}
          <div className="flex items-start gap-3.5 pr-8">
            <div className="p-3 bg-blue-50 text-blue-600 rounded-2xl shrink-0">
              <TbFileSpreadsheet className="w-6 h-6" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h2 className="font-['Linden_Hill',serif] text-2xl sm:text-3xl font-bold text-slate-800">
                  Modelo de Planilha
                </h2>
                {/* <span className="text-[11px] font-bold px-2.5 py-0.5 rounded-full bg-emerald-50 text-emerald-700 border border-emerald-200">
                  Apenas .csv
                </span> */}
              </div>
              <p className="text-xs sm:text-sm text-slate-500 mt-0.5">
                Consulte as colunas exigidas pelo sistema ou baixe o arquivo
                modelo pronto para preenchimento. O sistema aceita
                exclusivamente arquivos com extensão{' '}
                <span className="font-mono font-bold bg-amber-100/70 px-1 py-0.5 rounded">
                  .csv
                </span>
              </p>
            </div>
          </div>

          {/* Banner de Aviso de Formato Estrito
          <div className="p-3.5 bg-amber-50 border border-amber-200 rounded-xl flex items-start gap-3 text-amber-900">
            <TbAlertCircle className="w-5 h-5 text-amber-600 shrink-0 mt-0.5" />
            <div className="text-xs sm:text-[13px] leading-relaxed">
              <strong className="font-semibold">
                Regra de formato estrito:
              </strong>{' '}
              O sistema aceita <u>exclusivamente</u> arquivos com extensão{' '}
              <span className="font-mono font-bold bg-amber-100/70 px-1 py-0.5 rounded">
                .csv
              </span>
              . Documentos em outros formatos (como{' '}
              <em>.xlsx, .xls, .pdf, fotos ou vídeos</em>) serão{' '}
              <strong>bloqueados</strong> automaticamente antes do envio.
            </div>
          </div> */}

          {/* Tabela de Colunas Esperadas */}
          <div>
            <div className="flex items-center justify-between mb-2.5">
              <h3 className="text-sm font-bold text-slate-800 flex items-center gap-2">
                {/* <TbTable className="w-4 h-4 text-blue-500" /> */}
                Estrutura de Colunas Esperada
              </h3>
              {/* <span className="text-xs text-slate-400">
                A primeira linha deve ser o cabeçalho
              </span> */}
            </div>

            <div className="border border-slate-200 rounded-xl overflow-hidden shadow-xs">
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs sm:text-[13px]">
                  <thead className="bg-slate-50 text-slate-600 font-semibold border-b border-slate-200">
                    <tr>
                      <th className="py-2.5 px-3">Coluna</th>
                      <th className="py-2.5 px-3">Tipo / Formato</th>
                      <th className="py-2.5 px-3">Exemplo</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 text-slate-700">
                    {columnsData.map((col, idx) => (
                      <tr
                        key={idx}
                        className={
                          idx % 2 === 0 ? 'bg-white' : 'bg-slate-50/50'
                        }
                      >
                        <td className="py-2.5 px-3 font-semibold text-slate-900">
                          {col.name}
                        </td>
                        <td className="py-2.5 px-3 text-slate-600 text-xs">
                          {col.type}
                        </td>
                        <td className="py-2.5 px-3 font-mono text-[11px] text-slate-500">
                          {col.example}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>

          {/* Rodapé com Ações */}
          <div className="flex flex-col-reverse sm:flex-row items-center justify-between gap-3 pt-3 border-t border-slate-100 mt-1">
            {/* <button
              type="button"
              onClick={onClose}
              className="w-full sm:w-auto px-5 py-2.5 bg-slate-100 hover:bg-slate-200 text-slate-700 font-medium rounded-xl text-xs sm:text-sm transition-colors cursor-pointer"
            >
              Fechar
            </button> */}

            <button
              type="button"
              onClick={downloadCsvTemplate}
              className="w-full sm:w-auto flex items-center justify-center gap-2 px-5 py-2.5 bg-blue-600 hover:bg-blue-700 text-white font-semibold rounded-xl text-xs sm:text-sm shadow-md hover:shadow-lg transition-all cursor-pointer"
            >
              <TbDownload className="w-4 h-4" />
              Baixar Modelo de Planilha (.csv)
            </button>
          </div>
        </div>
      </div>
    </>
  )
}
