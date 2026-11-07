import { useEffect, type ReactNode } from 'react'

export function PageHeader({ title, description, actions, image, imageClassName = 'object-center' }: {
  title: string
  description?: string
  actions?: ReactNode
  /** A photo to show the heading over, as a large banner. Decorative: the heading says what the page is. */
  image?: string
  /** Where the photo is anchored when cropped, e.g. `object-[center_30%]`. */
  imageClassName?: string
}) {
  useEffect(() => {
    document.title = `${title} · UniSphere`
  }, [title])

  if (image) {
    return (
      <div className="relative isolate mb-6 flex min-h-44 flex-wrap items-end justify-between gap-4 overflow-hidden rounded-card bg-hero p-6 text-white sm:min-h-56 sm:p-8 print:mb-4 print:min-h-0 print:bg-transparent print:p-0 print:text-neutral-900">
        <img src={image} alt="" className={`absolute inset-0 -z-10 size-full object-cover print:hidden ${imageClassName}`} />
        <div aria-hidden="true" className="absolute inset-0 -z-10 bg-linear-to-t from-hero via-hero/70 to-hero/10 sm:bg-linear-to-r print:hidden" />
        <div>
          <h1 className="text-2xl font-semibold tracking-tight sm:text-3xl">{title}</h1>
          {description && <p className="mt-1 text-sm text-white/80 print:text-neutral-500">{description}</p>}
        </div>
        {actions && <div className="flex gap-2">{actions}</div>}
      </div>
    )
  }

  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4 pt-1">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight text-neutral-900 sm:text-[1.75rem]">{title}</h1>
        {description && <p className="mt-1 text-sm text-neutral-500">{description}</p>}
      </div>
      {actions && <div className="flex gap-2">{actions}</div>}
    </div>
  )
}
