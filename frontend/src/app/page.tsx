import { LoginForm } from '@/components/organisms/loginForm'

export default function Home() {
  return (
    <div className="flex flex-row h-screen items-center overflow-hidden">
      <div className="hidden xl:flex flex-col justify-center items-center w-1/2 min-w-0 h-screen">
        <h1 className="text-blue-400 text-8xl">Inventarium</h1>
        <p className="text-xl max-w-2xl text-center">
          Gerencie o inventário da sua empresa, departamento ou setor com o
          melhor sistema
        </p>
      </div>
      <div className="w-full xl:w-1/2 bg-slate-100 h-screen flex justify-center items-center">
        <LoginForm />
      </div>
    </div>
  )
}
