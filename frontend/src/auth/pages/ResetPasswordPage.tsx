import { useState } from 'react'
import { Link } from 'react-router'
import { resetPassword } from '@/api/auth'
import { Alert } from '@/components/ui/Alert'
import { AuthLayout } from './AuthLayout'
import { SetPasswordForm } from './SetPasswordForm'
import { useTokenFromUrl } from './useTokenFromUrl'

export function ResetPasswordPage() {
  const token = useTokenFromUrl()
  const [done, setDone] = useState(false)

  if (!token) {
    return (
      <AuthLayout title="Reset password">
        <Alert tone="error" title="Reset link is incomplete">
          Open the link from your email again, or <Link to="/forgot-password" className="underline">request a new one</Link>.
        </Alert>
      </AuthLayout>
    )
  }

  if (done) {
    return (
      <AuthLayout title="Password updated">
        <Alert tone="success" title="Your password has been changed.">
          For your security, you have been signed out on all devices.
        </Alert>
        <Link to="/login" className="mt-6 inline-block text-sm font-medium text-brand-700 hover:text-brand-900">
          Continue to sign in →
        </Link>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout title="Choose a new password">
      <SetPasswordForm
        submitLabel="Update password"
        onSubmit={async (password) => {
          await resetPassword(token, password)
          setDone(true)
        }}
      />
    </AuthLayout>
  )
}
