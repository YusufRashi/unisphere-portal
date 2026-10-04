import { X } from 'lucide-react'
import { useEffect, useId, useRef, type ReactNode, type RefObject } from 'react'
import { createPortal } from 'react-dom'
import { cn } from '@/lib/cn'

export interface ModalProps {
  open: boolean
  title: string
  description?: ReactNode
  onClose: () => void
  children?: ReactNode
  footer?: ReactNode
  size?: 'sm' | 'md' | 'lg'
  /** alertdialog for confirmations; dialog for forms. */
  role?: 'dialog' | 'alertdialog'
  /** Element to focus when opened; defaults to the first focusable element. */
  initialFocusRef?: RefObject<HTMLElement | null>
  /** While true, Escape and backdrop clicks do nothing (e.g. a save is in progress). */
  busy?: boolean
  icon?: ReactNode
}

const FOCUSABLE = 'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
const sizes = { sm: 'max-w-md', md: 'max-w-xl', lg: 'max-w-3xl' }

/**
 * Accessible modal: labelled by its title, focus moves inside on open and is trapped there, Escape closes,
 * and focus returns to the element that opened it.
 */
export function Modal({
  open,
  title,
  description,
  onClose,
  children,
  footer,
  size = 'md',
  role = 'dialog',
  initialFocusRef,
  busy = false,
  icon,
}: ModalProps) {
  const titleId = useId()
  const descriptionId = useId()
  const panelRef = useRef<HTMLDivElement>(null)
  // Keep the latest callbacks without re-running the focus effect on every render.
  const onCloseRef = useRef(onClose)
  const busyRef = useRef(busy)
  useEffect(() => {
    onCloseRef.current = onClose
    busyRef.current = busy
  })

  useEffect(() => {
    if (!open) return
    const previouslyFocused = document.activeElement as HTMLElement | null
    const target = initialFocusRef?.current ?? panelRef.current?.querySelector<HTMLElement>(FOCUSABLE)
    target?.focus()

    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape' && !busyRef.current) {
        event.preventDefault()
        onCloseRef.current()
      }
      if (event.key === 'Tab' && panelRef.current) {
        const focusable = Array.from(panelRef.current.querySelectorAll<HTMLElement>(FOCUSABLE))
        const first = focusable[0]
        const last = focusable[focusable.length - 1]
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault()
          last?.focus()
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault()
          first?.focus()
        }
      }
    }
    document.addEventListener('keydown', onKeyDown)
    const { overflow } = document.body.style
    document.body.style.overflow = 'hidden'
    // The modal is portalled to <body>; making the app root inert hides the page behind it from
    // keyboard focus and assistive technology, not just visually.
    const appRoot = document.getElementById('root')
    const wasInert = appRoot?.inert ?? false
    if (appRoot) appRoot.inert = true
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      document.body.style.overflow = overflow
      if (appRoot) appRoot.inert = wasInert
      previouslyFocused?.focus()
    }
  }, [open, initialFocusRef])

  if (!open) return null

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-end justify-center p-0 sm:items-center sm:p-4">
      <div className="absolute inset-0 bg-slate-900/40" aria-hidden="true" onClick={busy ? undefined : onClose} />
      <div
        ref={panelRef}
        role={role}
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={description ? descriptionId : undefined}
        className={cn(
          'relative flex max-h-[92dvh] w-full flex-col rounded-t-lg bg-white shadow-xl sm:rounded-lg',
          sizes[size],
        )}
      >
        <div className="flex items-start gap-4 px-5 pt-5">
          {icon}
          <div className="min-w-0 flex-1">
            <h2 id={titleId} className="text-base font-semibold text-slate-900">
              {title}
            </h2>
            {description && (
              <div id={descriptionId} className="mt-1.5 text-sm text-slate-600">
                {description}
              </div>
            )}
          </div>
          {role === 'dialog' && (
            <button
              type="button"
              onClick={onClose}
              disabled={busy}
              className="-mt-1 -mr-1 rounded-md p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600"
              aria-label="Close"
            >
              <X aria-hidden="true" className="size-4" />
            </button>
          )}
        </div>
        {children && <div className="overflow-y-auto px-5 pt-4 pb-1">{children}</div>}
        {footer && <div className="flex justify-end gap-2 px-5 py-4">{footer}</div>}
      </div>
    </div>,
    document.body,
  )
}
