'use client'

import { AccessGate } from '@/components/organisms/administration/shared'
import { SectorsScreen } from '@/components/organisms/administration/sectors'

export default function SectorsPage() {
  return (
    <AccessGate>
      <SectorsScreen />
    </AccessGate>
  )
}
