import studyTogether from '@/assets/photos/study-together.webp'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Megaphone } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useSearchParams } from 'react-router'
import { z } from 'zod'
import { errorMessage } from '@/api/errors'
import { applyServerErrors } from '@/api/formErrors'
import { getOffering, searchOfferings } from '@/api/calendar'
import { getAnnouncements, publishAnnouncement } from '@/api/notifications'
import { getMyTeaching } from '@/api/results'
import { useAuth } from '@/auth/authContext'
import { PageHeader } from '@/components/layout/PageHeader'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { EmptyState } from '@/components/ui/EmptyState'
import { SelectField, TextAreaField, TextField } from '@/components/ui/fields'
import { Modal } from '@/components/ui/Modal'
import { Pagination } from '@/components/ui/Pagination'
import { SearchSelect, type SelectOption } from '@/components/ui/SearchSelect'
import { Spinner } from '@/components/ui/Spinner'
import { useToast } from '@/components/ui/toastContext'
import { formatDateTime, timeAgo } from '@/lib/format'
import type { Offering, TeachingOffering } from '@/types/api'

/** Lecturers choose from the courses they teach; the registrar may post to any course. */
type CourseChoice = { kind: 'lecturer'; teaching: TeachingOffering[] } | { kind: 'registrar' }

function offeringOption(o: Offering): SelectOption {
  return { value: o.id, label: `${o.course.code} · Section ${o.section}`, description: `${o.course.title} · ${o.semester.name}` }
}

const PAGE_SIZE = 10

const announcementSchema = z.object({
  offeringId: z.string().min(1, 'Choose a course.'),
  title: z.string().trim().min(1, 'Enter a title.').max(200, 'At most 200 characters.'),
  body: z.string().trim().min(1, 'Write the announcement.').max(5000, 'At most 5000 characters.'),
})

function ComposeModal({ courses, initialOfferingId, onClose }: { courses: CourseChoice; initialOfferingId?: number; onClose: () => void }) {
  const teaching = courses.kind === 'lecturer' ? courses.teaching : []
  const toast = useToast()
  const queryClient = useQueryClient()
  const [formError, setFormError] = useState<string | null>(null)
  // The course the page is filtered to, or the lecturer's only course.
  const preselected = initialOfferingId ?? (teaching.length === 1 ? teaching[0]?.offering.id : undefined)
  const { register, handleSubmit, setError, setValue, formState } = useForm<z.input<typeof announcementSchema>, unknown, z.output<typeof announcementSchema>>({
    resolver: zodResolver(announcementSchema),
    defaultValues: { offeringId: preselected ? String(preselected) : '', title: '', body: '' },
  })
  const errors = formState.errors
  const [chosen, setChosen] = useState<SelectOption | null>(null)
  // The registrar arriving from a course's announcements: show that course as already chosen.
  const initialCourse = useQuery({
    queryKey: ['offering', initialOfferingId],
    queryFn: () => getOffering(initialOfferingId!),
    enabled: courses.kind === 'registrar' && initialOfferingId !== undefined,
  })
  const registrarChoice = chosen ?? (initialCourse.data ? offeringOption(initialCourse.data) : null)

  const mutation = useMutation({
    mutationFn: ({ offeringId, title, body }: z.output<typeof announcementSchema>) => publishAnnouncement(Number(offeringId), { title, body }),
    onSuccess: ({ announcement, notified }) => {
      void queryClient.invalidateQueries({ queryKey: ['announcements'] })
      toast.success(`Announcement posted to ${announcement.courseCode}. ${notified} ${notified === 1 ? 'student was' : 'students were'} notified.`)
      onClose()
    },
    onError: (error) => setFormError(applyServerErrors(error, setError, ['title', 'body'])),
  })

  return (
    <Modal
      open
      title="New announcement"
      description="Every student enrolled in the course is notified."
      onClose={onClose}
      busy={mutation.isPending}
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={mutation.isPending}>
            Cancel
          </Button>
          <Button type="submit" form="announcement" loading={mutation.isPending}>
            Post announcement
          </Button>
        </>
      }
    >
      <form id="announcement" noValidate onSubmit={handleSubmit((v) => mutation.mutate(v))} className="space-y-4">
        {formError && <Alert tone="error">{formError}</Alert>}
        {courses.kind === 'lecturer' ? (
          <SelectField label="Course" error={errors.offeringId?.message} {...register('offeringId')}>
            <option value="">Choose a course…</option>
            {teaching.map(({ offering: o }) => (
              <option key={o.id} value={o.id}>
                {o.course.code} · Section {o.section} — {o.course.title}
              </option>
            ))}
          </SelectField>
        ) : (
          <SearchSelect
            label="Course"
            queryKey="announcement-offerings"
            placeholder="Search by course code or title"
            loadOptions={async (q) => (await searchOfferings({ q, size: 10 })).content.map(offeringOption)}
            value={registrarChoice}
            onChange={(option) => {
              setChosen(option)
              setValue('offeringId', option ? String(option.value) : '', { shouldValidate: formState.isSubmitted })
            }}
            error={errors.offeringId?.message}
            hint="Any course, in any semester. Everyone enrolled in it is notified."
          />
        )}
        <TextField label="Title" maxLength={200} error={errors.title?.message} {...register('title')} />
        <TextAreaField label="Message" rows={6} maxLength={5000} error={errors.body?.message} {...register('body')} />
      </form>
    </Modal>
  )
}

