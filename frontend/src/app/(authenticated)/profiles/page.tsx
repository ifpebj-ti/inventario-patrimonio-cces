'use client'

import { AccessGate } from '@/components/organisms/administration/shared'
import { ProfilesScreen } from '@/components/organisms/administration/profiles'

export default function ProfilesPage() {
  return (
    <AccessGate admin>
      <ProfilesScreen />
    </AccessGate>
  )
}
