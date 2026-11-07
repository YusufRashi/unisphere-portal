import { useEffect, type ReactNode } from 'react'
import campusPhoto from '@/assets/photos/campus-archway.webp'
import { Logo } from '@/components/layout/Logo'

/** Signed-out pages: a full-screen campus photo with the welcome text on the left and the form floating on it. */
export function AuthLayout({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  useEffect(() => {
    document.title = `${title} · UniSphere`
  }, [title])

  return (
    <div className="relative isolate grid min-h-dvh bg-hero lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
      {/* Decorative: the text on the page says everything the photo would. */}
      <img src={campusPhoto} alt="" className="fixed inset-0 -z-10 size-full object-cover object-[center_60%]" />
      <div aria-hidden="true" className="fixed inset-0 -z-10 bg-linear-to-r from-hero/95 via-hero/75 to-hero/35" />
      <aside className="hidden p-12 text-white lg:flex lg:flex-col lg:justify-between">
        <Logo onDark />
        <div className="max-w-md">
          <h2 className="text-4xl leading-tight font-semibold tracking-tight">
            Everything about your academic life, in one place.
          </h2>
          <p className="mt-4 text-white/80">
            Register for courses, follow your results and GPA, download transcripts and keep track of fees — with
            dedicated workspaces for lecturers, the registrar and finance.
          </p>
        </div>
        <p className="text-xs text-white/60">UniSphere University Information System</p>
      </aside>
      <main className="flex items-center justify-center px-4 py-12 sm:px-8">
        <div className="w-full max-w-sm rounded-card border border-neutral-200 bg-surface p-6 shadow-2xl sm:p-8">
          <Logo className="mb-8 lg:hidden" />
          <h1 className="text-2xl font-semibold tracking-tight text-neutral-900">{title}</h1>
          {subtitle && <p className="mt-1.5 text-sm text-neutral-500">{subtitle}</p>}
          <div className="mt-8">{children}</div>
        </div>
      </main>
    </div>
  )
}
