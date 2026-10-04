import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router'
import { z } from 'zod'
import { requestPasswordReset } from '@/api/auth'
import { toApiError } from '@/api/errors'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { TextField } from '@/components/ui/fields'
import { AuthLayout } from './AuthLayout'

const schema = z.object({
  email: z.string().trim().min(1, 'Enter your email address.').pipe(z.email('Enter a valid email address.')),
})
type FormValues = z.infer<typeof schema>

export function ForgotPasswordPage() {
  const [sentTo, setSentTo] = useState<string | null>(null)
  const [serverError, setServerError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: { email: '' } })

  async function onSubmit({ email }: FormValues) {
    setServerError(null)
    try {
      await requestPasswordReset(email)
      setSentTo(email)
    } catch (error) {
      setServerError(toApiError(error).message)
    }
  }

  return (
    <AuthLayout title="Reset your password" subtitle="We'll email you a link to choose a new password.">
      {sentTo ? (
        // Same message whether or not the account exists, so this page cannot be used to discover accounts.
        <Alert tone="success" title="Check your inbox">
          If an active account exists for <strong>{sentTo}</strong>, a reset link is on its way. The link expires in 30
          minutes.
        </Alert>
      ) : (
        <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-5">
          {serverError && <Alert tone="error">{serverError}</Alert>}
          <TextField label="Email address" type="email" autoComplete="email" autoFocus error={errors.email?.message} {...register('email')} />
          <Button type="submit" className="w-full" loading={isSubmitting}>
            Send reset link
          </Button>
        </form>
      )}
      <Link to="/login" className="mt-6 inline-block text-sm font-medium text-brand-700 hover:text-brand-900">
        ← Back to sign in
      </Link>
    </AuthLayout>
  )
}
