import React, { ButtonHTMLAttributes } from 'react'

// Variantes aceitas do estilo do botão
type Variant = 1 | 2 | 3 | 4 | 5 | 6 | 7

// Propriedades do botão
export type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  image?: string
  variant?: Variant
  text?: string
  width?: string
  icon?: React.ReactNode
  tooltip?: string
}

// Estilo das variantes do botão
export const variantClass = {
  1: 'bg-blue-400 p-4 min-h-[44px] rounded-4xl text-white font-bold w-full cursor-pointer hover:bg-blue-500 flex items-center justify-center gap-2',
  2: 'bg-red-400 p-4 min-h-[44px] rounded-4xl text-white font-bold w-full cursor-pointer hover:bg-red-500 flex items-center justify-center gap-2',
  3: 'bg-emerald-400 p-4 min-h-[44px] rounded-4xl text-white font-bold w-full cursor-pointer hover:bg-emerald-500 flex items-center justify-center gap-2',
  4: 'w-11 h-11 min-h-[44px] min-w-[44px] bg-gray-400 text-white rounded-md flex items-center justify-center',
  5: 'w-11 h-11 min-h-[44px] min-w-[44px] bg-emerald-400 text-white rounded-md flex items-center justify-center transition-all duration-200 text-[#666] hover:bg-emerald-500 hover:-translate-y-[1px] cursor-pointer',
  6: 'w-11 h-11 min-h-[44px] min-w-[44px] bg-red-400 text-white rounded-md flex items-center justify-center transition-all duration-200 text-[#666] hover:bg-red-500 hover:-translate-y-[1px] cursor-pointer',
  7: 'w-11 h-11 min-h-[44px] min-w-[44px] bg-blue-400 text-white rounded-md flex items-center justify-center transition-all duration-200 text-[#666] hover:bg-blue-500 hover:-translate-y-[1px] cursor-pointer',
}
