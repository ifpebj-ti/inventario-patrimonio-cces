'use client'

import { SelectField } from '@/components/ui/select-field'
import { Controller, useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { toast } from 'sonner'
import { useEffect, useState } from 'react'
import inventory from '@/validations/inventory'
import {
  CreateInventoryRequest,
  createInventoryRequest,
} from '@/services/inventory'
import { listSectors } from '@/services/administration'
import { useAdministrationAccess } from '@/contexts/AdministrationAccess'
import type { Sector } from '@/commons/models/administration'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { DatePicker } from '@/components/ui/date-picker'
import { Label } from '@/components/ui/label'

// Data mínima aceita para a data prevista (hoje).
const currentDate = new Date().toISOString().slice(0, 10)

// Define as propriedades que o componente NewInventoryModal espera receber.
interface ModalProps {
  onClose: () => void
  onInventoryCreated: () => void
}

// Componente de modal específico para a criação de um novo inventário.
export const NewInventoryModal = ({
  onClose,
  onInventoryCreated,
}: ModalProps) => {
  const [sectors, setSectors] = useState<Sector[]>([])
  const { canManageInventoryIn } = useAdministrationAccess()
  // Hook 'react-hook-form' para gerenciar o estado, validação e submissão do formulário.
  const {
    register,
    handleSubmit,
    control,
    formState: { errors, isSubmitting },
  } = useForm<CreateInventoryRequest>({
    mode: 'all',
    criteriaMode: 'all',
    resolver: zodResolver(inventory),
  })

  useEffect(() => {
    const controller = new AbortController()
    listSectors(controller.signal)
      .then((data) =>
        // Só os setores em que o usuário gerencia inventários (o cargo vale por setor).
        setSectors(
          data.filter(
            (sector) => sector.active && canManageInventoryIn(sector.id),
          ),
        ),
      )
      .catch(() => {
        if (!controller.signal.aborted)
          toast.error('Não foi possível carregar os setores.')
      })
    return () => controller.abort()
  }, [canManageInventoryIn])

  // Função executada quando o formulário é submetido com dados válidos.
  const createInventory = async (data: CreateInventoryRequest) => {
    try {
      await createInventoryRequest(data)
      toast.success('Inventário criado com sucesso!')
      onInventoryCreated()
      onClose()
    } catch (error) {
      // Extrai a mensagem de erro da resposta da API ou usa uma mensagem padrão.
      const axiosError = error as { response?: { data?: { message?: string } } }
      toast.error(
        axiosError?.response?.data?.message || 'Erro ao criar inventário.',
      )
    }
  }

  return (
    <Dialog
      open
      onOpenChange={(open) => {
        if (!open && !isSubmitting) onClose()
      }}
    >
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>Novo Inventário</DialogTitle>
        </DialogHeader>

        <form
          onSubmit={handleSubmit(createInventory)}
          className="flex flex-col gap-4"
        >
          <fieldset disabled={isSubmitting} className="flex flex-col gap-4">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="name">Nome do inventário</Label>
              <Input
                id="name"
                type="text"
                placeholder="Nome do inventário..."
                autoComplete="name"
                required
                {...register('name')}
              />
              {errors.name && (
                <p className="text-sm text-destructive">
                  {errors.name.message}
                </p>
              )}
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="description">Descrição</Label>
              <Input
                id="description"
                type="text"
                placeholder="Descrição..."
                autoComplete="description"
                {...register('description')}
              />
              {errors.description && (
                <p className="text-sm text-destructive">
                  {errors.description.message}
                </p>
              )}
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="sectorId">Setor responsável</Label>
              <Controller
                control={control}
                name="sectorId"
                render={({ field }) => (
                  <SelectField
                    id="sectorId"
                    aria-label="Setor responsável"
                    options={sectors.map((sector) => ({
                      value: String(sector.id),
                      label: sector.name,
                    }))}
                    value={field.value ? String(field.value) : ''}
                    onValueChange={(value) => field.onChange(Number(value))}
                    placeholder="Selecione o setor"
                  />
                )}
              />
              {errors.sectorId && (
                <p className="text-sm text-destructive">
                  {errors.sectorId.message}
                </p>
              )}
            </div>

            <div className="flex flex-col gap-1.5">
              <Label htmlFor="plannedEndDate">
                Data prevista de finalização
              </Label>
              <Controller
                control={control}
                name="plannedEndDate"
                render={({ field }) => (
                  <DatePicker
                    id="plannedEndDate"
                    aria-label="Data prevista de finalização"
                    value={field.value}
                    onChange={field.onChange}
                    min={currentDate}
                  />
                )}
              />
              {errors.plannedEndDate && (
                <p className="text-sm text-destructive">
                  {errors.plannedEndDate.message}
                </p>
              )}
            </div>
          </fieldset>

          <DialogFooter>
            <Button
              type="submit"
              disabled={isSubmitting}
              className="w-full sm:w-auto"
            >
              {isSubmitting ? 'Processando...' : 'Criar Inventário'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
