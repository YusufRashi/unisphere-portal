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
          { path: '/account', lazy: async () => ({ Component: (await import('@/features/account/AccountPage')).AccountPage }) },
          {
            path: '/admin',
            element: <RequireRole roles={['ADMIN']} />,
            children: [
              { path: 'users', lazy: async () => ({ Component: (await import('@/features/admin/users/UsersPage')).UsersPage }) },
              { path: 'departments', lazy: async () => ({ Component: (await import('@/features/admin/departments/DepartmentsPage')).DepartmentsPage }) },
              { path: 'programs', lazy: async () => ({ Component: (await import('@/features/admin/programs/ProgramsPage')).ProgramsPage }) },
              { path: 'courses', lazy: async () => ({ Component: (await import('@/features/admin/courses/CoursesPage')).CoursesPage }) },
              { path: 'courses/:courseId', lazy: async () => ({ Component: (await import('@/features/admin/courses/CourseEditorPage')).CourseEditorPage }) },
              { path: 'semesters', lazy: async () => ({ Component: (await import('@/features/admin/semesters/SemestersPage')).SemestersPage }) },
              { path: 'offerings', lazy: async () => ({ Component: (await import('@/features/admin/offerings/OfferingsPage')).OfferingsPage }) },
              { path: 'offerings/:offeringId', lazy: async () => ({ Component: (await import('@/features/admin/offerings/OfferingEditorPage')).OfferingEditorPage }) },
              { path: 'students', lazy: async () => ({ Component: (await import('@/features/admin/students/StudentsPage')).StudentsPage }) },
              { path: 'lecturers', lazy: async () => ({ Component: (await import('@/features/admin/lecturers/LecturersPage')).LecturersPage }) },
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
