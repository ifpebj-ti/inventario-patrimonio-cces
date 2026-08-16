/* eslint-disable react/display-name */
import { forwardRef } from 'react'
import { ButtonProps, variantClass } from './types'

// Componente de botão genérico e reutilizável, construído com 'forwardRef' para
// permitir que a 'ref' seja passada de um componente pai para o elemento <button> nativo.
export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  (
    {
      type, // O tipo do botão (ex: 'button', 'submit').
      text, // O texto a ser exibido no botão.
      image, // Um elemento de imagem opcional.
      icon, // Um elemento de ícone opcional.
      width = 'w-1/2', // A largura padrão do contêiner do botão.
      variant = 1, // A variante de estilo a ser aplicada, com 1 como padrão.
      tooltip, // O texto opcional para a dica de ferramenta (tooltip).
      ...props // Pega todas as outras props de um botão HTML (como onClick, disabled, etc.).
    },
    ref,
  ) => {
    return (
      // Este 'div' serve como um contêiner para o botão e sua tooltip.
      // 'relative' é necessário para posicionar a tooltip de forma absoluta em relação a ele.
      // 'group' é uma classe do Tailwind que permite estilizar um elemento filho
      // quando o mouse passa por cima deste contêiner pai.
      <div className={`relative group ${width}`}>
        {/* O elemento de botão real. */}
        <button
          type={type}
          // As classes de estilo são aplicadas dinamicamente com base na prop 'variant'.
          className={variantClass[variant]}
          // A 'ref' é repassada para o elemento de botão, permitindo o acesso direto a ele.
          ref={ref}
          // Espalha todas as outras props recebidas (ex: 'onClick', 'disabled').
          {...props}
        >
          {/* O conteúdo do botão é renderizado aqui, permitindo ícones, imagens e texto. */}
          {image}
          {icon}
          {text}
        </button>

        {/* A tooltip só é renderizada se a prop 'tooltip' for fornecida. */}
        {tooltip && (
          // Este 'div' é a dica de ferramenta (tooltip).
          // As classes de posicionamento ('absolute', 'bottom-full', etc.) a colocam acima do botão.
          // As classes de opacidade e transição ('opacity-0', 'group-hover:opacity-100')
          // fazem com que ela apareça suavemente quando o mouse passa sobre o 'group' pai.
          // 'pointer-events-none' garante que a tooltip não interfira com os cliques do mouse.
          <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 w-max bg-gray-800 text-white text-xs rounded px-2 py-1 opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none z-100">
            {tooltip}
          </div>
        )}
      </div>
    )
  },
)
