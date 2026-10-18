'use client'

import { SelectField } from '@/components/ui/select-field'
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog'
import { Input as UiInput } from '@/components/ui/input'
import { DatePicker } from '@/components/ui/date-picker'

import { Button as UiButton } from '@/components/ui/button'
import { X } from 'lucide-react'
import { Label as UiLabel } from '@/components/ui/label'

import { Controller, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useEffect, useState } from 'react'
import { InventoryResponse } from '@/services/inventory'
import { listSectors, administrationFailure } from '@/services/administration'
import type { Sector } from '@/commons/models/administration'

// Schema de validação do formulário de edição, utilizando a biblioteca Zod.
const editInventorySchema = z.object({
  name: z.string().min(3, 'O nome deve ter pelo menos 3 caracteres.'),
  description: z.string().optional(),
  sectorId: z.coerce.number().int().positive('Selecione o setor responsável.'),
  plannedEndDate: z
    .string()
    .regex(/^\d{4}-\d{2}-\d{2}$/, 'Informe uma data válida'),
})

// Extrai o tipo TypeScript a partir do schema Zod para garantir a segurança de tipos.
type EditFormData = z.infer<typeof editInventorySchema>

const toDateInputValue = (date: string | null | undefined) => {
  if (!date) return undefined
  const parts = date.split('/')
  return parts.length === 3 ? `${parts[2]}-${parts[1]}-${parts[0]}` : date
}

// Define as propriedades (props) que o componente de modal espera receber do componente pai.
interface EditInventoryModalProps {
  isOpen: boolean
  onClose: () => void
  onConfirmEdit: (data: EditFormData) => void
  inventory: InventoryResponse | null
}

