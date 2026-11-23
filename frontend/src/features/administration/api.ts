import { apiRequest } from '../../shared/api/client';
import type {
  AccountStatus,
  AssignmentType,
  EmployeeDetail,
  EmployeeSummary,
  EmploymentStatus,
  LinkedAccount,
  OrganizationUnit,
  PageResponse,
  Permission,
  Role,
  RoleAssignmentView,
  RoleHolderView,
  ScopeType,
  UnitMember,
  UnitType,
  UserDetail,
  UserSummary,
} from '../../shared/api/types';

export interface UserListParams {
  keyword?: string;
  status?: AccountStatus | '';
  page: number;
  size: number;
}

function queryString(params: Record<string, string | number | boolean | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== '') {
      search.set(key, String(value));
    }
  }
  const query = search.toString();
  return query ? `?${query}` : '';
}

export function listUsers(params: UserListParams): Promise<PageResponse<UserSummary>> {
  return apiRequest(
    `/api/v1/admin/users${queryString({
      keyword: params.keyword,
      status: params.status,
      page: params.page,
      size: params.size,
    })}`,
  );
}

export function getUser(userId: string): Promise<UserDetail> {
  return apiRequest(`/api/v1/admin/users/${userId}`);
}

export interface CreateUserPayload {
  username: string;
  email?: string;
  displayName: string;
  temporaryPassword: string;
}

export function createUser(payload: CreateUserPayload): Promise<UserDetail> {
  return apiRequest('/api/v1/admin/users', { method: 'POST', body: payload });
}

export function updateUser(
  userId: string,
  payload: { displayName: string; email?: string },
): Promise<UserDetail> {
  return apiRequest(`/api/v1/admin/users/${userId}`, { method: 'PUT', body: payload });
}

export function changeUserStatus(userId: string, status: AccountStatus): Promise<UserSummary> {
  return apiRequest(`/api/v1/admin/users/${userId}/status`, {
    method: 'POST',
    body: { status },
  });
}

export function resetUserPassword(userId: string, temporaryPassword: string): Promise<void> {
  return apiRequest(`/api/v1/admin/users/${userId}/reset-password`, {
    method: 'POST',
    body: { temporaryPassword },
  });
}

export function unlockUser(userId: string): Promise<UserSummary> {
  return apiRequest(`/api/v1/admin/users/${userId}/unlock`, { method: 'POST' });
}

export function assignUnit(
  userId: string,
  payload: { organizationUnitId: string; assignmentType: AssignmentType; validUntil?: string | null },
): Promise<UnitMember> {
  return apiRequest(`/api/v1/admin/users/${userId}/units`, { method: 'POST', body: payload });
}

export function endUnitAssignment(userId: string, assignmentId: string): Promise<void> {
  return apiRequest(`/api/v1/admin/users/${userId}/units/${assignmentId}`, {
    method: 'DELETE',
  });
}

export function listActiveUnits(): Promise<OrganizationUnit[]> {
  return apiRequest('/api/v1/organization-units');
}

export function listAllUnits(): Promise<OrganizationUnit[]> {
  return apiRequest('/api/v1/admin/organization-units');
}

export function listUnitsByType(unitType: UnitType): Promise<OrganizationUnit[]> {
  return apiRequest(`/api/v1/admin/organization-units?unitType=${unitType}`);
}

export function getUnit(unitId: string): Promise<OrganizationUnit> {
  return apiRequest(`/api/v1/admin/organization-units/${unitId}`);
}

export function createUnit(payload: {
  parentId?: string | null;
  unitType?: UnitType | null;
  unitCode: string;
  unitName: string;
  description?: string;
}): Promise<OrganizationUnit> {
  return apiRequest('/api/v1/admin/organization-units', { method: 'POST', body: payload });
}

export function updateUnit(
  unitId: string,
  payload: {
    parentId?: string | null;
    unitType?: UnitType | null;
    unitName: string;
    description?: string;
    status: 'ACTIVE' | 'INACTIVE';
  },
): Promise<OrganizationUnit> {
  return apiRequest(`/api/v1/admin/organization-units/${unitId}`, {
    method: 'PUT',
    body: payload,
  });
}

export interface EmployeeListParams {
  keyword?: string;
  status?: EmploymentStatus | '';
  linked?: boolean;
  page: number;
  size: number;
}

