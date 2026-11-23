import { Suspense } from 'react';
import type { ReactNode } from 'react';
import { createBrowserRouter } from 'react-router-dom';
import { AppShell } from './layouts/AppShell';
import { RequireAuth, RequireCapability } from './guards/RequireAuth';
import {
  AuditPage,
  ChangePasswordPage,
  DashboardPage,
  DepartmentsPage,
  DirectoratesPage,
  DistributionDetailPage,
  DistributionsPage,
  DivisionsPage,
  DocumentDetailPage,
  DocumentsPage,
  EmployeeDetailPage,
  EmployeesPage,
  LoginPage,
  MasterDataPage,
  MigrationPage,
  NotFoundPage,
  NotificationsPage,
  OrganizationPage,
  ProfilePage,
  RequestDetailPage,
  RequestsPage,
  RoleDetailPage,
  RolesPage,
  RouteFallback,
  TemplateDetailPage,
  TemplatesPage,
  UserDetailPage,
  UnitDetailPage,
  UsersPage,
  WorkspaceDetailPage,
  WorkspacesPage,
} from './routes/pages';

function withSuspense(element: ReactNode) {
  return <Suspense fallback={<RouteFallback />}>{element}</Suspense>;
}

export const router = createBrowserRouter([
  { path: '/login', element: withSuspense(<LoginPage />) },
  {
    path: '/change-password',
    element: (
      <RequireAuth allowPasswordChangeRequired>
        {withSuspense(<ChangePasswordPage />)}
      </RequireAuth>
    ),
  },
  {
    path: '/',
    element: (
      <RequireAuth>
        <AppShell />
      </RequireAuth>
    ),
    children: [
      { index: true, element: <DashboardPage /> },
      {
        path: 'documents',
        element: (
          <RequireCapability capability="document.view">
            <DocumentsPage />
          </RequireCapability>
        ),
      },
      {
        path: 'documents/:documentId',
        element: (
          <RequireCapability capability="document.view">
            <DocumentDetailPage />
          </RequireCapability>
        ),
      },
      {
        path: 'workspaces',
        element: (
          <RequireCapability capability="workspace.view">
            <WorkspacesPage />
          </RequireCapability>
        ),
      },
      {
        path: 'workspaces/:workspaceId',
        element: (
          <RequireCapability capability="workspace.view">
            <WorkspaceDetailPage />
          </RequireCapability>
        ),
      },
      {
        path: 'requests',
        element: <RequestsPage />,
      },
      {
        path: 'requests/:requestId',
        element: <RequestDetailPage />,
      },
      {
        path: 'distributions',
        element: <DistributionsPage />,
      },
      {
        path: 'distributions/:distributionId',
        element: <DistributionDetailPage />,
      },
      {
        path: 'templates',
        element: (
          <RequireCapability capability="template.view">
            <TemplatesPage />
          </RequireCapability>
        ),
      },
      {
        path: 'templates/:templateId',
        element: (
          <RequireCapability capability="template.view">
            <TemplateDetailPage />
          </RequireCapability>
        ),
      },
      {
        path: 'notifications',
        element: <NotificationsPage />,
      },
      {
        path: 'profile',
        element: <ProfilePage />,
      },
      {
        path: 'admin/master-data',
        element: (
          <RequireCapability capability="master_data.manage">
            <MasterDataPage />
          </RequireCapability>
        ),
      },
      {
        path: 'admin/users',
        element: (
          <RequireCapability capability="user.manage">
            <UsersPage />
          </RequireCapability>
        ),
      },
      {
        path: 'admin/users/:userId',
        element: (
          <RequireCapability capability="user.manage">
            <UserDetailPage />
          </RequireCapability>
        ),
      },
      {
        path: 'admin/organization',
        element: (
          <RequireCapability capability="organization.manage">
            <OrganizationPage />
          </RequireCapability>
        ),
      },
      {
        path: 'admin/organization/directorates',
        element: (
          <RequireCapability capability="organization.manage">
            <DirectoratesPage />
          </RequireCapability>
        ),
      },
      {
        path: 'admin/organization/departments',
        element: (
          <RequireCapability capability="organization.manage">
            <DepartmentsPage />
          </RequireCapability>
        ),
      },
      {
        path: 'admin/organization/divisions',
        element: (
          <RequireCapability capability="organization.manage">
            <DivisionsPage />
          </RequireCapability>
        ),
      },
      {
        path: 'admin/organization/units/:unitId',
        element: (
          <RequireCapability capability="organization.manage">
            <UnitDetailPage />
          </RequireCapability>
        ),
      },
      {
        path: 'admin/employees',
        element: (
          <RequireCapability capability="employee.view">
            <EmployeesPage />
          </RequireCapability>
        ),
      },
      {
        path: 'admin/employees/:employeeId',
        element: (
          <RequireCapability capability="employee.view">
            <EmployeeDetailPage />
          </RequireCapability>
        ),
      },
      {
        path: 'admin/migration',
        element: (
          <RequireCapability capability="employee.view">
            <MigrationPage />
          </RequireCapability>
        ),
      },
      {
        path: 'admin/roles',
        element: (
          <RequireCapability capability="role.manage">
            <RolesPage />
          </RequireCapability>
        ),
      },
      {
        path: 'admin/roles/:roleId',
        element: (
          <RequireCapability capability="role.manage">
            <RoleDetailPage />
          </RequireCapability>
        ),
      },
      {
        path: 'admin/audit',
        element: (
          <RequireCapability capability="audit.view">
            <AuditPage />
          </RequireCapability>
        ),
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
