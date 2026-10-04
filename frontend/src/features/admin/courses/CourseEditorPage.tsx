import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Link2, X } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useParams } from 'react-router'
import { z } from 'zod'
import { createCourse, getCourse, replacePrerequisites, updateCourse } from '@/api/academic'
import { errorMessage, toApiError } from '@/api/errors'
import { applyServerErrors } from '@/api/formErrors'
import { PageHeader } from '@/components/layout/PageHeader'
import { Alert } from '@/components/ui/Alert'
import { Button } from '@/components/ui/Button'
import { Card, CardBody, CardHeader } from '@/components/ui/Card'
import { CheckboxField, SelectField, TextAreaField, TextField } from '@/components/ui/fields'
import { SearchSelect } from '@/components/ui/SearchSelect'
import { Spinner } from '@/components/ui/Spinner'
import { useToast } from '@/components/ui/toastContext'
import { emptyToNull } from '@/lib/format'
import type { CourseDetail } from '@/types/api'

type Prerequisite = Pick<CourseDetail['prerequisites'][number], 'id' | 'code' | 'title'>
import { prerequisiteOptions, useDepartments } from '../queries'

const schema = z.object({
  departmentId: z.coerce.number<string>().int().positive('Choose a department.'),
  code: z.string().trim().min(1, 'Enter a code.').max(15).regex(/^[A-Za-z0-9][A-Za-z0-9-]*$/, 'Letters, digits and hyphens only.'),
  title: z.string().trim().min(1, 'Enter a title.').max(150),
  description: z.string().max(4000, 'At most 4000 characters.'),
  credits: z.coerce.number<string>().int('Whole numbers only.').min(1, 'Between 1 and 10.').max(10, 'Between 1 and 10.'),
  level: z.coerce.number<string>().int().refine((l) => l >= 100 && l <= 900 && l % 100 === 0, 'Choose a level.'),
  active: z.boolean(),
})
type FormInput = z.input<typeof schema>
type FormValues = z.output<typeof schema>
const FIELDS = ['departmentId', 'code', 'title', 'description', 'credits', 'level'] as const

function CourseForm({ course }: { course: CourseDetail | null }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const toast = useToast()
  const departments = useDepartments()
  const [formError, setFormError] = useState<string | null>(null)
  const { register, handleSubmit, setError, formState } = useForm<FormInput, unknown, FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      departmentId: course ? String(course.department.id) : '',
      code: course?.code ?? '',
      title: course?.title ?? '',
      description: course?.description ?? '',
      credits: String(course?.credits ?? 3),
      level: String(course?.level ?? 100),
      active: course?.active ?? true,
    },
  })
  const errors = formState.errors

  const save = useMutation({
    mutationFn: (v: FormValues) => {
      const input = { ...v, description: emptyToNull(v.description) }
      return course ? updateCourse(course.id, input) : createCourse(input)
    },
    onSuccess: (saved) => {
      void queryClient.invalidateQueries({ queryKey: ['courses'] })
      queryClient.setQueryData(['course', saved.id], saved)
      toast.success(`${saved.code} ${course ? 'saved' : 'created'}.`)
      if (!course) navigate(`/admin/courses/${saved.id}`, { replace: true })
    },
    onError: (error) => setFormError(applyServerErrors(error, setError, FIELDS)),
  })

  return (
    <Card>
      <CardHeader title="Course details" />
      <CardBody>
        <form noValidate onSubmit={handleSubmit((v) => save.mutate(v))} className="space-y-4">
          {formError && <Alert tone="error">{formError}</Alert>}
          <div className="grid gap-4 sm:grid-cols-[10rem_1fr]">
            <TextField label="Code" placeholder="CS201" error={errors.code?.message} {...register('code')} />
            <TextField label="Title" placeholder="Data Structures" error={errors.title?.message} {...register('title')} />
          </div>
          <SelectField label="Department" error={errors.departmentId?.message} {...register('departmentId')}>
            <option value="">Choose a department…</option>
            {departments.data?.map((d) => (
              <option key={d.id} value={d.id}>
                {d.name}
              </option>
            ))}
          </SelectField>
          <div className="grid gap-4 sm:grid-cols-2">
            <TextField label="Credits" type="number" min={1} max={10} error={errors.credits?.message} {...register('credits')} />
            <SelectField label="Level" error={errors.level?.message} {...register('level')}>
              {[100, 200, 300, 400, 500, 600, 700, 800, 900].map((l) => (
                <option key={l} value={l}>
                  Level {l}
                </option>
              ))}
            </SelectField>
          </div>
          <TextAreaField label="Description" rows={4} error={errors.description?.message} {...register('description')} />
          <CheckboxField label="Active" description="Inactive courses stay in transcripts but cannot be offered." {...register('active')} />
          <div className="flex justify-end gap-2">
            <Button variant="secondary" onClick={() => navigate('/admin/courses')}>
              Cancel
            </Button>
            <Button type="submit" loading={save.isPending}>
              {course ? 'Save changes' : 'Create course'}
            </Button>
          </div>
        </form>
      </CardBody>
    </Card>
  )
}

