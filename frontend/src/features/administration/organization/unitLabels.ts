import type { OrganizationUnit, UnitType } from '../../../shared/api/types';

export const UNIT_TYPE_LABELS: Record<UnitType, string> = {
  COMPANY: 'Perusahaan',
  DIRECTORATE: 'Direktorat',
  DEPARTMENT: 'Departemen',
  DIVISION: 'Biro/Division',
};

export function expectedParentType(type: UnitType): UnitType | null {
  switch (type) {
    case 'DIRECTORATE':
      return 'COMPANY';
    case 'DEPARTMENT':
      return 'DIRECTORATE';
    case 'DIVISION':
      return 'DEPARTMENT';
    default:
      return null;
  }
}

export function typeLabel(type: UnitType | null): string {
  return type ? UNIT_TYPE_LABELS[type] : 'Belum bertipe';
}

export function parentCandidates(
  units: OrganizationUnit[],
  typeValue: string,
  excludeId?: string,
): OrganizationUnit[] {
  if (typeValue === 'COMPANY') {
    return [];
  }
  const required = typeValue === '' ? null : expectedParentType(typeValue as UnitType);
  return units.filter(
    (unit) =>
      unit.id !== excludeId &&
      unit.status === 'ACTIVE' &&
      (required === null || unit.unitType === required),
  );
}

export function unitPath(
  unit: OrganizationUnit,
  byId: Map<string, OrganizationUnit>,
): OrganizationUnit[] {
  const chain: OrganizationUnit[] = [unit];
  const seen = new Set<string>([unit.id]);
  let current = unit;
  while (current.parentId && !seen.has(current.parentId)) {
    seen.add(current.parentId);
    const parent = byId.get(current.parentId);
    if (!parent) {
      break;
    }
    chain.unshift(parent);
    current = parent;
    if (chain.length > 20) {
      break;
    }
  }
  return chain;
}