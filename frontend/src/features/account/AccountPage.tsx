import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { changePassword } from '@/api/auth'
import { toApiError } from '@/api/errors'
import { useAuth } from '@/auth/authContext'
import { passwordProblems } from '@/auth/passwordRules'
import { PageHeader } from '@/components/layout/PageHeader'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { TextField } from '@/components/ui/fields'
import { useToast } from '@/components/ui/toastContext'

function buildSchema(email: string) {
  return z
    .object({
      currentPassword: z.string().min(1, 'Enter your current password.'),
      newPassword: z.string(),
      confirm: z.string(),
    })
    .superRefine((v, ctx) => {
      const problems = passwordProblems(v.newPassword, email)
      if (problems.length > 0) ctx.addIssue({ code: 'custom', path: ['newPassword'], message: problems.join(' · ') })
      if (v.newPassword && v.newPassword === v.currentPassword) {
        ctx.addIssue({ code: 'custom', path: ['newPassword'], message: 'Choose a password different from the current one.' })
      }
      if (v.confirm !== v.newPassword) ctx.addIssue({ code: 'custom', path: ['confirm'], message: 'Passwords do not match.' })
    })
}
type FormValues = z.infer<ReturnType<typeof buildSchema>>

export function AccountPage() {
  const { user, setSessionUser } = useAuth()
  const toast = useToast()
  const [serverError, setServerError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(buildSchema(user?.email ?? '')),
    defaultValues: { currentPassword: '', newPassword: '', confirm: '' },
  })

  async function onSubmit(values: FormValues) {
    setServerError(null)
    try {
      const session = await changePassword(values.currentPassword, values.newPassword)
      setSessionUser(session.user)
      reset()
      toast.success('Password changed. Other devices have been signed out.')
    } catch (error) {
      const apiError = toApiError(error)
      if (apiError.code === 'VALIDATION_ERROR' && apiError.message.toLowerCase().includes('current password')) {
        setError('currentPassword', { message: apiError.message })
      } else {
        setServerError(apiError.violations.join(' ') || apiError.message)
      }
    }
  }

  return (
    <>
      <PageHeader title="Account settings" description="Manage your sign-in credentials." />
      <Card className="max-w-xl">
        <CardHeader title="Change password" description="Changing your password signs you out on all other devices." />
        <CardBody>
          <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-5">
            {serverError && <Alert tone="error">{serverError}</Alert>}
            <TextField label="Current password" type="password" autoComplete="current-password" error={errors.currentPassword?.message} {...register('currentPassword')} />
            <TextField
              label="New password"
              type="password"
              autoComplete="new-password"
              hint="At least 10 characters, including a letter and a number."
              error={errors.newPassword?.message}
              {...register('newPassword')}
            />
            <TextField label="Confirm new password" type="password" autoComplete="new-password" error={errors.confirm?.message} {...register('confirm')} />
            <div className="flex justify-end">
              <Button type="submit" loading={isSubmitting}>
                Update password
              </Button>
            </div>
          </form>
        </CardBody>
      </Card>
    </>
  )
}