// Componente de modal específico para a edição de um inventário existente.
export const EditInventoryModal = ({
  isOpen,
  onClose,
  onConfirmEdit,
  inventory,
}: EditInventoryModalProps) => {
  const creationDate = toDateInputValue(inventory?.createdAt)
  const [sectors, setSectors] = useState<Sector[]>([])
  const [sectorError, setSectorError] = useState<string | null>(null)
  const [sectorLoading, setSectorLoading] = useState(true)
  const [sectorReload, setSectorReload] = useState(0)
  useEffect(() => {
    if (!isOpen) return
    const controller = new AbortController()
    setSectorLoading(true)
    setSectorError(null)
    listSectors(controller.signal)
      .then((rows) => {
        if (!controller.signal.aborted) setSectors(rows)
      })
      .catch((error) => {
        if (!controller.signal.aborted)
          setSectorError(administrationFailure(error).message)
      })
      .finally(() => {
        if (!controller.signal.aborted) setSectorLoading(false)
      })
    return () => controller.abort()
  }, [isOpen, sectorReload])
  // Hook 'react-hook-form' para gerenciar o estado, validação e submissão do formulário.
  const {
    register,
    handleSubmit,
    reset,
    control,
    formState: { errors, isSubmitting },
  } = useForm<EditFormData>({
    resolver: zodResolver(editInventorySchema),
  })

  // Efeito que executa quando o modal é aberto ou o inventário a ser editado muda.
  // Sua função é pré-preencher os campos do formulário com os dados atuais do inventário.
  useEffect(() => {
    if (inventory) {
      reset({
        name: inventory.name,
        description: inventory.description,
        plannedEndDate: inventory.plannedEndDate || '',
        sectorId: inventory.sectorId || 0,
      })
    }
  }, [inventory, reset])

  // Se a prop 'isOpen' for falsa, o componente não renderiza nada no DOM.
  if (!isOpen) return null

  return (
    // O contêiner do modal, que inclui o fundo escuro (backdrop) e o painel centralizado.
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && !isSubmitting) onClose()
      }}
    >
      <DialogContent
        showCloseButton={false}
        className="flex max-h-[90vh] flex-col overflow-y-auto rounded-xl gap-0 p-6 sm:max-w-lg sm:p-8"
      >
        <DialogTitle className="sr-only">Editar Inventário</DialogTitle>
        {/* Botão para fechar o modal no canto superior direito. */}
        <UiButton
          variant="ghost"
          size="icon-sm"
          onClick={onClose}
          className="absolute top-4 right-4 text-muted-foreground"
          aria-label="Fechar modal"
        >
          <X />
        </UiButton>

        <h2 className="text-xl sm:text-2xl font-bold text-foreground mb-6">
          Editar Inventário
        </h2>

        {/* O 'handleSubmit' do react-hook-form envolve a função 'onConfirmEdit' do pai,
            garantindo que ela só seja chamada se o formulário for válido. */}
        <form
          onSubmit={handleSubmit(onConfirmEdit)}
          className="flex flex-col gap-4"
        >
          <div>
            <UiLabel
              htmlFor="name"
              className="block text-sm font-medium text-foreground mb-1"
            >
              Nome
            </UiLabel>
            {/* Componente de Input para o nome, conectado ao formulário via 'register'. */}
            <UiInput
              className="w-full"
              id="name"
              type="text"
              placeholder="Nome do inventário"
              {...register('name')}
            />
            {/* Exibe a mensagem de erro para o campo 'name', se houver. */}
            {errors.name && (
              <p className="text-destructive text-sm mt-1">
                {errors.name.message}
              </p>
            )}
          </div>

          <div>
            <UiLabel
              htmlFor="description"
              className="block text-sm font-medium text-foreground mb-1"
            >
              Descrição
            </UiLabel>
            {/* Componente de Input para a descrição. */}
            <UiInput
              className="w-full"
              id="description"
              type="text"
              placeholder="Descrição do inventário"
              {...register('description')}
            />
            {errors.description && (
              <p className="text-destructive text-sm mt-1">
                {errors.description.message}
              </p>
            )}
          </div>

          <div>
            <UiLabel
              htmlFor="edit-sectorId"
              className="block text-sm font-medium text-foreground mb-1"
            >
              Setor responsável
            </UiLabel>
            <Controller
              control={control}
              name="sectorId"
              render={({ field }) => (
                <SelectField
                  id="edit-sectorId"
                  aria-label="Setor responsável"
                  options={sectors
                    .filter(
                      (sector) =>
                        sector.active || sector.id === inventory?.sectorId,
                    )
                    .map((sector) => ({
                      value: String(sector.id),
                      label: `${sector.name}${sector.active ? '' : ' (inativo)'}`,
                    }))}
                  value={field.value ? String(field.value) : ''}
                  onValueChange={(value) => field.onChange(Number(value))}
                  placeholder={
                    sectorLoading ? 'Carregando setores…' : 'Selecione o setor'
                  }
                  disabled={sectorLoading || isSubmitting || !!sectorError}
                />
              )}
            />
            {errors.sectorId && (
              <p role="alert" className="text-destructive text-sm mt-1">
                {errors.sectorId.message}
              </p>
            )}
            {sectorError && (
              <div role="alert" className="text-destructive text-sm mt-1">
                <p>{sectorError}</p>
                <UiButton
                  variant="link"
                  type="button"
                  className="h-auto p-0 text-destructive"
                  onClick={() => setSectorReload((value) => value + 1)}
                >
                  Tentar carregar setores novamente
                </UiButton>
              </div>
            )}
          </div>

          <div>
            <UiLabel
              htmlFor="plannedEndDate"
              className="block text-sm font-medium text-foreground mb-1"
            >
              Data prevista de finalização
            </UiLabel>
            <Controller
              control={control}
              name="plannedEndDate"
              render={({ field }) => (
                <DatePicker
                  id="plannedEndDate"
                  aria-label="Data prevista de finalização"
                  value={field.value}
                  onChange={field.onChange}
                  min={creationDate}
                />
              )}
            />
            {errors.plannedEndDate && (
              <p className="text-destructive text-sm mt-1">
                {errors.plannedEndDate.message}
              </p>
            )}
          </div>

          {/* Seção com os botões de ação do formulário. */}
          <div className="mt-6 flex flex-col-reverse sm:flex-row justify-end gap-3 sm:gap-4">
            <UiButton
              variant={'outline'}
              className="w-full sm:w-auto"
              type="button"
              onClick={onClose}
            >
              Cancelar
            </UiButton>
            <UiButton
              variant="default"
              className="w-full sm:w-auto"
              type="submit"
              disabled={isSubmitting || sectorLoading || !!sectorError}
            >
              {isSubmitting ? 'Salvando...' : 'Salvar Alterações'}
            </UiButton>
          </div>
        </form>
      </DialogContent>
    </Dialog>
  )
}
