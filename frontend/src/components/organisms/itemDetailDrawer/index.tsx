'use client'

import React, { useState, useEffect } from 'react'
import { Item, Observation } from '@/commons/models/item'
import {
  X,
  CheckCircle2,
  Clock,
  MapPin,
  User,
  Plus,
  Trash2,
  Barcode,
  Camera,
  Save,
  Lock,
} from 'lucide-react'
import { AnimatePresence, motion } from 'framer-motion'
import { updateItemRequest, updateItemNotesRequest } from '@/services/item'
import toast from 'react-hot-toast'

interface ItemDetailDrawerProps {
  isOpen: boolean
  onClose: () => void
  item: Item | null
  onItemUpdated: (updatedItem: Item) => void
  onScanNext?: () => void
  isResponsibleLocked?: boolean
}

export const ItemDetailDrawer: React.FC<ItemDetailDrawerProps> = ({
  isOpen,
  onClose,
  item,
  onItemUpdated,
  onScanNext,
  isResponsibleLocked = true,
}) => {
  const [formData, setFormData] = useState<Item | null>(null)
  const [observations, setObservations] = useState<Observation[]>([])
  const [newNoteText, setNewNoteText] = useState('')
  const [isSaving, setIsSaving] = useState(false)

  // Sincroniza dados sempre que o item muda ou a Drawer abre
  useEffect(() => {
    if (item) {
      setFormData({ ...item })
      setObservations(item.observations ? [...item.observations] : [])
      setNewNoteText('')
    }
  }, [item, isOpen])

  if (!isOpen || !formData) return null

  // Alterna o status de validação (Válido / Pendente)
  const toggleValidationStatus = () => {
    setFormData((prev) => (prev ? { ...prev, isValid: !prev.isValid } : null))
  }

  // Adiciona nova observação
  const handleAddObservation = () => {
    if (!newNoteText.trim()) return
    const newObs: Observation = {
      id: null,
      content: newNoteText.trim(),
    }
    setObservations((prev) => [...prev, newObs])
    setNewNoteText('')
  }

  // Remove observação
  const handleDeleteObservation = (index: number) => {
    setObservations((prev) => prev.filter((_, i) => i !== index))
  }

  // Salva no backend
  const handleSave = async (shouldScanNext = false) => {
    if (!formData) return

    try {
      setIsSaving(true)

      // 1. Atualiza dados principais do item
      const updatedItem = await updateItemRequest({
        ...formData,
        price: Number(formData.price) || 0,
      })

      // 2. Atualiza observações se houver alterações
      const finalItem = await updateItemNotesRequest(
        updatedItem.id,
        observations,
      )

      onItemUpdated(finalItem)
      toast.success(`Patrimônio ${finalItem.code} atualizado!`)

      if (shouldScanNext && onScanNext) {
        onScanNext()
      } else {
        onClose()
      }
    } catch (error) {
      console.error('Erro ao salvar item:', error)
      toast.error('Não foi possível salvar as alterações do item.')
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <AnimatePresence>
      <div className="fixed inset-0 z-50 overflow-hidden font-['Montserrat',sans-serif]">
        {/* Backdrop com desfoque */}
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          onClick={onClose}
          aria-hidden="true"
          className="absolute inset-0 bg-black/40 backdrop-blur-xs transition-opacity"
        />

        {/* Painel Lateral Deslizante */}
        <div className="fixed inset-y-0 right-0 max-w-full flex pl-6 sm:pl-10">
          <motion.div
            initial={{ x: '100%' }}
            animate={{ x: 0 }}
            exit={{ x: '100%' }}
            transition={{ type: 'spring', damping: 26, stiffness: 220 }}
            className="w-screen max-w-md bg-white shadow-2xl flex flex-col justify-between"
          >
            {/* Topo / Header */}
            <div className="px-5 sm:px-6 py-4 border-b border-slate-100 flex items-center justify-between bg-slate-50/70">
              <div className="flex items-center gap-2.5 min-w-0">
                <div className="p-2 rounded-xl bg-blue-50 text-blue-500 shrink-0">
                  <Barcode className="w-5 h-5" />
                </div>
                <div className="truncate">
                  <span className="text-[11px] font-bold text-slate-400 uppercase tracking-wider block">
                    Item Auditado
                  </span>
                  <h2 className="font-['Linden_Hill',serif] text-2xl sm:text-3xl font-bold text-slate-900 truncate">
                    {formData.code}
                  </h2>
                </div>
              </div>

              <button
                type="button"
                onClick={onClose}
                aria-label="Fechar gaveta"
                className="min-h-[44px] min-w-[44px] flex items-center justify-center rounded-xl text-slate-400 hover:text-slate-600 hover:bg-slate-100 transition-colors cursor-pointer"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Corpo / Conteúdo com Scroll */}
            <div className="flex-1 overflow-y-auto px-5 sm:px-6 py-5 space-y-5">
              {/* Toggle de Status: Verificado vs Pendente */}
              <div className="p-4 rounded-2xl bg-slate-50 border border-slate-100 flex items-center justify-between gap-3">
                <div className="min-w-0">
                  <span className="text-xs font-semibold text-slate-700 block">
                    Status de Verificação
                  </span>
                  <span className="text-[11px] text-slate-500 leading-tight block">
                    {formData.isValid
                      ? 'Item conferido fisicamente no local.'
                      : 'Pendente de conferência ou ausente.'}
                  </span>
                </div>

                <button
                  type="button"
                  onClick={toggleValidationStatus}
                  className={`min-h-[44px] px-3.5 py-1.5 rounded-xl font-semibold text-xs flex items-center gap-1.5 transition-all shadow-xs cursor-pointer shrink-0 active:scale-95 ${
                    formData.isValid
                      ? 'bg-emerald-500 hover:bg-emerald-600 text-white'
                      : 'bg-amber-500 hover:bg-amber-600 text-white'
                  }`}
                >
                  {formData.isValid ? (
                    <>
                      <CheckCircle2 className="w-4 h-4" />
                      <span>Verificado</span>
                    </>
                  ) : (
                    <>
                      <Clock className="w-4 h-4" />
                      <span>Pendente</span>
                    </>
                  )}
                </button>
              </div>

              {/* Informações Básicas do Item */}
              <div className="space-y-1.5">
                <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider">
                  Descrição
                </label>
                <textarea
                  rows={2}
                  value={formData.description || ''}
                  onChange={(e) =>
                    setFormData({ ...formData, description: e.target.value })
                  }
                  className="w-full p-3 rounded-xl border border-slate-200 text-xs sm:text-sm text-slate-800 focus:border-blue-400 outline-none transition-all resize-none"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1.5">
                  <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider">
                    Sala / Local
                  </label>
                  <div className="flex items-center gap-2 p-2.5 rounded-xl border border-slate-200">
                    <MapPin className="w-4 h-4 text-slate-400 shrink-0" />
                    <input
                      type="text"
                      value={formData.locale || ''}
                      onChange={(e) =>
                        setFormData({ ...formData, locale: e.target.value })
                      }
                      className="w-full text-xs text-slate-800 outline-none bg-transparent"
                      placeholder="Ex: Sala 102"
                    />
                  </div>
                </div>

                <div className="space-y-1.5">
                  <div className="flex items-center justify-between">
                    <label className="block text-xs font-bold text-slate-500 uppercase tracking-wider">
                      Carga (Responsável)
                    </label>
                    {isResponsibleLocked && (
                      <span className="inline-flex items-center gap-1 text-[10px] font-semibold text-slate-500 bg-slate-100 px-1.5 py-0.5 rounded border border-slate-200">
                        <Lock className="w-3 h-3 text-slate-400" />
                        Bloqueado
                      </span>
                    )}
                  </div>
                  <div
                    className={`flex items-center gap-2 p-2.5 rounded-xl border ${
                      isResponsibleLocked
                        ? 'border-slate-200 bg-slate-100/80 text-slate-500 cursor-not-allowed'
                        : 'border-slate-200 bg-white text-slate-800'
                    }`}
                  >
                    <User className="w-4 h-4 text-slate-400 shrink-0" />
                    <input
                      type="text"
                      readOnly={isResponsibleLocked}
                      disabled={isResponsibleLocked}
                      value={formData.responsible || ''}
                      onChange={(e) =>
                        setFormData({
                          ...formData,
                          responsible: e.target.value,
                        })
                      }
                      className={`w-full text-xs outline-none bg-transparent ${
                        isResponsibleLocked
                          ? 'cursor-not-allowed text-slate-600 font-medium'
                          : 'text-slate-800'
                      }`}
                      placeholder="Nome do servidor"
                    />
                  </div>
                </div>
              </div>

              {/* Observações de Campo */}
              <div className="space-y-3 pt-3 border-t border-slate-100">
                <div className="flex items-center justify-between">
                  <label className="text-xs font-bold text-slate-600 uppercase tracking-wider">
                    Observações de Campo ({observations.length})
                  </label>
                </div>

                {/* Input de Nova Observação */}
                <div className="flex items-center gap-2">
                  <input
                    type="text"
                    value={newNoteText}
                    onChange={(e) => setNewNoteText(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        e.preventDefault()
                        handleAddObservation()
                      }
                    }}
                    placeholder="Adicionar nota de campo..."
                    className="flex-1 p-2.5 rounded-xl border border-slate-200 text-xs text-slate-800 focus:border-blue-400 outline-none"
                  />
                  <button
                    type="button"
                    onClick={handleAddObservation}
                    aria-label="Adicionar observação"
                    className="min-h-[40px] px-3 rounded-xl bg-blue-50 text-blue-600 hover:bg-blue-100 transition-colors font-medium text-xs flex items-center justify-center cursor-pointer shrink-0"
                  >
                    <Plus className="w-4 h-4" />
                  </button>
                </div>

                {/* Lista de Observações */}
                <div className="space-y-2 max-h-48 overflow-y-auto pr-1">
                  {observations.length === 0 ? (
                    <p className="text-xs text-slate-400 italic py-2">
                      Nenhuma observação registrada para este item.
                    </p>
                  ) : (
                    observations.map((obs, idx) => (
                      <div
                        key={idx}
                        className="flex items-start justify-between gap-2 p-2.5 bg-slate-50 rounded-xl border border-slate-100 text-xs text-slate-700"
                      >
                        <span className="leading-relaxed">{obs.content}</span>
                        <button
                          type="button"
                          onClick={() => handleDeleteObservation(idx)}
                          aria-label="Deletar observação"
                          className="text-slate-400 hover:text-red-500 p-1 rounded-md transition-colors cursor-pointer shrink-0"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    ))
                  )}
                </div>
              </div>
            </div>

            {/* Rodapé de Ações Finais */}
            <div className="p-5 border-t border-slate-100 bg-slate-50/70 flex flex-col gap-2">
              <button
                type="button"
                onClick={() => handleSave(false)}
                disabled={isSaving}
                className="w-full min-h-[44px] py-2.5 px-4 rounded-xl bg-blue-400 hover:bg-blue-500 active:bg-blue-600 text-white font-semibold text-xs sm:text-sm shadow-xs transition-all flex items-center justify-center gap-2 cursor-pointer disabled:opacity-50"
              >
                <Save className="w-4 h-4" />
                <span>{isSaving ? 'Salvando...' : 'Salvar Alterações'}</span>
              </button>

              {onScanNext && (
                <button
                  type="button"
                  onClick={() => handleSave(true)}
                  disabled={isSaving}
                  className="w-full min-h-[44px] py-2.5 px-4 rounded-xl bg-white hover:bg-slate-100 border border-slate-200 text-slate-700 font-semibold text-xs sm:text-sm transition-all flex items-center justify-center gap-2 cursor-pointer disabled:opacity-50"
                >
                  <Camera className="w-4 h-4 text-blue-500" />
                  <span>Salvar e Escanear Próximo</span>
                </button>
              )}
            </div>
          </motion.div>
        </div>
      </div>
    </AnimatePresence>
  )
}
