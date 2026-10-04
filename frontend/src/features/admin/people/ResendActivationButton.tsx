import { useMutation } from '@tanstack/react-query'
import { Send } from 'lucide-react'
import { errorMessage } from '@/api/errors'
import { resendActivation } from '@/api/people'
import { Button } from '@/components/ui/Button'
import { useToast } from '@/components/ui/toastContext'

/** Sends a fresh activation link to an account that has not been activated; older links stop working. */
export function ResendActivationButton({ userId, name }: { userId: number; name: string }) {
  const toast = useToast()
  const mutation = useMutation({
    mutationFn: () => resendActivation(userId),
    onSuccess: () => toast.success(`A new activation link was sent to ${name}.`),
    onError: (error) => toast.error(errorMessage(error)),
  })
  return (
    <Button variant="ghost" size="sm" loading={mutation.isPending} onClick={() => mutation.mutate()}>
      {!mutation.isPending && <Send aria-hidden="true" className="size-4" />}
      Resend link
    </Button>
  )
}
