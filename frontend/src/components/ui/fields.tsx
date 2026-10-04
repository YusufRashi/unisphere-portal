import { useId, type ComponentProps, type ReactNode } from 'react'
import { cn } from '@/lib/cn'

const controlBase =
  'block w-full rounded-md border bg-white px-3 text-sm text-slate-900 shadow-xs placeholder:text-slate-400 ' +
  'focus:border-brand-600 focus:ring-2 focus:ring-brand-600/20 focus:outline-none disabled:bg-slate-50 disabled:text-slate-500'

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
      <label htmlFor={id} className={cn('block text-sm font-medium text-slate-700', hideLabel && 'sr-only')}>
        {label}
      </label>
      {children}
      {hint && !error && (
        <div id={`${id}-hint`} className="text-xs text-slate-500">
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
        className={cn(controlBase, 'h-9', error ? 'border-red-500' : 'border-slate-300', className)}
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
        className={cn(controlBase, 'h-9 pr-8', error ? 'border-red-500' : 'border-slate-300', className)}
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
        className={cn(controlBase, 'py-2', error ? 'border-red-500' : 'border-slate-300', className)}
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
        className="mt-0.5 size-4 rounded border-slate-300 text-brand-700 focus:ring-brand-600"
        {...props}
      />
      <div className="text-sm">
        <label htmlFor={fieldId} className="font-medium text-slate-700">
          {label}
        </label>
        {description && (
          <p id={`${fieldId}-description`} className="text-slate-500">
            {description}
          </p>
        )}
      </div>
    </div>
  )
}
