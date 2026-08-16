'use client'

import { InputField, Modal, ModalProps } from '@/components/organisms/modal'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { toast } from 'react-hot-toast'
import sendEmail from '@/validations/sendEmail'
import { SendEmailSheetRequest } from '@/services/item'
import React from 'react'

// Array de configuração que define os campos que o formulário do modal irá renderizar.
const sendEmailFields: InputField[] = [
  {
    name: 'email',
    placeholder: 'Email destinatário...',
    type: 'text',
    required: false,
    autoComplete: 'email',
  },
  {
    name: 'subject',
    placeholder: 'Assunto...',
    type: 'text',
    required: false,
    autoComplete: 'off',
  },
  {
    name: 'message',
    placeholder: 'Informe alguma descrição...',
    type: 'text',
    required: false,
    autoComplete: 'off',
  },
]

// Define o schema de validação e o tipo de dados do formulário a partir do Zod.
const sendEmailSheetFormSchema = sendEmail

// Este componente representa o modal específico para o envio da planilha por e-mail.
// Ele contém a lógica do formulário e usa um componente genérico <Modal> para a renderização.
export const SendEmailSheetModal = ({ onClose, onSendEmail }: ModalProps) => {
  // Função para fechar o modal ao clicar no fundo escuro (backdrop).
  const handleBackdropClick = (e: React.MouseEvent) => {
    if (e.target === e.currentTarget) {
      onClose()
    }
  }

  // Hook 'react-hook-form' para gerenciar o estado e a validação do formulário.
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<SendEmailSheetRequest>({
    mode: 'all',
    criteriaMode: 'all',
    resolver: zodResolver(sendEmailSheetFormSchema),
  })

  // Função chamada quando o formulário é submetido com dados válidos.
  const sendEmailSheet = async (data: SendEmailSheetRequest) => {
    try {
      // Executa a função 'onSendEmail' passada pelo componente pai.
      if (onSendEmail) {
        onSendEmail(data)
      }
      // Fecha o modal após o envio.
      onClose()
    } catch (error) {
      // Em caso de erro, exibe uma notificação.
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const axiosError = error as any
      const errorMessage =
        axiosError?.response?.data?.message || 'Erro ao enviar email.'
      toast.error(errorMessage)
    }
  }

  return (
    <>
      {/* Backdrop (fundo escuro) do modal */}
      <div
        className="fixed inset-0 bg-opacity-20 backdrop-blur-sm z-60"
        onClick={handleBackdropClick}
      ></div>

      {/* Contêiner que centraliza o modal na tela */}
      <div className="fixed inset-0 flex justify-center items-center z-60 p-4">
        {/* Painel branco do modal com a estilização */}
        <div className="bg-white rounded-3xl shadow-2xl flex justify-center flex-col gap-6 p-8 relative max-h-screen overflow-y-auto w-2/5 max-w-md">
          {/* Botão para fechar o modal no canto superior direito */}
          <button
            className="absolute top-4 right-4 text-gray-500 hover:text-gray-700 text-2xl font-bold transition-colors"
            onClick={onClose}
          >
            ×
          </button>

          {/* Componente de Modal genérico que renderiza o formulário dinamicamente */}
          <Modal
            onClose={onClose}
            title="Encaminhar planilha por e-mail"
            buttonText="Encaminhar"
            fields={sendEmailFields}
            // O 'handleSubmit' do react-hook-form envolve nossa função 'sendEmailSheet'.
            onSubmit={handleSubmit(sendEmailSheet)}
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
