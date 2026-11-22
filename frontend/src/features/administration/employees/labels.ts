import type { EmploymentStatus } from '../../../shared/api/types';

export const employmentStatusLabels: Record<EmploymentStatus, string> = {
  ACTIVE: 'Aktif',
  INACTIVE: 'Nonaktif',
  TERMINATED: 'Berhenti',
};
