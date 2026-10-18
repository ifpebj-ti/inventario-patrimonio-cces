'use client'

import { AccessGate } from '@/components/organisms/administration/shared'
import { UsersScreen } from '@/components/organisms/administration/users'

export default function UsersPage() {
  return (
    <AccessGate users>
      <UsersScreen />
    </AccessGate>
  )
}
