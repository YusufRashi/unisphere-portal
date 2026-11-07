import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { BookOpen, Plus, Search } from 'lucide-react'
import { Link, useNavigate } from 'react-router'
import { searchCourses } from '@/api/academic'
import { DataTable, SortTh, TableToolbar, Td, Th } from '@/components/table/DataTable'
import { PageHeader } from '@/components/layout/PageHeader'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Card } from '@/components/ui/Card'
import { SelectField, TextField } from '@/components/ui/fields'
import { Pagination } from '@/components/ui/Pagination'
import { useListParams } from '@/hooks/useListParams'
import { useDepartments } from '../queries'

const PAGE_SIZE = 20
const LEVELS = [100, 200, 300, 400, 500, 600, 700, 800, 900]

export function CoursesPage() {
  const navigate = useNavigate()
  const list = useListParams('code,asc')
  const departments = useDepartments()
  const departmentId = list.getNumber('departmentId')
  const level = list.getNumber('level')
  const status = list.get('status')

  const params = {
    q: list.q,
    departmentId,
    level,
    active: status === '' ? undefined : status === 'active',
    sort: list.sort,
    page: list.page,
    size: PAGE_SIZE,
  }
  const query = useQuery({ queryKey: ['courses', params], queryFn: () => searchCourses(params), placeholderData: keepPreviousData })
  const data = query.data
  const onSort = (sort: string) => list.update({ sort })

  return (
    <>
      <PageHeader
        title="Courses"
        description="The course catalogue. Courses are offered in semesters as course offerings."
        actions={
          <Button onClick={() => navigate('/admin/courses/new')}>
            <Plus aria-hidden="true" className="size-4" /> New course
          </Button>
        }
      />
      <Card>
        <TableToolbar className="sm:grid-cols-[minmax(0,1fr)_14rem_9rem_10rem]">
          <div className="relative">
            <Search aria-hidden="true" className="pointer-events-none absolute top-2.5 left-3 size-4 text-neutral-400" />
            <TextField
              label="Search courses"
              hideLabel
              type="search"
              placeholder="Search by code or title"
              className="pl-9"
              value={list.searchInput}
              onChange={(e) => list.setSearchInput(e.target.value)}
            />
          </div>
          <SelectField label="Department" hideLabel value={departmentId ?? ''} onChange={(e) => list.update({ departmentId: e.target.value })}>
            <option value="">All departments</option>
            {departments.data?.map((d) => (
              <option key={d.id} value={d.id}>
                {d.name}
              </option>
            ))}
          </SelectField>
          <SelectField label="Level" hideLabel value={level ?? ''} onChange={(e) => list.update({ level: e.target.value })}>
            <option value="">All levels</option>
            {LEVELS.map((l) => (
              <option key={l} value={l}>
                Level {l}
              </option>
            ))}
          </SelectField>
          <SelectField label="Status" hideLabel value={status} onChange={(e) => list.update({ status: e.target.value })}>
            <option value="">All statuses</option>
            <option value="active">Active</option>
            <option value="inactive">Inactive</option>
          </SelectField>
        </TableToolbar>
        <DataTable
          caption="Courses"
          isLoading={!data}
          error={query.error}
          onRetry={() => query.refetch()}
          isFetching={query.isFetching}
          isEmpty={data?.content.length === 0}
          emptyIcon={list.hasFilters ? Search : BookOpen}
          emptyTitle={list.hasFilters ? 'No courses match your filters' : 'No courses yet'}
          emptyAction={
            list.hasFilters ? (
              <Button variant="secondary" onClick={list.clear}>
                Clear filters
              </Button>
            ) : undefined
          }
          head={
            <>
              <SortTh field="code" label="Course" sort={list.sort} onSort={onSort} className="min-w-48" />
              <Th className="hidden md:table-cell">Department</Th>
              <SortTh field="credits" label="Credits" sort={list.sort} onSort={onSort} className="text-right" />
              <SortTh field="level" label="Level" sort={list.sort} onSort={onSort} className="hidden sm:table-cell" />
              <Th className="hidden lg:table-cell">Prerequisites</Th>
              <Th>Status</Th>
            </>
          }
          footer={
            data && (
              <Pagination
                page={data.page}
                size={data.size}
                totalPages={data.totalPages}
                totalElements={data.totalElements}
                onPageChange={(page) => list.update({ page })}
              />
            )
          }
        >
          {data?.content.map((c) => (
            <tr key={c.id} className="hover:bg-neutral-50/60">
              <Td>
                <Link to={`/admin/courses/${c.id}`} className="font-medium text-brand-800 hover:underline">
                  {c.code}
                </Link>
                <p className="text-sm text-neutral-600">{c.title}</p>
              </Td>
              <Td className="hidden text-neutral-600 md:table-cell">{c.department.name}</Td>
              <Td className="text-right tabular-nums">{c.credits}</Td>
              <Td className="hidden tabular-nums sm:table-cell">{c.level}</Td>
              <Td className="hidden text-neutral-600 lg:table-cell">{c.prerequisiteCount || '—'}</Td>
              <Td>{c.active ? <Badge tone="success">Active</Badge> : <Badge>Inactive</Badge>}</Td>
            </tr>
          ))}
        </DataTable>
      </Card>
    </>
  )
}
