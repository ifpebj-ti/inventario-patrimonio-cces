'use client'

import { useAuth } from '@/hooks/useAuth'
import { useRouter } from 'next/navigation'
import { PiSignOutBold } from 'react-icons/pi'
import { FaArrowLeft } from 'react-icons/fa'

// Componente reutilizável para o cabeçalho principal da aplicação.
export const Header = () => {
  // Obtém a função de logout do contexto de autenticação global.
  const { signOut } = useAuth()
  // Hook do Next.js para controlar a navegação entre as páginas.
  const router = useRouter()

  return (
    // 'sticky' e 'top-0' mantêm o cabeçalho fixo no topo da página durante a rolagem.
    <header className="sticky flex flex-row justify-center items-center shadow p-4 top-0 z-50 bg-white">
      {/* Ícone de seta para voltar para a página anterior no histórico do navegador. */}
      <button
        onClick={() => router.back()}
        aria-label="Voltar para a página anterior"
        title="Voltar para a página anterior"
        className="absolute left-10"
        accessKey="b" // atalho intuitivo para back/Voltar
      >
        <FaArrowLeft className="text-2xl text-blue-400 cursor-pointer sm:text-4xl" />
      </button>
      {/* Título/logo principal da aplicação, que também é um link para o dashboard. */}
      <h1
        className="text-3xl sm:text-6xl text-blue-400 cursor-pointer"
        onClick={() => router.push('/dashboard')}
      >
        Inventarium
      </h1>
      {/* Ícone para executar a função de logout (sair) do sistema. */}
      <button
        onClick={signOut}
        aria-label="Sair da conta"
        title="Sair da conta"
        className="absolute right-10"
      >
        <PiSignOutBold className="text-2xl text-blue-400 cursor-pointer sm:text-4xl" />
      </button>
    </header>
  )
}
