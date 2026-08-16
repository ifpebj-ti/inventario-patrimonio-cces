'use client'

import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { Input } from '@/components/atoms/input'
import { Button } from '@/components/atoms/button'
import { useEffect } from 'react'
import { InventoryResponse } from '@/services/inventory'

// Schema de validação do formulário de edição, utilizando a biblioteca Zod.
const editInventorySchema = z.object({
  name: z.string().min(3, 'O nome deve ter pelo menos 3 caracteres.'),
  description: z.string().optional(),
})

// Extrai o tipo TypeScript a partir do schema Zod para garantir a segurança de tipos.
type EditFormData = z.infer<typeof editInventorySchema>

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
  // Hook 'react-hook-form' para gerenciar o estado, validação e submissão do formulário.
  const {
    register,
    handleSubmit,
    reset,
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
      })
    }
  }, [inventory, reset])

  // Se a prop 'isOpen' for falsa, o componente não renderiza nada no DOM.
  if (!isOpen) return null

  return (
    // O contêiner do modal, que inclui o fundo escuro (backdrop) e o painel centralizado.
    <div className="fixed inset-0 bg-opacity-30 backdrop-blur-sm flex justify-center items-center z-50 p-4">
      {/* O painel branco do modal com a estilização principal. */}
      <div className="bg-white p-8 rounded-2xl shadow-xl w-full max-w-lg relative">
        {/* Botão para fechar o modal no canto superior direito. */}
        <button
          onClick={onClose}
          className="absolute top-4 right-4 text-gray-400 hover:text-gray-600 text-3xl cursor-pointer"
        >
          &times;
        </button>

        <h2 className="text-2xl font-bold text-slate-800 mb-6">
          Editar Inventário
        </h2>

        {/* O 'handleSubmit' do react-hook-form envolve a função 'onConfirmEdit' do pai,
            garantindo que ela só seja chamada se o formulário for válido. */}
        <form
          onSubmit={handleSubmit(onConfirmEdit)}
          className="flex flex-col gap-4"
        >
          <div>
            <label
              htmlFor="name"
              className="block text-sm font-medium text-slate-700 mb-1"
            >
              Nome
            </label>
            {/* Componente de Input para o nome, conectado ao formulário via 'register'. */}
            <Input
              id="name"
              type="text"
              placeholder="Nome do inventário"
              {...register('name')}
              width="w-full"
            />
            {/* Exibe a mensagem de erro para o campo 'name', se houver. */}
            {errors.name && (
              <p className="text-red-500 text-sm mt-1">{errors.name.message}</p>
            )}
          </div>

          <div>
            <label
              htmlFor="description"
              className="block text-sm font-medium text-slate-700 mb-1"
            >
              Descrição
            </label>
            {/* Componente de Input para a descrição. */}
            <Input
              id="description"
              type="text"
              placeholder="Descrição do inventário"
              {...register('description')}
              width="w-full"
            />
            {errors.description && (
              <p className="text-red-500 text-sm mt-1">
                {errors.description.message}
              </p>
            )}
          </div>

          {/* Seção com os botões de ação do formulário. */}
          <div className="mt-6 flex justify-end gap-4">
            <Button
              type="button"
              text="Cancelar"
              variant={2}
              onClick={onClose}
            />
            <Button
              type="submit"
              // O texto do botão e seu estado 'disabled' mudam durante a submissão.
              text={isSubmitting ? 'Salvando...' : 'Salvar Alterações'}
              disabled={isSubmitting}
            />
          </div>
        </form>
      </div>
    </div>
  )
}
