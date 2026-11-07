import { useEffect } from 'react'
import libraryPhoto from '@/assets/photos/library-reading-room.webp'
import { useNow } from '@/hooks/useNow'

function greeting(hour: number): string {
  if (hour < 12) return 'Good morning'
  if (hour < 18) return 'Good afternoon'
  return 'Good evening'
}

/** The dashboard's page heading: the greeting and today's date over a photo, darkened so the text stays readable. */
export function WelcomeBanner({ firstName }: { firstName: string }) {
  const now = useNow()
  const title = `${greeting(now.getHours())}, ${firstName}!`
  const today = now.toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long' })

  useEffect(() => {
    document.title = `${title} · UniSphere`
  }, [title])

  return (
    <div className="relative isolate mb-6 flex min-h-44 flex-col justify-end overflow-hidden rounded-card bg-hero p-6 text-white sm:min-h-60 sm:p-8">
      {/* Decorative, so it has no alternative text. */}
      <img src={libraryPhoto} alt="" className="absolute inset-0 -z-10 size-full object-cover object-[center_45%]" />
      <div aria-hidden="true" className="absolute inset-0 -z-10 bg-linear-to-r from-hero via-hero/80 to-hero/25" />
      <p className="font-mono text-xs tracking-wide text-white/70 uppercase">{today}</p>
      <h1 className="mt-1 text-2xl font-semibold tracking-tight sm:text-4xl">{title}</h1>
      <p className="mt-1 text-sm text-white/80">Here is where things stand.</p>
    </div>
  )
}
