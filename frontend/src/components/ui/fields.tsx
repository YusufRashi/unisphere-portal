import { useId, type ComponentProps, type ReactNode } from 'react'
import { cn } from '@/lib/cn'

const controlBase =
  'block w-full rounded-lg border bg-surface px-3 text-sm text-neutral-900 shadow-xs placeholder:text-neutral-400 ' +
  'focus:border-brand-500 focus:ring-3 focus:ring-brand-500/20 focus:outline-none disabled:bg-neutral-50 disabled:text-neutral-500'

interface FieldShellProps {
  id: string
  label: string
  error?: string
  hint?: ReactNode
  children: ReactNode
  hideLabel?: boolean
}

function FieldShell({ id, label, error, hint, children, hideLabel }: FieldShellProps) {
  return (
    <div className="space-y-1.5">
      <label htmlFor={id} className={cn('block text-sm font-medium text-neutral-700', hideLabel && 'sr-only')}>
        {label}
      </label>
      {children}
      {hint && !error && (
        <div id={`${id}-hint`} className="text-xs text-neutral-500">
          {hint}
        </div>
      )}
      {error && (
        <p id={`${id}-error`} className="text-xs font-medium text-red-700">
          {error}
        </p>
      )}
    </div>
  )
}

export interface TextFieldProps extends ComponentProps<'input'> {
  label: string
  error?: string
  hint?: ReactNode
  hideLabel?: boolean
}

export function TextField({ label, error, hint, hideLabel, id, className, ...props }: TextFieldProps) {
  const generated = useId()
  const fieldId = id ?? generated
  const describedBy = error ? `${fieldId}-error` : hint ? `${fieldId}-hint` : undefined
  return (
    <FieldShell id={fieldId} label={label} error={error} hint={hint} hideLabel={hideLabel}>
      <input
        id={fieldId}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className={cn(controlBase, 'h-10', error ? 'border-red-500' : 'border-neutral-200', className)}
        {...props}
      />
    </FieldShell>
  )
}

export interface SelectFieldProps extends ComponentProps<'select'> {
  label: string
  error?: string
  hideLabel?: boolean
}

export function SelectField({ label, error, hideLabel, id, className, children, ...props }: SelectFieldProps) {
  const generated = useId()
  const fieldId = id ?? generated
  return (
    <FieldShell id={fieldId} label={label} error={error} hideLabel={hideLabel}>
      <select
        id={fieldId}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${fieldId}-error` : undefined}
        className={cn(controlBase, 'h-10 pr-8', error ? 'border-red-500' : 'border-neutral-200', className)}
        {...props}
      >
        {children}
      </select>
    </FieldShell>
  )
}

export interface TextAreaFieldProps extends ComponentProps<'textarea'> {
  label: string
  error?: string
  hint?: ReactNode
}

export function TextAreaField({ label, error, hint, id, className, rows = 4, ...props }: TextAreaFieldProps) {
  const generated = useId()
  const fieldId = id ?? generated
  const describedBy = error ? `${fieldId}-error` : hint ? `${fieldId}-hint` : undefined
  return (
    <FieldShell id={fieldId} label={label} error={error} hint={hint}>
      <textarea
        id={fieldId}
        rows={rows}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className={cn(controlBase, 'py-2', error ? 'border-red-500' : 'border-neutral-200', className)}
        {...props}
      />
    </FieldShell>
  )
}

export interface CheckboxFieldProps extends Omit<ComponentProps<'input'>, 'type'> {
  label: string
  description?: string
}

export function CheckboxField({ label, description, id, ...props }: CheckboxFieldProps) {
  const generated = useId()
  const fieldId = id ?? generated
  return (
    <div className="flex items-start gap-3">
      <input
        id={fieldId}
        type="checkbox"
        aria-describedby={description ? `${fieldId}-description` : undefined}
        className="mt-0.5 size-4 rounded border-neutral-300 text-brand-700 focus:ring-brand-600"
        {...props}
      />
      <div className="text-sm">
        <label htmlFor={fieldId} className="font-medium text-neutral-700">
          {label}
        </label>
        {description && (
          <p id={`${fieldId}-description`} className="text-neutral-500">
            {description}
          </p>
        )}
      </div>
    </div>
  )
}
