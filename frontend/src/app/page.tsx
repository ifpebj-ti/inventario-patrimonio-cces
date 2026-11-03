import Image from 'next/image'
import { ClipboardCheck, FileSpreadsheet, QrCode } from 'lucide-react'
import { LoginForm } from '@/components/organisms/loginForm'
import logoImg from '../../public/logo.png'

const HIGHLIGHTS = [
  {
    icon: QrCode,
    title: 'Escaneie e encontre',
    text: 'Leia o código de barras ou QR do item direto pela câmera.',
  },
  {
    icon: ClipboardCheck,
    title: 'Valide por setor e sala',
    text: 'Confira o que foi encontrado e aponte o que está fora do lugar.',
  },
  {
    icon: FileSpreadsheet,
    title: 'Importe e exporte',
    text: 'Traga seus itens por planilha CSV e leve os resultados em XLSX.',
  },
]

export default function Home() {
  return (
    <div className="flex min-h-screen flex-col lg:flex-row">
      {/* Painel da marca: só aparece em telas largas. */}
      <aside className="relative hidden min-h-screen flex-1 overflow-hidden bg-[linear-gradient(150deg,var(--primary)_0%,color-mix(in_srgb,var(--primary)_62%,#0f172a)_100%)] text-primary-foreground lg:flex lg:flex-col lg:justify-between lg:p-14">
        {/* Textura de pontos e anéis decorativos, só com a cor da marca. */}
        <div
          aria-hidden
          className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_1px_1px,rgba(255,255,255,0.14)_1px,transparent_0)] bg-[length:26px_26px] opacity-60"
        />
        <div
          aria-hidden
          className="pointer-events-none absolute -right-32 -top-32 size-[28rem] rounded-full border border-white/15"
        />
        <div
          aria-hidden
          className="pointer-events-none absolute -right-14 -top-14 size-[16rem] rounded-full border border-white/20"
        />
        <div
          aria-hidden
          className="pointer-events-none absolute -bottom-40 -left-40 size-[30rem] rounded-full bg-white/[0.06]"
        />

        <div className="relative flex items-center gap-3 motion-safe:animate-in motion-safe:fade-in motion-safe:slide-in-from-left-4 motion-safe:duration-700">
          <Image
            src={logoImg}
            alt="Logo Inventarium"
            width={52}
            height={52}
            priority
            className="rounded-2xl bg-white p-1.5 shadow-lg shadow-black/20"
          />
          <span className="brand-font text-4xl">Inventarium</span>
        </div>

        <div className="relative max-w-xl motion-safe:animate-in motion-safe:fade-in motion-safe:slide-in-from-bottom-4 motion-safe:duration-700">
          <h1 className="brand-font text-7xl leading-[1.02] xl:text-8xl">
            Cada item
            <br />
            no seu lugar.
          </h1>
          <p className="mt-6 max-w-md text-lg leading-relaxed text-white/85">
            Gerencie o inventário da sua instituição, departamento ou setor em
            um só lugar, do cadastro à conferência.
          </p>

          <ul className="mt-10 flex flex-col gap-5">
            {HIGHLIGHTS.map(({ icon: Icon, title, text }) => (
              <li key={title} className="flex items-start gap-4">
                <span className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-white/15 ring-1 ring-white/25 backdrop-blur-sm">
                  <Icon className="size-5" aria-hidden />
                </span>
                <span>
                  <span className="block text-base font-semibold">{title}</span>
                  <span className="block text-sm leading-snug text-white/75">
                    {text}
                  </span>
                </span>
              </li>
            ))}
          </ul>
        </div>

        <p className="relative text-xs tracking-wide text-white/60">
          Controle patrimonial simples, rastreável e feito para o dia a dia.
        </p>
      </aside>

      {/* Lado do formulário. No celular vira tela cheia: topo da marca + opções de login. */}
      <main className="flex min-h-dvh flex-1 flex-col bg-card lg:min-h-screen lg:max-w-[46rem] lg:items-center lg:justify-center lg:bg-muted lg:px-12 lg:py-10">
        <header className="relative flex flex-col items-center gap-3 overflow-hidden bg-[linear-gradient(150deg,var(--primary)_0%,color-mix(in_srgb,var(--primary)_62%,#0f172a)_100%)] px-6 pb-20 pt-14 text-primary-foreground lg:hidden">
          <div
            aria-hidden
            className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_1px_1px,rgba(255,255,255,0.14)_1px,transparent_0)] bg-[length:22px_22px] opacity-60"
          />
          {/* Anéis desenhados como fundo (e não como caixas deslocadas para fora), para nada passar da largura da tela. */}
          <div
            aria-hidden
            className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_100%_0%,transparent_5rem,rgba(255,255,255,0.2)_5rem,rgba(255,255,255,0.2)_5.06rem,transparent_5.06rem,transparent_8rem,rgba(255,255,255,0.14)_8rem,rgba(255,255,255,0.14)_8.06rem,transparent_8.06rem)]"
          />
          <Image
            src={logoImg}
            alt="Logo Inventarium"
            width={72}
            height={72}
            priority
            className="relative rounded-2xl bg-white p-1.5 shadow-lg shadow-black/20"
          />
          <span className="brand-font relative text-5xl">Inventarium</span>
          <span className="relative text-sm text-white/80">
            Cada item no seu lugar.
          </span>
        </header>

        <div className="relative -mt-8 flex w-full flex-1 flex-col rounded-t-[2rem] bg-card px-6 pb-10 pt-10 lg:mt-0 lg:flex-none lg:items-center lg:rounded-none lg:bg-transparent lg:p-0">
          <LoginForm />
        </div>
      </main>
    </div>
  )
}
