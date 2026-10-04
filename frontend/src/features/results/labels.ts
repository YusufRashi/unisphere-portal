import type { BadgeTone } from '@/components/ui/Badge'
import type { ResultsStatus } from '@/types/api'

export const RESULTS_STATUS_LABELS: Record<ResultsStatus, string> = {
  DRAFT: 'Marks in progress',
  SUBMITTED: 'Awaiting registrar',
  FINALIZED: 'Finalized',
}

export const RESULTS_STATUS_TONES: Record<ResultsStatus, BadgeTone> = {
  DRAFT: 'neutral',
  SUBMITTED: 'warning',
  FINALIZED: 'success',
}
