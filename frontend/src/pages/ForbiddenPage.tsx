import { Link } from 'react-router'
import { LockedDoor } from '@/components/illustrations/Illustrations'

export function ForbiddenPage() {
  return (
    <div className="flex flex-col items-center py-16 text-center">
      <LockedDoor className="mb-6 w-64 max-w-full" />
      <h1 className="text-xl font-semibold text-neutral-900">You don’t have access to this page</h1>
      <p className="mt-2 max-w-md text-sm text-neutral-500">
        This area is restricted to a different role. If you believe this is a mistake, contact the registrar’s office.
      </p>
      <Link to="/dashboard" className="mt-6 text-sm font-medium text-brand-700 hover:text-brand-900">
        Go to dashboard
      </Link>
    </div>
  )
}
