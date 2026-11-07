import { zodResolver } from '@hookform/resolvers/zod'
import { Check, Circle } from 'lucide-react'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { z } from 'zod'
import { toApiError } from '@/api/errors'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { TextField } from '@/components/ui/fields'
import { cn } from '@/lib/cn'
import { PASSWORD_RULES, passwordProblems } from '../passwordRules'

const schema = z
  .object({
    password: z.string(),
    confirm: z.string(),
  })
  .superRefine((values, ctx) => {
    const problems = passwordProblems(values.password)
    if (problems.length > 0) {
      ctx.addIssue({ code: 'custom', path: ['password'], message: 'Password does not meet the requirements below.' })
    }
    if (values.confirm !== values.password) {
      ctx.addIssue({ code: 'custom', path: ['confirm'], message: 'Passwords do not match.' })
    }
  })
type FormValues = z.infer<typeof schema>

/** Shared by account activation and password reset. */
export function SetPasswordForm({ submitLabel, onSubmit }: {
  submitLabel: string
  onSubmit: (password: string) => Promise<void>
}) {
  const [serverError, setServerError] = useState<{ message: string; violations: string[] } | null>(null)
  const {
    register,
    handleSubmit,
    control,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: { password: '', confirm: '' } })
  const password = useWatch({ control, name: 'password' })

  async function submit(values: FormValues) {
    setServerError(null)
    try {
      await onSubmit(values.password)
    } catch (error) {
      const apiError = toApiError(error)
      setServerError({ message: apiError.message, violations: apiError.violations })
    }
  }

  return (
    <form onSubmit={handleSubmit(submit)} noValidate className="space-y-5">
      {serverError && (
        <Alert tone="error">
          {serverError.violations.length > 0 ? (
            <ul className="list-disc pl-4">
              {serverError.violations.map((v) => (
                <li key={v}>{v}</li>
              ))}
            </ul>
          ) : (
            serverError.message
          )}
        </Alert>
      )}
      <TextField
        label="New password"
        type="password"
        autoComplete="new-password"
        autoFocus
        error={errors.password?.message}
        {...register('password')}
      />
      <ul aria-label="Password requirements" className="space-y-1">
        {PASSWORD_RULES.filter((r) => r.id !== 'email').map((rule) => {
          const ok = rule.test(password ?? '')
          return (
            <li key={rule.id} className={cn('flex items-center gap-2 text-xs', ok ? 'text-emerald-700' : 'text-neutral-500')}>
              {ok ? <Check aria-hidden="true" className="size-3.5" /> : <Circle aria-hidden="true" className="size-3.5" />}
              {rule.label}
              <span className="sr-only">{ok ? '(met)' : '(not met)'}</span>
            </li>
          )
        })}
      </ul>
      <TextField
        label="Confirm new password"
        type="password"
        autoComplete="new-password"
        error={errors.confirm?.message}
        {...register('confirm')}
      />
      <Button type="submit" className="w-full" loading={isSubmitting}>
        {submitLabel}
      </Button>
    </form>
  )
}
