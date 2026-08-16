'use client'

import { SignUpForm } from '@/components/organisms/signUpForm'

export default function SignUpPage() {
  return (
    <div className="flex flex-row h-screen items-center overflow-hidden">
      {/* Formulário na esquerda */}
      <div className="w-full xl:w-1/2 bg-gray-100 h-screen flex justify-center items-center">
        <SignUpForm />
      </div>

      {/* Texto na direita */}
      <div className="hidden xl:flex flex-col justify-center items-center w-1/2 min-w-0 h-screen text-center">
        <h1 className="text-blue-400 text-8xl">Inventarium</h1>
        <p className="text-xl max-w-2xl mt-4">
          Gerencie o inventário da sua empresa, departamento ou setor com o
          melhor sistema
        </p>
      </div>
    </div>
  )
}
