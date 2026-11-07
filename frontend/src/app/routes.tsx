import { Navigate, type RouteObject } from 'react-router'
import { RedirectIfAuthenticated, RequireAuth, RequireRole } from '@/auth/guards'
import { LoginPage } from '@/auth/pages/LoginPage'
import { AppLayout } from '@/components/layout/AppLayout'
import { FullPageSpinner } from '@/components/ui/Spinner'
import { NotFoundPage } from '@/pages/NotFoundPage'

// The sign-in page and app shell are in the main bundle; every feature page is code-split so each
// role only downloads the screens it actually opens.
const appRoutes: RouteObject[] = [
  { path: '/login', element: <RedirectIfAuthenticated><LoginPage /></RedirectIfAuthenticated> },
  {
    path: '/forgot-password',
    lazy: async () => {
      const { ForgotPasswordPage } = await import('@/auth/pages/ForgotPasswordPage')
      return { element: <RedirectIfAuthenticated><ForgotPasswordPage /></RedirectIfAuthenticated> }
    },
  },
  // Activation and reset links work regardless of the current session.
  { path: '/activate', lazy: async () => ({ Component: (await import('@/auth/pages/ActivateAccountPage')).ActivateAccountPage }) },
  { path: '/reset-password', lazy: async () => ({ Component: (await import('@/auth/pages/ResetPasswordPage')).ResetPasswordPage }) },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { index: true, element: <Navigate to="/dashboard" replace /> },
          { path: '/dashboard', lazy: async () => ({ Component: (await import('@/features/home/HomePage')).HomePage }) },
          { path: '/notifications', lazy: async () => ({ Component: (await import('@/features/notifications/NotificationsPage')).NotificationsPage }) },
          {
            path: '/announcements',
            element: <RequireRole roles={['STUDENT', 'LECTURER', 'ADMIN']} />,
            children: [{ index: true, lazy: async () => ({ Component: (await import('@/features/announcements/AnnouncementsPage')).AnnouncementsPage }) }],
          },
          { path: '/account', lazy: async () => ({ Component: (await import('@/features/account/AccountPage')).AccountPage }) },
          {
            path: '/student',
            element: <RequireRole roles={['STUDENT']} />,
            children: [
              { path: 'registration', lazy: async () => ({ Component: (await import('@/features/student/registration/RegistrationPage')).RegistrationPage }) },
              { path: 'timetable', lazy: async () => ({ Component: (await import('@/features/student/timetable/TimetablePage')).TimetablePage }) },
              { path: 'results', lazy: async () => ({ Component: (await import('@/features/student/results/ResultsPage')).ResultsPage }) },
              { path: 'fees', lazy: async () => ({ Component: (await import('@/features/student/fees/FeesPage')).FeesPage }) },
              {
                path: 'transcript',
                lazy: async () => {
                  const { TranscriptPage } = await import('@/features/transcript/TranscriptPage')
                  return { element: <TranscriptPage mode="student" /> }
                },
              },
            ],
          },
          {
            path: '/lecturer',
            element: <RequireRole roles={['LECTURER']} />,
            children: [
              { path: 'courses', lazy: async () => ({ Component: (await import('@/features/lecturer/courses/MyCoursesPage')).MyCoursesPage }) },
              {
                path: 'courses/:offeringId',
                lazy: async () => {
                  const { MarksSheetPage } = await import('@/features/results/MarksSheetPage')
                  return { element: <MarksSheetPage mode="lecturer" /> }
                },
              },
            ],
          },
          {
            // Finance staff manage fees; administrators (registrar) can view everything here but change nothing.
            path: '/finance',
            element: <RequireRole roles={['FINANCE', 'ADMIN']} />,
            children: [
              { path: 'accounts', lazy: async () => ({ Component: (await import('@/features/finance/accounts/FeeAccountsPage')).FeeAccountsPage }) },
              { path: 'accounts/:studentId', lazy: async () => ({ Component: (await import('@/features/finance/accounts/FeeAccountPage')).FeeAccountPage }) },
              { path: 'structures', lazy: async () => ({ Component: (await import('@/features/finance/structures/FeeStructuresPage')).FeeStructuresPage }) },
              { path: 'reports', lazy: async () => ({ Component: (await import('@/features/finance/reports/FinanceReportsPage')).FinanceReportsPage }) },
            ],
          },
          {
            path: '/admin',
            element: <RequireRole roles={['ADMIN']} />,
            children: [
              { path: 'users', lazy: async () => ({ Component: (await import('@/features/admin/users/UsersPage')).UsersPage }) },
              { path: 'audit', lazy: async () => ({ Component: (await import('@/features/admin/audit/AuditLogPage')).AuditLogPage }) },
              { path: 'departments', lazy: async () => ({ Component: (await import('@/features/admin/departments/DepartmentsPage')).DepartmentsPage }) },
              { path: 'programs', lazy: async () => ({ Component: (await import('@/features/admin/programs/ProgramsPage')).ProgramsPage }) },
              { path: 'courses', lazy: async () => ({ Component: (await import('@/features/admin/courses/CoursesPage')).CoursesPage }) },
              { path: 'courses/:courseId', lazy: async () => ({ Component: (await import('@/features/admin/courses/CourseEditorPage')).CourseEditorPage }) },
              { path: 'semesters', lazy: async () => ({ Component: (await import('@/features/admin/semesters/SemestersPage')).SemestersPage }) },
              { path: 'offerings', lazy: async () => ({ Component: (await import('@/features/admin/offerings/OfferingsPage')).OfferingsPage }) },
              { path: 'offerings/:offeringId', lazy: async () => ({ Component: (await import('@/features/admin/offerings/OfferingEditorPage')).OfferingEditorPage }) },
              { path: 'students', lazy: async () => ({ Component: (await import('@/features/admin/students/StudentsPage')).StudentsPage }) },
              {
                path: 'students/:studentId/transcript',
                lazy: async () => {
                  const { TranscriptPage } = await import('@/features/transcript/TranscriptPage')
                  return { element: <TranscriptPage mode="registrar" /> }
                },
              },
              { path: 'transcripts/verify', lazy: async () => ({ Component: (await import('@/features/transcript/VerifyTranscriptPage')).VerifyTranscriptPage }) },
              { path: 'lecturers', lazy: async () => ({ Component: (await import('@/features/admin/lecturers/LecturersPage')).LecturersPage }) },
              { path: 'registrations', lazy: async () => ({ Component: (await import('@/features/admin/registrations/RegistrationsPage')).RegistrationsPage }) },
              { path: 'results', lazy: async () => ({ Component: (await import('@/features/admin/results/ResultsQueuePage')).ResultsQueuePage }) },
              {
                path: 'results/:offeringId',
                lazy: async () => {
                  const { MarksSheetPage } = await import('@/features/results/MarksSheetPage')
                  return { element: <MarksSheetPage mode="registrar" /> }
                },
              },
              { path: 'grading-scale', lazy: async () => ({ Component: (await import('@/features/admin/results/GradingScalePage')).GradingScalePage }) },
            ],
          },
        ],
      },
    ],
  },
  { path: '*', element: <NotFoundPage /> },
]

export const routes: RouteObject[] = [
  // Shown while the code for the first page is downloading.
  { hydrateFallbackElement: <FullPageSpinner label="Loading…" />, children: appRoutes },
]
