/* eslint-disable @typescript-eslint/no-explicit-any */
import { InputField, Modal } from '@/components/organisms/modal'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { toast } from 'react-hot-toast'
import inventory from '@/validations/inventory'
import {
  CreateInventoryRequest,
  createInventoryRequest,
} from '@/services/inventory'
import React from 'react'

// Define as propriedades que o componente NewInventoryModal espera receber.
interface ModalProps {
  onClose: () => void
  onInventoryCreated: () => void
}

// Array de configuração que descreve os campos do formulário a serem renderizados.
// Isso permite que o formulário seja gerado dinamicamente.
const inventoryFields: InputField[] = [
  {
    name: 'name',
    placeholder: 'Nome do inventário...',
    type: 'text',
    required: true,
    autoComplete: 'name',
  },
  {
    name: 'description',
    placeholder: 'Descrição...',
    type: 'text',
    required: false,
    autoComplete: 'description',
  },
]

// Associa o schema de validação Zod para ser usado com o react-hook-form.
const newInventoryFormSchema = inventory

// Componente de modal específico para a criação de um novo inventário.
export const NewInventoryModal = ({
  onClose,
  onInventoryCreated,
}: ModalProps) => {
  // Função que permite fechar o modal ao clicar no fundo escuro (backdrop).
  const handleBackdropClick = (e: React.MouseEvent) => {
    if (e.target === e.currentTarget) {
      onClose()
    }
  }

  // Hook 'react-hook-form' para gerenciar o estado, validação e submissão do formulário.
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<CreateInventoryRequest>({
    mode: 'all',
    criteriaMode: 'all',
    resolver: zodResolver(newInventoryFormSchema),
  })

  // Função executada quando o formulário é submetido com dados válidos.
  const createInventory = async (data: CreateInventoryRequest) => {
    try {
      console.log('Dados do formulário:', data)
      // Chama a função de serviço para enviar os dados para a API.
      await createInventoryRequest(data)
      // Exibe uma notificação de sucesso.
      toast.success('Inventário criado com sucesso!')
      // Chama as funções de callback do componente pai para atualizar a UI e fechar o modal.
      onInventoryCreated()
      onClose()
    } catch (error) {
      // Desativa a regra do linter para permitir o tipo 'any' no erro.
      const axiosError = error as any
      // Extrai a mensagem de erro da resposta da API ou usa uma mensagem padrão.
      const errorMessage =
        axiosError?.response?.data?.message || 'Erro ao criar inventário.'
      // Exibe uma notificação de erro.
      toast.error(errorMessage)
    }
  }

  return (
    <>
      {/* Backdrop - fundo escuro e com blur, que fecha o modal ao ser clicado. */}
      <div
        className="fixed inset-0 bg-opacity-20 backdrop-blur-sm z-60"
        onClick={handleBackdropClick}
      ></div>

      {/* Contêiner que centraliza o modal na tela. */}
      <div className="fixed inset-0 flex justify-center items-center z-60 p-4">
        {/* O painel branco do modal com a estilização principal. */}
        <div className="bg-white rounded-3xl shadow-2xl flex justify-center flex-col gap-6 p-8 relative max-h-screen overflow-y-auto w-2/5 max-w-md">
          {/* Botão para fechar o modal no canto superior direito. */}
          <button
            className="absolute top-4 right-4 text-gray-500 hover:text-gray-700 text-2xl font-bold transition-colors"
            onClick={onClose}
          >
            ×
          </button>

          {/* Componente de Modal genérico que renderiza a estrutura do formulário. */}
          {/* Ele recebe a lógica e os campos como props, tornando-o reutilizável. */}
          <Modal
            onClose={onClose}
            title="Novo Inventário"
            buttonText="Criar Inventário"
            fields={inventoryFields}
            // O 'handleSubmit' do react-hook-form envolve a função 'createInventory'.
            onSubmit={handleSubmit(createInventory)}
            // As funções e estados do formulário são passados como props para o modal genérico.
            register={register}
            errors={errors}
            isSubmitting={isSubmitting}
          />
        </div>
      </div>
    </>
  )
}