/** Announcements for the courses the user is enrolled in or teaches. Lecturers and the registrar post new ones here. */
export function AnnouncementsPage() {
  const { hasRole } = useAuth()
  const isLecturer = hasRole('LECTURER')
  const isRegistrar = hasRole('ADMIN')
  const canPost = isLecturer || isRegistrar
  const [params, setParams] = useSearchParams()
  const offeringId = Number(params.get('offeringId')) || undefined
  const page = Math.max(Number(params.get('page')) || 0, 0)
  const [composing, setComposing] = useState(false)

  const query = useQuery({
    queryKey: ['announcements', { offeringId, page }],
    queryFn: () => getAnnouncements({ offeringId, page, size: PAGE_SIZE }),
  })
  const teaching = useQuery({ queryKey: ['lecturer', 'teaching', undefined], queryFn: () => getMyTeaching(), enabled: isLecturer })
  const course = query.data?.content[0]
  const composeCourses: CourseChoice | null = isRegistrar
    ? { kind: 'registrar' }
    : teaching.data
      ? { kind: 'lecturer', teaching: teaching.data }
      : null

  return (
    <>
      <PageHeader
        image={studyTogether}
        imageClassName="object-[center_35%]"
        title="Announcements"
        description={isRegistrar ? 'Messages to the students of any course.' : isLecturer ? 'Messages to the students in your courses.' : 'Messages from the lecturers of your courses.'}
        actions={
          canPost ? (
            <Button onClick={() => setComposing(true)} disabled={!isRegistrar && !teaching.data?.length}>
              <Megaphone aria-hidden="true" className="size-4" /> New announcement
            </Button>
          ) : undefined
        }
      />
      {offeringId && (
        <p className="mb-4 text-sm text-neutral-600">
          Showing announcements for {course ? `${course.courseCode} · Section ${course.section}` : 'one course'}.{' '}
          <Link to="/announcements" className="font-medium text-brand-700 hover:text-brand-900">
            Show all courses
          </Link>
        </p>
      )}

      {query.error ? (
        <Alert tone="error">{errorMessage(query.error)}</Alert>
      ) : !query.data ? (
        <div className="flex justify-center p-12 text-brand-700">
          <Spinner label="Loading announcements…" />
        </div>
      ) : query.data.content.length === 0 ? (
        <Card>
          <EmptyState
            icon={Megaphone}
            title="No announcements yet"
            description={canPost ? 'Post one to reach every student in a course at once.' : 'Announcements from your lecturers will appear here.'}
          />
        </Card>
      ) : (
        <>
          <ul aria-label="Announcements" className="space-y-4">
            {query.data.content.map((a) => (
              <li key={a.id}>
                <Card>
                  <article aria-labelledby={`announcement-${a.id}`} className="px-5 py-4">
                    <p className="text-xs font-medium text-brand-700">
                      <Link to={`/announcements?offeringId=${a.offeringId}`} className="hover:text-brand-900">
                        {a.courseCode} · Section {a.section}
                      </Link>{' '}
                      <span className="text-neutral-500">— {a.courseTitle}</span>
                    </p>
                    <h2 id={`announcement-${a.id}`} className="mt-1 text-base font-semibold text-neutral-900">
                      {a.title}
                    </h2>
                    <p className="mt-2 text-sm whitespace-pre-line break-words text-neutral-700">{a.body}</p>
                    <p className="mt-3 text-xs text-neutral-500">
                      {a.authorName} ·{' '}
                      <time dateTime={a.createdAt} title={formatDateTime(a.createdAt)}>
                        {timeAgo(a.createdAt)}
                      </time>
                    </p>
                  </article>
                </Card>
              </li>
            ))}
          </ul>
          <Card className="mt-4">
            <Pagination
              page={query.data.page}
              totalPages={query.data.totalPages}
              totalElements={query.data.totalElements}
              size={query.data.size}
              onPageChange={(p) => setParams({ ...(offeringId ? { offeringId: String(offeringId) } : {}), page: String(p) })}
            />
          </Card>
        </>
      )}

      {composing && composeCourses && <ComposeModal courses={composeCourses} initialOfferingId={offeringId} onClose={() => setComposing(false)} />}
    </>
  )
}
