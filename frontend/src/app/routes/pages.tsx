import { lazy } from 'react';
import { Link } from 'react-router-dom';
import { LoadingBlock } from '../../shared/components/Feedback';

export const LoginPage = lazy(() =>
  import('../../features/auth/LoginPage').then((module) => ({ default: module.LoginPage })),
);
export const ChangePasswordPage = lazy(() =>
  import('../../features/auth/ChangePasswordPage').then((module) => ({
    default: module.ChangePasswordPage,
  })),
);
export const DashboardPage = lazy(() =>
  import('../../features/dashboard/DashboardPage').then((module) => ({
    default: module.DashboardPage,
  })),
);
export const UsersPage = lazy(() =>
  import('../../features/administration/users/UsersPage').then((module) => ({
    default: module.UsersPage,
  })),
);
export const UserDetailPage = lazy(() =>
  import('../../features/administration/users/UserDetailPage').then((module) => ({
    default: module.UserDetailPage,
  })),
);
export const OrganizationPage = lazy(() =>
  import('../../features/administration/organization/OrganizationPage').then((module) => ({
    default: module.OrganizationPage,
  })),
);
export const DirectoratesPage = lazy(() =>
  import('../../features/administration/organization/UnitTypePage').then((module) => ({
    default: module.DirectoratesPage,
  })),
);
export const DepartmentsPage = lazy(() =>
  import('../../features/administration/organization/UnitTypePage').then((module) => ({
    default: module.DepartmentsPage,
  })),
);
export const DivisionsPage = lazy(() =>
  import('../../features/administration/organization/UnitTypePage').then((module) => ({
    default: module.DivisionsPage,
  })),
);
export const UnitDetailPage = lazy(() =>
  import('../../features/administration/organization/UnitDetailPage').then((module) => ({
    default: module.UnitDetailPage,
  })),
);
export const EmployeesPage = lazy(() =>
  import('../../features/administration/employees/EmployeesPage').then((module) => ({
    default: module.EmployeesPage,
  })),
);
export const EmployeeDetailPage = lazy(() =>
  import('../../features/administration/employees/EmployeeDetailPage').then((module) => ({
    default: module.EmployeeDetailPage,
  })),
);
export const MigrationPage = lazy(() =>
  import('../../features/administration/migration/MigrationPage').then((module) => ({
    default: module.MigrationPage,
  })),
);
export const RolesPage = lazy(() =>
  import('../../features/administration/roles/RolesPage').then((module) => ({
    default: module.RolesPage,
  })),
);
export const RoleDetailPage = lazy(() =>
  import('../../features/administration/roles/RoleDetailPage').then((module) => ({
    default: module.RoleDetailPage,
  })),
);
export const WorkspacesPage = lazy(() =>
  import('../../features/workspaces/WorkspacesPage').then((module) => ({
    default: module.WorkspacesPage,
  })),
);
export const WorkspaceDetailPage = lazy(() =>
  import('../../features/workspaces/WorkspaceDetailPage').then((module) => ({
    default: module.WorkspaceDetailPage,
  })),
);
export const DocumentsPage = lazy(() =>
  import('../../features/documents/DocumentsPage').then((module) => ({
    default: module.DocumentsPage,
  })),
);
export const DocumentDetailPage = lazy(() =>
  import('../../features/documents/DocumentDetailPage').then((module) => ({
    default: module.DocumentDetailPage,
  })),
);
export const MasterDataPage = lazy(() =>
  import('../../features/masterdata/MasterDataPage').then((module) => ({
    default: module.MasterDataPage,
  })),
);
export const RequestsPage = lazy(() =>
  import('../../features/requests/RequestsPage').then((module) => ({
    default: module.RequestsPage,
  })),
);
export const RequestDetailPage = lazy(() =>
  import('../../features/requests/RequestDetailPage').then((module) => ({
    default: module.RequestDetailPage,
  })),
);
export const DistributionsPage = lazy(() =>
  import('../../features/distributions/DistributionsPage').then((module) => ({
    default: module.DistributionsPage,
  })),
);
export const DistributionDetailPage = lazy(() =>
  import('../../features/distributions/DistributionDetailPage').then((module) => ({
    default: module.DistributionDetailPage,
  })),
);
export const TemplatesPage = lazy(() =>
  import('../../features/templates/TemplatesPage').then((module) => ({
    default: module.TemplatesPage,
  })),
);
export const TemplateDetailPage = lazy(() =>
  import('../../features/templates/TemplateDetailPage').then((module) => ({
    default: module.TemplateDetailPage,
  })),
);
export const NotificationsPage = lazy(() =>
  import('../../features/notifications/NotificationsPage').then((module) => ({
    default: module.NotificationsPage,
  })),
);
export const ProfilePage = lazy(() =>
  import('../../features/profile/ProfilePage').then((module) => ({
    default: module.ProfilePage,
  })),
);
export const AuditPage = lazy(() =>
  import('../../features/administration/audit/AuditPage').then((module) => ({
    default: module.AuditPage,
  })),
);

export function RouteFallback() {
  return <LoadingBlock label="Memuat halaman…" />;
}

export function NotFoundPage() {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center bg-background px-4 text-center">
      <p className="text-sm font-bold tracking-[0.12em] text-primary">KCIC DOCS</p>
      <h1 className="mt-3 text-3xl font-bold text-ink">Halaman tidak ditemukan</h1>
      <p className="mt-2 max-w-md text-sm text-ink-secondary">
        Alamat yang Anda buka tidak tersedia atau sudah dipindahkan.
      </p>
      <Link
        to="/"
        className="mt-6 inline-flex h-10 items-center rounded-lg bg-primary px-4 text-sm font-semibold text-white hover:bg-primary-hover"
      >
        Kembali ke dashboard
      </Link>
    </div>
  );
}