export interface EmployeeProfilePayload {
  nip: string;
  fullName: string;
  email: string;
  employmentStatus: EmploymentStatus;
}

export function listEmployees(params: EmployeeListParams): Promise<PageResponse<EmployeeSummary>> {
  return apiRequest(
    `/api/v1/admin/employees${queryString({
      keyword: params.keyword,
      status: params.status,
      linked: params.linked,
      page: params.page,
      size: params.size,
    })}`,
  );
}

export function getEmployee(employeeId: string): Promise<EmployeeDetail> {
  return apiRequest(`/api/v1/admin/employees/${employeeId}`);
}

export function createEmployee(
  payload: EmployeeProfilePayload & { divisionUnitId: string },
): Promise<EmployeeDetail> {
  return apiRequest('/api/v1/admin/employees', { method: 'POST', body: payload });
}

export function updateEmployee(
  employeeId: string,
  payload: EmployeeProfilePayload,
): Promise<EmployeeDetail> {
  return apiRequest(`/api/v1/admin/employees/${employeeId}`, { method: 'PUT', body: payload });
}

export function transferEmployee(
  employeeId: string,
  payload: { divisionUnitId: string; expectedPlacementId?: string | null },
): Promise<EmployeeDetail> {
  return apiRequest(`/api/v1/admin/employees/${employeeId}/placements`, {
    method: 'POST',
    body: payload,
  });
}

export function createEmployeeAccount(
  employeeId: string,
  payload: { username: string; temporaryPassword: string },
): Promise<LinkedAccount> {
  return apiRequest(`/api/v1/admin/employees/${employeeId}/account`, {
    method: 'POST',
    body: payload,
  });
}

export function linkEmployeeAccount(
  employeeId: string,
  userId: string,
): Promise<LinkedAccount> {
  return apiRequest(`/api/v1/admin/users/${userId}/employee-link`, {
    method: 'POST',
    body: { employeeId },
  });
}

export function listUnlinkedAccounts(params: {
  keyword?: string;
  page: number;
  size: number;
}): Promise<PageResponse<UserSummary>> {
  return apiRequest(
    `/api/v1/admin/users/unlinked${queryString({
      keyword: params.keyword,
      page: params.page,
      size: params.size,
    })}`,
  );
}

export function listUnitMembers(unitId: string): Promise<UnitMember[]> {
  return apiRequest(`/api/v1/admin/organization-units/${unitId}/members`);
}

export function listRoles(): Promise<Role[]> {
  return apiRequest('/api/v1/admin/roles');
}

export function listPermissions(): Promise<Permission[]> {
  return apiRequest('/api/v1/admin/permissions');
}

export function createRole(payload: {
  roleCode: string;
  roleName: string;
  description?: string;
}): Promise<Role> {
  return apiRequest('/api/v1/admin/roles', { method: 'POST', body: payload });
}

export function updateRole(
  roleId: string,
  payload: { roleName: string; description?: string; status: 'ACTIVE' | 'INACTIVE' },
): Promise<Role> {
  return apiRequest(`/api/v1/admin/roles/${roleId}`, { method: 'PUT', body: payload });
}

export function setRolePermissions(roleId: string, permissionIds: string[]): Promise<Role> {
  return apiRequest(`/api/v1/admin/roles/${roleId}/permissions`, {
    method: 'PUT',
    body: { permissionIds },
  });
}

export function listRoleAssignments(userId: string): Promise<RoleAssignmentView[]> {
  return apiRequest(`/api/v1/admin/users/${userId}/role-assignments`);
}

export function listRoleHolders(roleId: string): Promise<RoleHolderView[]> {
  return apiRequest(`/api/v1/admin/roles/${roleId}/assignments`);
}

export function assignRole(
  userId: string,
  payload: {
    roleCode: string;
    scopeType: ScopeType;
    organizationUnitId?: string | null;
    workspaceId?: string | null;
    includeDescendants?: boolean;
    validUntil?: string | null;
  },
): Promise<RoleAssignmentView> {
  return apiRequest(`/api/v1/admin/users/${userId}/role-assignments`, {
    method: 'POST',
    body: payload,
  });
}

export function revokeRoleAssignment(assignmentId: string): Promise<void> {
  return apiRequest(`/api/v1/admin/role-assignments/${assignmentId}`, { method: 'DELETE' });
}
