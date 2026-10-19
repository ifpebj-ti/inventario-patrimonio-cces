'use client'

import { AccessGate } from '@/components/organisms/administration/shared'
import { RolesScreen } from '@/components/organisms/administration/roles'

export default function RolesPage() {
  return (
    <AccessGate admin>
      <RolesScreen />
    </AccessGate>
  )
}
