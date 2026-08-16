/* eslint-disable @typescript-eslint/no-explicit-any */
'use client'

import { forwardRef } from 'react'
import { IMaskInput } from 'react-imask' // Importa o componente de máscara da biblioteca 'react-imask'.
import { InputProps, variantClass } from './types'

// Componente 'Input' genérico e reutilizável.
// Utiliza 'forwardRef' para que a 'ref' possa ser passada de um componente pai
// diretamente para o elemento <input> interno, o que é essencial para bibliotecas como o react-hook-form.
export const Input = forwardRef<HTMLInputElement, InputProps>(
  (
    {
      // Desestruturação das props para uso explícito no componente.
      name,
      type,
      width = 'w-2/3', // 'width' controla a largura do container do input, com um valor padrão.
      placeholder,
      variant = 1, // 'variant' define o estilo visual do input, com 1 como padrão.
      mask, // A prop opcional que define a máscara a ser aplicada.
      className, // Permite que classes CSS customizadas sejam passadas de fora.
      ...props // Captura todas as outras props de um input HTML padrão (ex: disabled, autoComplete, onClick).
    },
    ref,
  ) => {
    // Constrói a string de classes CSS final, unindo a classe da variante com quaisquer classes customizadas.
    // O '.trim()' remove espaços em branco extras no final.
    const finalClassName = `${variantClass[variant]} ${className || ''}`.trim()

    return (
      // Container div que controla a largura do campo de input.
      <div className={width}>
        {/* Renderização condicional: verifica se a prop 'mask' foi fornecida. */}
        {mask ? (
          // Se uma máscara for fornecida, renderiza o componente IMaskInput.
          <IMaskInput
            mask={mask}
            // A 'ref' é repassada para o input interno do IMaskInput.
            // O 'as any' é usado aqui para contornar checagens de tipo complexas entre as bibliotecas.
            ref={ref as any}
            name={name}
            type={type}
            placeholder={placeholder}
            className={finalClassName}
            // Espalha as props restantes. Isso é crucial para passar 'onChange', 'onBlur', etc., do react-hook-form.
            {...(props as any)}
          />
        ) : (
          // Se nenhuma máscara for fornecida, renderiza um elemento <input> HTML padrão.
          <input
            name={name}
            type={type}
            placeholder={placeholder}
            ref={ref}
            className={finalClassName}
            {...props}
          />
        )}
      </div>
    )
  },
)

// Define um nome de exibição para o componente, o que ajuda na depuração com as ferramentas de desenvolvedor do React.
Input.displayName = 'Input'

// Exporta o componente para ser usado em outras partes da aplicação.
export default Input
