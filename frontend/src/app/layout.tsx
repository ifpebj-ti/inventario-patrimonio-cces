import type { Metadata } from 'next'
import './globals.css'

export const metadata: Metadata = {
  title: 'Inventarium',
  description: 'Seu aplicativo de inventário',
}

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode
}>) {
  return (
    <html lang="pt-br">
      <body className={`antialiased`}>{children}</body>
    </html>
  )
}
