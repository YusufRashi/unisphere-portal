import { Link } from 'react-router'
import { LostMap } from '@/components/illustrations/Illustrations'

export function NotFoundPage() {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center px-4 text-center">
      <LostMap className="mb-6 w-64 max-w-full" />
      <p className="font-mono text-sm font-semibold text-brand-700">404</p>
      <h1 className="mt-2 text-2xl font-semibold text-neutral-900">Page not found</h1>
      <p className="mt-2 text-sm text-neutral-500">The page you are looking for does not exist or has moved.</p>
      <Link to="/dashboard" className="mt-6 text-sm font-medium text-brand-700 hover:text-brand-900">
        Back to UniSphere
      </Link>
    </div>
  )
}
