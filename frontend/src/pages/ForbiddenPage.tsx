import { ShieldAlert } from 'lucide-react'
import { Link } from 'react-router'

export function ForbiddenPage() {
  return (
    <div className="flex flex-col items-center py-20 text-center">
      <div className="mb-4 rounded-full bg-amber-50 p-3 text-amber-700">
        <ShieldAlert aria-hidden="true" className="size-6" />
      </div>
      <h1 className="text-xl font-semibold text-slate-900">You don’t have access to this page</h1>
      <p className="mt-2 max-w-md text-sm text-slate-500">
        This area is restricted to a different role. If you believe this is a mistake, contact the registrar’s office.
      </p>
      <Link to="/dashboard" className="mt-6 text-sm font-medium text-brand-700 hover:text-brand-900">
        Go to dashboard
      </Link>
    </div>
  )
}