function PrerequisitesCard({ course }: { course: CourseDetail }) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const [selected, setSelected] = useState<Prerequisite[]>(course.prerequisites)
  const [error, setError] = useState<string | null>(null)
  const dirty =
    selected.length !== course.prerequisites.length || selected.some((s) => !course.prerequisites.some((p) => p.id === s.id))

  const save = useMutation({
    mutationFn: () => replacePrerequisites(course.id, selected.map((s) => s.id)),
    onSuccess: (saved) => {
      setError(null)
      queryClient.setQueryData(['course', saved.id], saved)
      void queryClient.invalidateQueries({ queryKey: ['courses'] })
      toast.success('Prerequisites saved.')
    },
    // A PREREQUISITE_CYCLE (409) explains the cycle, e.g. "CS101 → CS301 → CS201 → CS101".
    onError: (e) => setError(toApiError(e).message),
  })

  return (
    <Card>
      <CardHeader title="Prerequisites" description="Students must pass these courses before registering." />
      <CardBody className="space-y-4">
        {error && <Alert tone="error">{error}</Alert>}
        {selected.length === 0 ? (
          <p className="text-sm text-slate-500">No prerequisites.</p>
        ) : (
          <ul className="flex flex-wrap gap-2" aria-label="Selected prerequisites">
            {selected.map((p) => (
              <li key={p.id} className="inline-flex items-center gap-1.5 rounded-full bg-brand-50 py-1 pr-1 pl-3 text-sm text-brand-900 ring-1 ring-brand-200">
                <span className="font-medium">{p.code}</span>
                <span className="hidden text-brand-800 sm:inline">{p.title}</span>
                <button
                  type="button"
                  onClick={() => setSelected((all) => all.filter((x) => x.id !== p.id))}
                  className="rounded-full p-0.5 hover:bg-brand-100"
                  aria-label={`Remove ${p.code}`}
                >
                  <X aria-hidden="true" className="size-3.5" />
                </button>
              </li>
            ))}
          </ul>
        )}
        <SearchSelect
          label="Add a prerequisite"
          queryKey="prerequisite-courses"
          loadOptions={prerequisiteOptions}
          value={null}
          clearOnSelect
          exclude={[course.id, ...selected.map((s) => s.id)]}
          onChange={(option) => {
            if (!option) return
            setSelected((all) => [...all, { id: option.value, code: option.label, title: option.description ?? '' }])
          }}
        />
        <div className="flex justify-end gap-2">
          <Button variant="secondary" disabled={!dirty || save.isPending} onClick={() => setSelected(course.prerequisites)}>
            Reset
          </Button>
          <Button disabled={!dirty} loading={save.isPending} onClick={() => save.mutate()}>
            <Link2 aria-hidden="true" className="size-4" /> Save prerequisites
          </Button>
        </div>
      </CardBody>
    </Card>
  )
}

export function CourseEditorPage() {
  const { courseId } = useParams()
  const isNew = courseId === 'new'
  const id = Number(courseId)
  const query = useQuery({ queryKey: ['course', id], queryFn: () => getCourse(id), enabled: !isNew && Number.isInteger(id) })

  const back = (
    <Link to="/admin/courses" className="mb-2 inline-flex items-center gap-1 text-sm text-slate-600 hover:text-slate-900">
      <ArrowLeft aria-hidden="true" className="size-4" /> Courses
    </Link>
  )

  if (isNew) {
    return (
      <>
        {back}
        <PageHeader title="New course" />
        <div className="max-w-3xl">
          <CourseForm course={null} />
        </div>
      </>
    )
  }
  if (query.error) {
    return (
      <>
        {back}
        <Alert tone="error" title="Could not load the course">
          {errorMessage(query.error)}
        </Alert>
      </>
    )
  }
  if (!query.data) {
    return (
      <div className="flex justify-center p-12 text-brand-700">
        <Spinner label="Loading course…" />
      </div>
    )
  }
  const course = query.data
  return (
    <>
      {back}
      <PageHeader title={`${course.code} · ${course.title}`} description={course.department.name} />
      <div className="grid gap-6 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
        {/* key resets the forms when another course is opened or after saving */}
        <CourseForm key={`form-${course.id}-${course.updatedAt}`} course={course} />
        <PrerequisitesCard key={`pre-${course.id}-${course.prerequisites.map((p) => p.id).join(',')}`} course={course} />
      </div>
    </>
  )
}
