import { useState } from 'react'
import { Link } from 'react-router'
import { activateAccount } from '@/api/auth'
import { Alert } from '@/components/ui/Alert'
import { AuthLayout } from './AuthLayout'
import { SetPasswordForm } from './SetPasswordForm'
import { useTokenFromUrl } from './useTokenFromUrl'

export function ActivateAccountPage() {
  const token = useTokenFromUrl()
  const [done, setDone] = useState(false)

  if (!token) {
    return (
      <AuthLayout title="Activate your account">
        <Alert tone="error" title="Activation link is incomplete">
          Open the link from your activation email again, or ask the registrar to resend it.
        </Alert>
      </AuthLayout>
    )
  }

  if (done) {
    return (
      <AuthLayout title="Account activated">
        <Alert tone="success" title="Your password has been set.">
          You can now sign in with your university email address.
        </Alert>
        <Link to="/login" className="mt-6 inline-block text-sm font-medium text-brand-700 hover:text-brand-900">
          Continue to sign in →
        </Link>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout title="Activate your account" subtitle="Choose a password to finish setting up your UniSphere account.">
      <SetPasswordForm
        submitLabel="Activate account"
        onSubmit={async (password) => {
          await activateAccount(token, password)
          setDone(true)
        }}
      />
    </AuthLayout>
  )
}
