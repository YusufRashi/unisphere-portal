import { AlertTriangle } from 'lucide-react'
import { useRef, type ReactNode } from 'react'
import { Button } from './Button'
import { Modal } from './Modal'

export interface ConfirmDialogProps {
  open: boolean
  title: string
  description: ReactNode
  confirmLabel: string
  tone?: 'danger' | 'primary'
  loading?: boolean
  onConfirm: () => void
  onCancel: () => void
}

/** Confirmation for consequential actions. Focus starts on Cancel — the safe choice. */
export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel,
  tone = 'danger',
  loading = false,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const cancelRef = useRef<HTMLButtonElement>(null)
  return (
    <Modal
      open={open}
      role="alertdialog"
      size="sm"
      title={title}
      description={description}
      onClose={onCancel}
      busy={loading}
      initialFocusRef={cancelRef}
      icon={
        tone === 'danger' ? (
          <div className="flex size-9 shrink-0 items-center justify-center rounded-full bg-red-100 text-red-700">
            <AlertTriangle aria-hidden="true" className="size-4" />
          </div>
        ) : undefined
      }
      footer={
        <>
          <Button ref={cancelRef} variant="secondary" onClick={onCancel} disabled={loading}>
            Cancel
          </Button>
          <Button variant={tone === 'danger' ? 'danger' : 'primary'} onClick={onConfirm} loading={loading}>
            {confirmLabel}
          </Button>
        </>
      }
    />
  )
}
