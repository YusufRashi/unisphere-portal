import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Plus, Trash2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Controller, useFieldArray, useForm, useWatch } from 'react-hook-form'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { createOffering, getOffering, updateOffering } from '@/api/calendar'
import { errorMessage, toApiError } from '@/api/errors'
import { applyServerErrors } from '@/api/formErrors'
import { PageHeader } from '@/components/layout/PageHeader'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { SelectField, TextField } from '@/components/ui/fields'
import { SearchSelect } from '@/components/ui/SearchSelect'
import { Spinner } from '@/components/ui/Spinner'
import { useToast } from '@/components/ui/toastContext'
import { DAY_LONG, fromLocalInput, shortTime, toLocalInput } from '@/lib/datetime'
import { emptyToNull } from '@/lib/format'
import { DAYS_OF_WEEK, type Offering } from '@/types/api'
import { courseOptions, lecturerOptions, useSemesters } from '../queries'
import { offeringSchema, type OfferingFormInput, type OfferingFormValues } from './offeringSchema'

const FIELDS = ['semesterId', 'section', 'capacity', 'catWeight', 'examStartsAt', 'examVenue'] as const

function OfferingForm({ offering, defaultSemesterId }: { offering: Offering | null; defaultSemesterId?: string }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const toast = useToast()
  const semesters = useSemesters()
  const [formError, setFormError] = useState<string | null>(null)

  const { register, control, handleSubmit, setError, setValue, getValues, formState } = useForm<OfferingFormInput, unknown, OfferingFormValues>({
    resolver: zodResolver(offeringSchema),
    defaultValues: {
      course: offering ? { value: offering.course.id, label: `${offering.course.code} — ${offering.course.title}` } : null,
      semesterId: offering ? String(offering.semester.id) : (defaultSemesterId ?? ''),
      section: offering?.section ?? 'A',
      lecturer: offering?.lecturer ? { value: offering.lecturer.id, label: offering.lecturer.displayName } : null,
      capacity: String(offering?.capacity ?? 40),
      catWeight: String(offering?.catWeight ?? 30),
      examStartsAt: toLocalInput(offering?.examStartsAt),
      examVenue: offering?.examVenue ?? '',
      sessions: offering?.sessions.map((s) => ({ ...s, startTime: shortTime(s.startTime), endTime: shortTime(s.endTime) })) ?? [
        { dayOfWeek: 'MONDAY', startTime: '08:00', endTime: '10:00', venue: '' },
      ],
    },
  })
  const { fields, append, remove } = useFieldArray({ control, name: 'sessions' })

  // Opened without ?semesterId (bookmark, history): default to the current semester once it is known.
  const currentSemesterId = semesters.data?.find((s) => s.current && s.status !== 'COMPLETED')?.id
  useEffect(() => {
    if (!offering && currentSemesterId && !getValues('semesterId')) {
      setValue('semesterId', String(currentSemesterId))
    }
  }, [offering, currentSemesterId, getValues, setValue])
  const catWeight = Number(useWatch({ control, name: 'catWeight' }))
  const errors = formState.errors

  const save = useMutation({
    mutationFn: (v: OfferingFormValues) => {
      const input = {
        courseId: v.course!.value,
        semesterId: v.semesterId,
        section: v.section,
        lecturerId: v.lecturer?.value ?? null,
        capacity: v.capacity,
        catWeight: v.catWeight,
        examWeight: 100 - v.catWeight,
        examStartsAt: fromLocalInput(v.examStartsAt),
        examVenue: emptyToNull(v.examVenue),
        sessions: v.sessions,
      }
      return offering ? updateOffering(offering.id, input) : createOffering(input)
    },
    onSuccess: (saved) => {
      void queryClient.invalidateQueries({ queryKey: ['offerings'] })
      void queryClient.invalidateQueries({ queryKey: ['semesters'] })
      queryClient.setQueryData(['offering', saved.id], saved)
      toast.success(`${saved.course.code} section ${saved.section} ${offering ? 'saved' : 'created'}.`)
      navigate(`/admin/offerings?semesterId=${saved.semester.id}`)
    },
    onError: (error) => {
      const apiError = toApiError(error)
      // Venue/lecturer clashes and duplicates explain themselves best as a banner.
      if (apiError.code === 'SCHEDULE_CONFLICT' || apiError.code === 'DUPLICATE_RESOURCE') {
        setFormError(apiError.message)
      } else {
        setFormError(applyServerErrors(error, setError, FIELDS))
      }
      window.scrollTo({ top: 0, behavior: 'smooth' })
    },
  })

  return (
    <form noValidate onSubmit={handleSubmit((v) => save.mutate(v))} className="space-y-6">
      {formError && <Alert tone="error" title="The offering could not be saved">{formError}</Alert>}

      <Card>
        <CardHeader title="Course & teaching" />
        <CardBody className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <Controller
              control={control}
              name="course"
              render={({ field, fieldState }) => (
                <SearchSelect
                  label="Course"
                  queryKey="offering-course"
                  loadOptions={courseOptions}
                  value={field.value}
                  onChange={field.onChange}
                  disabled={!!offering}
                  hint={offering ? 'The course of an existing offering cannot be changed.' : undefined}
                  error={fieldState.error?.message}
                />
              )}
            />
            {offering ? (
              // Not registered: a disabled registered field would submit as undefined. The id stays in defaultValues.
              <TextField label="Semester" value={offering.semester.name} disabled readOnly />
            ) : (
              <SelectField label="Semester" error={errors.semesterId?.message} {...register('semesterId')}>
                <option value="">Choose a semester…</option>
                {semesters.data
                  ?.filter((s) => s.status !== 'COMPLETED')
                  .map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.name}
                    </option>
                  ))}
              </SelectField>
            )}
          </div>
          <div className="grid gap-4 sm:grid-cols-[8rem_1fr_10rem]">
            <TextField label="Section" error={errors.section?.message} {...register('section')} />
            <Controller
              control={control}
              name="lecturer"
              render={({ field }) => (
                <SearchSelect
                  label="Lecturer"
                  queryKey="offering-lecturer"
                  loadOptions={lecturerOptions}
                  value={field.value}
                  onChange={field.onChange}
                  placeholder="Search by name or staff number"
                  hint="Optional — can be assigned later."
                />
              )}
            />
            <TextField label="Capacity" type="number" min={1} max={1000} error={errors.capacity?.message} {...register('capacity')} />
          </div>
        </CardBody>
      </Card>

      <Card>
        <CardHeader title="Weekly timetable" description="Venue and lecturer clashes with other offerings are checked when you save." />
        <CardBody className="space-y-3">
          {fields.length === 0 && <p className="text-sm text-neutral-500">No weekly sessions (e.g. a project or self-study course).</p>}
          {fields.map((f, i) => {
            const e = errors.sessions?.[i]
            return (
              <fieldset key={f.id} className="grid items-start gap-3 rounded-md border border-neutral-200 p-3 sm:grid-cols-[10rem_7rem_7rem_minmax(0,1fr)_auto]">
                <legend className="sr-only">Session {i + 1}</legend>
                <SelectField label="Day" error={e?.dayOfWeek?.message} {...register(`sessions.${i}.dayOfWeek`)}>
                  {DAYS_OF_WEEK.map((d) => (
                    <option key={d} value={d}>
                      {DAY_LONG[d]}
                    </option>
                  ))}
                </SelectField>
                <TextField label="Starts" type="time" step={300} error={e?.startTime?.message} {...register(`sessions.${i}.startTime`)} />
                <TextField label="Ends" type="time" step={300} error={e?.endTime?.message} {...register(`sessions.${i}.endTime`)} />
                <TextField label="Venue" placeholder="LT-1" error={e?.venue?.message} {...register(`sessions.${i}.venue`)} />
                <Button variant="ghost" size="sm" className="justify-self-end text-red-700 hover:bg-red-50 sm:mt-6" onClick={() => remove(i)} aria-label={`Remove session ${i + 1}`}>
                  <Trash2 aria-hidden="true" className="size-4" />
                </Button>
              </fieldset>
            )
          })}
          {errors.sessions?.message && <p className="text-xs font-medium text-red-700">{errors.sessions.message}</p>}
          <Button
            variant="secondary"
            size="sm"
            disabled={fields.length >= 10}
            onClick={() => append({ dayOfWeek: 'MONDAY', startTime: '08:00', endTime: '10:00', venue: '' })}
          >
            <Plus aria-hidden="true" className="size-4" /> Add session
          </Button>
        </CardBody>
      </Card>

      <Card>
        <CardHeader title="Assessment & examination" />
        <CardBody className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField
              label="Continuous assessment weight (%)"
              type="number"
              min={0}
              max={100}
              error={errors.catWeight?.message}
              hint={Number.isFinite(catWeight) && catWeight >= 0 && catWeight <= 100 ? `Final exam: ${100 - catWeight}%` : undefined}
              {...register('catWeight')}
            />
          </div>
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField label="Exam date & time" type="datetime-local" error={errors.examStartsAt?.message} hint="Optional — must fall within the semester." {...register('examStartsAt')} />
            <TextField label="Exam venue" placeholder="Main Hall" error={errors.examVenue?.message} {...register('examVenue')} />
          </div>
        </CardBody>
      </Card>

      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={() => navigate(-1)}>
          Cancel
        </Button>
        <Button type="submit" loading={save.isPending}>
          {offering ? 'Save changes' : 'Create offering'}
        </Button>
      </div>
    </form>
  )
}

