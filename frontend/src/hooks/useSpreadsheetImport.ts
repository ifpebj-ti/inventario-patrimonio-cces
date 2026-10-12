'use client'

import { useEffect, useRef, useState } from 'react'
import toast from 'react-hot-toast'
import type { SpreadsheetState } from '@/commons/models/spreadsheet'
import {
  getSpreadsheetFailure,
  inspectCsv,
  SpreadsheetRequestError,
} from '@/commons/utils/spreadsheet'
import { addItemsBySheet, validateSpreadsheetData } from '@/services/inventory'

const initialState: SpreadsheetState = {
  file: null,
  status: 'idle',
  message: '',
  errors: [],
  report: null,
}

export const useSpreadsheetImport = (
  inventoryId: number,
  onImported: () => Promise<void>,
) => {
  const [state, setState] = useState(initialState)
  const current = useRef(initialState)
  const version = useRef(0)
  const validation = useRef<AbortController | null>(null)
  const importing = useRef(false)
  const update = (next: SpreadsheetState) => {
    current.current = next
    setState(next)
  }

  useEffect(() => {
    version.current++
    validation.current?.abort()
    current.current = initialState
    setState(initialState)
    return () => {
      version.current++
      validation.current?.abort()
    }
  }, [inventoryId])

  const remove = () => {
    if (importing.current) return
    version.current++
    validation.current?.abort()
    update(initialState)
  }

  const select = async (file: File) => {
    if (importing.current) return
    const requestVersion = ++version.current
    validation.current?.abort()
    const controller = new AbortController()
    validation.current = controller
    update({
      ...initialState,
      file,
      status: 'validating',
      message: 'Validando arquivo...',
    })
    try {
      const errors = await inspectCsv(file)
      if (requestVersion !== version.current) return
      if (errors.length > 0)
        throw new SpreadsheetRequestError(
          'Corrija os erros da planilha antes de importar.',
          errors,
        )
      await validateSpreadsheetData(file, controller.signal)
      if (requestVersion !== version.current) return
      update({
        ...initialState,
        file,
        status: 'valid',
        message: 'Planilha validada. Clique em Processar para importar.',
      })
    } catch (error) {
      if (requestVersion !== version.current) return
      const failure = getSpreadsheetFailure(error)
      update({
        ...initialState,
        file,
        status: 'invalid',
        message: failure.message,
        errors: failure.errors,
      })
      toast.error(failure.message)
    }
  }

  const process = async () => {
    const approved = current.current
    if (approved.status !== 'valid' || !approved.file || importing.current)
      return
    importing.current = true
    const requestVersion = version.current
    update({
      ...approved,
      status: 'importing',
      message: 'Processando e importando planilha...',
    })
    try {
      const report = await addItemsBySheet(approved.file, inventoryId)
      if (requestVersion !== version.current) return
      const message = `Importação concluída: ${report.successes} itens adicionados, ${report.failures} falharam.`
      update({
        ...approved,
        status: 'completed',
        message,
        errors: report.errors,
        report,
      })
      toast.success(message)
      await onImported()
    } catch (error) {
      if (requestVersion !== version.current) return
      const failure = getSpreadsheetFailure(error)
      update({
        ...approved,
        status: 'invalid',
        message: failure.message,
        errors: failure.errors,
        report: failure.report,
      })
      toast.error(failure.message)
      // Uma falha de transporte pode ocorrer depois de itens terem sido gravados.
      await onImported()
    } finally {
      importing.current = false
    }
  }

  return { state, select, remove, process }
}
