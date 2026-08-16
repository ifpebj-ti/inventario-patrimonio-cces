import { InputHTMLAttributes } from 'react'

// Variantes dos botões
type Variant = 1 | 2

// Propriedades do input
export type InputProps = InputHTMLAttributes<HTMLInputElement> & {
  variant?: Variant
  width?: string
  mask?: string
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  onAccept?: (value: string, maskRef: any) => void
}

// Estilos do input
export const variantClass = {
  1: 'bg-white p-4 border-b-blue-400 border-b-2 w-full hover:border-b-blue-500',
  2: 'bg-gray-100 rounded-2xl m-4 p-3 hover:bg-gray-200',
}
