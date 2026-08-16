/* eslint-disable @typescript-eslint/no-explicit-any */
import Input from '@/components/atoms/input'
import { Button } from '@/components/atoms/button'
import React from 'react'
import { SendEmailSheetRequest } from '@/services/item'

// Define a estrutura de um objeto de configuração para cada campo do formulário.
export interface InputField {
  name: string
  placeholder: string
  type: 'text' | 'email' | 'password' | 'tel' | 'number' | 'date'
  required?: boolean
  autoComplete?: string
  width?: string
  id?: string
}

// Define as propriedades (props) que o componente Modal pode receber.
// Ele é projetado para ser genérico e receber a lógica do formulário de um componente pai.
export interface ModalProps {
  onClose: () => void
  title?: string
  buttonText?: string
  fields?: InputField[]
  onSubmit?: (e: React.FormEvent) => void
  register?: any
  errors?: any
  isSubmitting?: boolean
  onInventoryCreated?: () => void
  onSendEmail?: (data: SendEmailSheetRequest) => void
}

// Componente de Modal genérico e reutilizável, projetado para renderizar um formulário dinamicamente.
export const Modal = ({
  onClose,
  title,
  buttonText,
  fields,
  register,
  errors,
  onSubmit,
  isSubmitting,
}: ModalProps) => {
  // Função que permite fechar o modal ao clicar no fundo escuro (backdrop).
  const handleBackdropClick = (e: React.MouseEvent) => {
    if (e.target === e.currentTarget) {
      onClose()
    }
  }

  return (
    <>
      {/* Backdrop: o fundo escuro e com blur que cobre a página. */}
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
            className="absolute top-4 right-4 text-gray-500 hover:text-gray-700 text-2xl font-bold transition-colors cursor-pointer"
            onClick={onClose}
          >
            &times;
          </button>

          <h2 className="text-2xl text-blue-400 mt-4">{title}</h2>

          {/* Formulário que será submetido usando a função 'onSubmit' passada via props. */}
          <form
            onSubmit={onSubmit}
            className="w-full max-w-md flex flex-col justify-center items-center gap-6"
          >
            {/* Mapeia o array 'fields' para renderizar cada campo do formulário dinamicamente. */}
            {fields?.map((field, index) => (
              <React.Fragment key={index}>
                <Input
                  // A função 'register' (do react-hook-form) é usada para conectar o input ao estado do formulário.
                  {...register(field.name)}
                  placeholder={field.placeholder}
                  type={field.type}
                  name={field.name}
                  autoComplete={field.autoComplete}
                  required={field.required}
                  id={field.name}
                  width={field.width || 'w-full'}
                />
                {/* Exibe a mensagem de erro para o campo atual, se houver alguma. */}
                {errors[field.name] && (
                  <p className="text-red-500 text-[14px]">
                    {errors[field.name]?.message}
                  </p>
                )}
              </React.Fragment>
            ))}

            {/* Botão de submissão do formulário. */}
            {/* O texto e o estado 'disabled' são controlados pelo 'isSubmitting'. */}
            <Button text={buttonText} type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Processando...' : buttonText}
            </Button>
          </form>
        </div>
      </div>
    </>
  )
}
