'use client'

import { JSX } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { usePathname } from 'next/navigation'

// Objeto que define os diferentes estados (variantes) da animação.
const variants = {
  // 'hidden': Estado inicial, antes de a página entrar na tela.
  hidden: { opacity: 0, x: 300, y: 0 },
  // 'enter': Estado final, quando a página está visível.
  enter: { opacity: 1, x: 0, y: 0 },
  // 'quit': Estado de saída, quando a página está sendo removida.
  quit: { opacity: 0, x: 300, y: 0 },
}

type Props = {
  children: JSX.Element
}

// Componente interno que contém a lógica principal da animação.
function Motion({ children }: Props): JSX.Element {
  // Hook do Next.js que pega o caminho da URL atual (ex: "/dashboard").
  const pathname = usePathname()
  return (
    // AnimatePresence gerencia as animações de entrada e saída dos componentes filhos.
    // 'mode="wait"': Garante que a animação de saída termine antes da de entrada começar.
    // 'initial={false}': Impede a animação de rodar na primeira carga da página.
    <AnimatePresence mode="wait" initial={false}>
      <motion.main
        // A 'key' é crucial. Quando o pathname muda, o AnimatePresence detecta a troca de
        // componente e dispara as animações de saída (do antigo) e entrada (do novo).
        key={pathname}
        // Associa o elemento às variantes de animação definidas acima.
        variants={variants}
        // Define o estado inicial da animação.
        initial="hidden"
        // Define o estado para o qual o componente deve animar ao entrar.
        animate="enter"
        // Define o estado para o qual o componente deve animar ao sair.
        exit="quit"
      >
        {children}
      </motion.main>
    </AnimatePresence>
  )
}

// Componente principal exportado, que envolve a lógica de animação.
export default function MotionWrapper({ children }: Props): JSX.Element {
  return <Motion>{children}</Motion>
}
