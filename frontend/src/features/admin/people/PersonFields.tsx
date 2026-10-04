import type { FieldErrors, UseFormRegister } from 'react-hook-form'
import { TextField } from '@/components/ui/fields'

interface PersonValues {
  firstName: string
  lastName: string
  email: string
  phone: string
}

/** Name, email and phone inputs shared by every account-creation form. */
export function PersonFields<T extends PersonValues>({ register, errors }: {
  register: UseFormRegister<T>
  errors: FieldErrors<T>
}) {
  // The generic constraint guarantees these field names exist on T.
  const reg = register as unknown as UseFormRegister<PersonValues>
  const err = errors as FieldErrors<PersonValues>
  return (
    <>
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField label="First name" autoComplete="off" error={err.firstName?.message} {...reg('firstName')} />
        <TextField label="Last name" autoComplete="off" error={err.lastName?.message} {...reg('lastName')} />
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField
          label="University email"
          type="email"
          autoComplete="off"
          hint="The activation link is sent here."
          error={err.email?.message}
          {...reg('email')}
        />
        <TextField label="Phone (optional)" type="tel" autoComplete="off" error={err.phone?.message} {...reg('phone')} />
      </div>
    </>
  )
}