export function OfferingEditorPage() {
  const { offeringId } = useParams()
  const [params] = useSearchParams()
  const isNew = offeringId === 'new'
  const id = Number(offeringId)
  const query = useQuery({ queryKey: ['offering', id], queryFn: () => getOffering(id), enabled: !isNew && Number.isInteger(id) })

  const back = (
    <Link to="/admin/offerings" className="mb-2 inline-flex items-center gap-1 text-sm text-neutral-600 hover:text-neutral-900">
      <ArrowLeft aria-hidden="true" className="size-4" /> Course offerings
    </Link>
  )

  if (isNew) {
    return (
      <>
        {back}
        <PageHeader title="New course offering" />
        <div className="max-w-4xl">
          <OfferingForm offering={null} defaultSemesterId={params.get('semesterId') ?? undefined} />
        </div>
      </>
    )
  }
  if (query.error) {
    return (
      <>
        {back}
        <Alert tone="error" title="Could not load the offering">
          {errorMessage(query.error)}
        </Alert>
      </>
    )
  }
  if (!query.data) {
    return (
      <div className="flex justify-center p-12 text-brand-700">
        <Spinner label="Loading offering…" />
      </div>
    )
  }
  const o = query.data
  return (
    <>
      {back}
      <PageHeader title={`${o.course.code} · Section ${o.section}`} description={`${o.course.title} · ${o.semester.name}`} />
      <div className="max-w-4xl">
        <OfferingForm key={o.id} offering={o} />
      </div>
    </>
  )
}
