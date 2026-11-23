export interface FieldErrorItem {
  field: string;
  message: string;
}

export interface ApiErrorPayload {
  code: string;
  message: string;
  fieldErrors?: FieldErrorItem[];
  requestId?: string;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export type AccountStatus = 'ACTIVE' | 'INACTIVE' | 'LOCKED';
export type AssignmentType = 'PRIMARY' | 'SECONDARY';
export type ScopeType = 'GLOBAL' | 'ORGANIZATION_UNIT' | 'WORKSPACE';

export interface UnitRef {
  id: string;
  unitCode: string;
  unitName: string;
  assignmentType: AssignmentType;
}

export interface MeResponse {
  userId: string;
  username: string;
  email: string | null;
  displayName: string;
  mustChangePassword: boolean;
  roles: string[];
  capabilities: string[];
  organizationUnits: UnitRef[];
  lastLoginAt: string | null;
}

export interface UserSummary {
  id: string;
  username: string;
  email: string | null;
  displayName: string;
  status: AccountStatus;
  mustChangePassword: boolean;
  employeeId: string | null;
  lastLoginAt: string | null;
  createdAt: string;
}

export interface RoleAssignmentView {
  id: string;
  roleId: string;
  roleCode: string;
  roleName: string;
  scopeType: ScopeType;
  organizationUnitId: string | null;
  workspaceId: string | null;
  includeDescendants: boolean;
  validFrom: string;
  validUntil: string | null;
  status: string;
  createdAt: string;
}

export interface RoleHolderView {
  id: string;
  userId: string;
  username: string;
  displayName: string;
  accountStatus: AccountStatus;
  scopeType: ScopeType;
  organizationUnitId: string | null;
  organizationUnitCode: string | null;
  organizationUnitName: string | null;
  organizationUnitType: UnitType | null;
  workspaceId: string | null;
  workspaceName: string | null;
  includeDescendants: boolean;
  validFrom: string;
  validUntil: string | null;
  status: string;
  createdAt: string;
}

export interface UnitMembershipView {
  id: string;
  organizationUnitId: string;
  unitCode: string;
  unitName: string;
  assignmentType: AssignmentType;
  active: boolean;
  validFrom: string;
  validUntil: string | null;
}

export interface UserDetail {
  user: UserSummary;
  roleAssignments: RoleAssignmentView[];
  organizationUnits: UnitMembershipView[];
}

export type EmploymentStatus = 'ACTIVE' | 'INACTIVE' | 'TERMINATED';

export interface EmployeeSummary {
  id: string;
  nip: string;
  fullName: string;
  email: string;
  employmentStatus: EmploymentStatus;
  activePlacementId: string | null;
  divisionUnitId: string | null;
  divisionCode: string | null;
  divisionName: string | null;
  departmentId: string | null;
  departmentName: string | null;
  directorateId: string | null;
  directorateName: string | null;
  hasAccount: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface PlacementView {
  id: string;
  divisionUnitId: string;
  divisionCode: string | null;
  divisionName: string | null;
  departmentName: string | null;
  directorateName: string | null;
  validFrom: string;
  validUntil: string | null;
  active: boolean;
  createdAt: string;
}

export interface LinkedAccount {
  userId: string;
  username: string;
  status: AccountStatus;
  mustChangePassword: boolean;
  lastLoginAt: string | null;
}

export interface EmployeeDetail {
  employee: EmployeeSummary;
  placements: PlacementView[];
  linkedAccount: LinkedAccount | null;
}

export type UnitType = 'COMPANY' | 'DIRECTORATE' | 'DEPARTMENT' | 'DIVISION';

export interface OrganizationUnit {
  id: string;
  parentId: string | null;
  unitCode: string;
  unitName: string;
  description: string | null;
  unitType: UnitType | null;
  status: 'ACTIVE' | 'INACTIVE';
  createdAt: string;
  updatedAt: string;
}

export interface UnitMember {
  assignmentId: string;
  userId: string;
  username: string;
  displayName: string;
  assignmentType: AssignmentType;
  active: boolean;
  validFrom: string;
  validUntil: string | null;
}

export interface Role {
  id: string;
  roleCode: string;
  roleName: string;
  description: string | null;
  systemRole: boolean;
  status: 'ACTIVE' | 'INACTIVE';
  permissions: string[];
}

export interface Permission {
  id: string;
  permissionCode: string;
  permissionName: string;
  description: string | null;
  moduleCode: string;
}

export type WorkspaceStatus = 'ACTIVE' | 'INACTIVE' | 'ARCHIVED';
export type FolderStatus = 'ACTIVE' | 'ARCHIVED';

export interface Workspace {
  id: string;
  workspaceCode: string;
  workspaceName: string;
  description: string | null;
  ownerOrganizationUnitId: string;
  ownerOrganizationUnitName: string | null;
  ownerUserId: string | null;
  status: WorkspaceStatus;
  activeFolderCount: number;
  canManage: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface RepositoryFolder {
  id: string;
  workspaceId: string;
  parentFolderId: string | null;
  folderName: string;
  description: string | null;
  status: FolderStatus;
  canManage: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface DocumentCategory {
  id: string;
  parentId: string | null;
  categoryCode: string;
  categoryName: string;
  description: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface DocumentType {
  id: string;
  typeCode: string;
  typeName: string;
  description: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface DocumentClassification {
  id: string;
  classificationCode: string;
  classificationName: string;
  sensitivityRank: number;
  description: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface DocumentTag {
  id: string;
  tagCode: string;
  tagName: string;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface FilePolicy {
  id: string;
  policyCode: string;
  policyName: string;
  allowedExtensions: string[];
  allowedMimeTypes: string[];
  maxFileSizeBytes: number;
  defaultPolicy: boolean;
  active: boolean;
  updatedAt: string;
}

export type DocumentStatus = 'DRAFT' | 'ACTIVE' | 'ARCHIVED';
export type ContentSource = 'STORAGE_OBJECT' | 'EXTERNAL_LINK';
export type VersionStatus = 'DRAFT' | 'CURRENT' | 'SUPERSEDED' | 'WITHDRAWN';

export interface DocumentTagRef {
  id: string;
  tagCode: string;
  tagName: string;
}

export interface DocumentSummary {
  id: string;
  documentCode: string | null;
  title: string;
  status: DocumentStatus;
  workspaceId: string;
  workspaceName: string | null;
  folderId: string | null;
  folderName: string | null;
  categoryId: string;
  categoryName: string | null;
  documentTypeId: string;
  documentTypeName: string | null;
  classificationId: string;
  classificationName: string | null;
  classificationRank: number;
  ownerUserId: string | null;
  ownerUserName: string | null;
  ownerOrganizationUnitId: string | null;
  ownerOrganizationUnitName: string | null;
  tags: DocumentTagRef[];
  currentVersionNumber: number | null;
  currentVersionLabel: string | null;
  contentSource: ContentSource | null;
  updatedAt: string;
  favorited: boolean;
}

export interface DocumentVersion {
  id: string;
  versionNumber: number;
  versionLabel: string | null;
  contentSource: ContentSource;
  status: VersionStatus;
  changeSummary: string | null;
  createdBy: string;
  createdByName: string | null;
  createdAt: string;
  publishedAt: string | null;
  originalFilename: string | null;
  contentType: string | null;
  sizeBytes: number | null;
  checksumSha256: string | null;
  externalUrl: string | null;
  externalHost: string | null;
  canPreview: boolean;
  canDownload: boolean;
}

export interface OwnerHistoryEntry {
  id: string;
  ownerUserId: string | null;
  ownerUserName: string | null;
  ownerOrganizationUnitId: string | null;
  ownerOrganizationUnitName: string | null;
  changedBy: string;
  changedByName: string | null;
  changeReason: string | null;
  validFrom: string;
  validUntil: string | null;
}

export interface DocumentPermissions {
  canEdit: boolean;
  canUpload: boolean;
  canCreateVersion: boolean;
  canArchive: boolean;
  canRestore: boolean;
  canPreview: boolean;
  canDownload: boolean;
  canManageAccess: boolean;
}

export type AccessResourceType = 'WORKSPACE' | 'FOLDER' | 'DOCUMENT';
export type PrincipalType = 'USER' | 'ROLE' | 'ORGANIZATION_UNIT';
export type AclEffect = 'ALLOW' | 'DENY';
export type GrantSource = 'MANUAL' | 'REQUEST' | 'DISTRIBUTION' | 'SYSTEM';

export interface AccessGrant {
  id: string;
  resourceType: AccessResourceType;
  resourceId: string;
  principalType: PrincipalType;
  principalId: string;
  principalName: string | null;
  permissionCode: string;
  permissionName: string | null;
  effect: AclEffect;
  grantSource: GrantSource;
  includeDescendants: boolean;
  grantedBy: string | null;
  grantedByName: string | null;
  grantedAt: string;
  expiresAt: string | null;
  active: boolean;
  revokedBy: string | null;
  revokedByName: string | null;
  revokedAt: string | null;
}

export interface AccessSummary {
  resourceType: AccessResourceType;
  resourceId: string;
  canManage: boolean;
  active: AccessGrant[];
  history: AccessGrant[];
}

export interface AccessPrincipalOption {
  id: string;
  name: string;
  detail: string;
}

export interface AccessDirectory {
  users: AccessPrincipalOption[];
  roles: AccessPrincipalOption[];
  units: AccessPrincipalOption[];
}

export interface DocumentDetail {
  summary: DocumentSummary;
  description: string | null;
  effectiveDate: string | null;
  reviewDate: string | null;
  expiryDate: string | null;
  createdAt: string;
  createdBy: string;
  createdByName: string | null;
  currentVersion: DocumentVersion | null;
  ownerHistory: OwnerHistoryEntry[];
  versionCount: number;
  permissions: DocumentPermissions;
}

export interface DocumentActivity {
  id: string;
  actionCode: string;
  outcome: string;
  actorUserId: string | null;
  actorName: string | null;
  occurredAt: string;
  metadata: Record<string, unknown>;
}

export interface ExternalLinkInfo {
  url: string;
  host: string;
  warning: string;
}

export type RequestType = 'ACCESS' | 'DOCUMENT_DELIVERY' | 'TEMPLATE';
export type RequestStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'UNDER_REVIEW'
  | 'APPROVED'
  | 'REJECTED'
  | 'IN_PROGRESS'
  | 'FULFILLED'
  | 'CLOSED'
  | 'CANCELLED';
export type RequestScope = 'own' | 'assigned';
export type FulfillmentType =
  | 'ACCESS_GRANTED'
  | 'DOCUMENT_LINKED'
  | 'DOCUMENT_UPLOADED'
  | 'EXTERNAL_LINK_REGISTERED';

export interface RequestSummary {
  id: string;
  requestCode: string;
  requestType: RequestType;
  title: string;
  requesterUserId: string;
  requesterName: string | null;
  assignedToUserId: string | null;
  assignedToName: string | null;
  targetDocumentId: string | null;
  targetDocumentTitle: string | null;
  targetOrganizationUnitId: string | null;
  targetOrganizationUnitName: string | null;
  status: RequestStatus;
  neededByDate: string | null;
  submittedAt: string | null;
  createdAt: string;
  updatedAt: string;
  canApprove: boolean;
  canReject: boolean;
  canFulfill: boolean;
  canCancel: boolean;
  canClose: boolean;
}

export interface RequestApproval {
  id: string;
  approverUserId: string;
  approverName: string | null;
  decision: 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';
  decisionReason: string | null;
  assignedAt: string;
  decidedAt: string | null;
}

export interface RequestFulfillmentItem {
  documentId: string;
  documentTitle: string | null;
  documentVersionId: string | null;
}

export interface RequestFulfillment {
  id: string;
  type: FulfillmentType;
  fulfilledByUserId: string;
  fulfilledByName: string | null;
  note: string | null;
  externalUrl: string | null;
  externalLinkTitle: string | null;
  fulfilledAt: string;
  items: RequestFulfillmentItem[];
}

export interface RequestHistoryEntry {
  id: string;
  actorUserId: string | null;
  actorName: string | null;
  fromStatus: RequestStatus | null;
  toStatus: RequestStatus;
  comment: string | null;
  occurredAt: string;
}

export interface RequestDetail {
  summary: RequestSummary;
  description: string | null;
  reason: string;
  targetOwnerUserId: string | null;
  targetOwnerName: string | null;
  approvals: RequestApproval[];
  fulfillments: RequestFulfillment[];
  history: RequestHistoryEntry[];
}

export interface NotificationItem {
  id: string;
  type: string;
  title: string;
  message: string | null;
  targetResourceType: string | null;
  targetResourceId: string | null;
  read: boolean;
  readAt: string | null;
  createdAt: string;
}

export interface UnreadCount {
  unreadCount: number;
}
