'use client'
import { Button } from '@/components/atoms/button'
import { useAuth } from '@/hooks/useAuth'
import { useParams, useRouter } from 'next/navigation'
import { useEffect } from 'react'

export default function VerifyEmail() {
  const { token } = useParams()
  const router = useRouter()
  const { verifyEmail } = useAuth()

  useEffect(() => {
    verifyEmail(token!.toString())
  }, [])
  return (
    <div className="flex flex-col justify-start items-center mt-24 overflow-hidden gap-6">
      <h1 className="text-blue-400 text-9xl">Parabéns!</h1>
      <p className="text-2xl max-w-4xl text-center">
        Você verificou seu email, agora pode aproveitar todas as funcionalidades
        do Inventarium.
      </p>
      <Button
        onClick={() => router.push('/')}
        text="Voltar para tela de login"
        width="w-[20rem]"
      />
    </div>
  )
}
