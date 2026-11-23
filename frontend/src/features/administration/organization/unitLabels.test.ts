import { describe, expect, it } from 'vitest';
import type { OrganizationUnit } from '../../../shared/api/types';
import { expectedParentType, parentCandidates, typeLabel, unitPath } from './unitLabels';

function unit(partial: Partial<OrganizationUnit> & { id: string }): OrganizationUnit {
  return {
    parentId: null,
    unitCode: partial.id.toUpperCase(),
    unitName: partial.id,
    description: null,
    unitType: null,
    status: 'ACTIVE',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...partial,
  };
}

describe('expectedParentType', () => {
  it('maps the hierarchy COMPANY > DIRECTORATE > DEPARTMENT > DIVISION', () => {
    expect(expectedParentType('DIRECTORATE')).toBe('COMPANY');
    expect(expectedParentType('DEPARTMENT')).toBe('DIRECTORATE');
    expect(expectedParentType('DIVISION')).toBe('DEPARTMENT');
    expect(expectedParentType('COMPANY')).toBeNull();
  });
});

describe('typeLabel', () => {
  it('labels DIVISION as Biro/Division and untyped units explicitly', () => {
    expect(typeLabel('DIVISION')).toBe('Biro/Division');
    expect(typeLabel(null)).toBe('Belum bertipe');
  });
});

describe('parentCandidates', () => {
  const company = unit({ id: 'c1', unitType: 'COMPANY' });
  const directorate = unit({ id: 'd1', unitType: 'DIRECTORATE', parentId: 'c1' });
  const inactiveDirectorate = unit({
    id: 'd2',
    unitType: 'DIRECTORATE',
    parentId: 'c1',
    status: 'INACTIVE',
  });
  const department = unit({ id: 'dept1', unitType: 'DEPARTMENT', parentId: 'd1' });
  const units = [company, directorate, inactiveDirectorate, department];

  it('offers only active units of the required parent type', () => {
    expect(parentCandidates(units, 'DEPARTMENT').map((item) => item.id)).toEqual(['d1']);
    expect(parentCandidates(units, 'DIVISION').map((item) => item.id)).toEqual(['dept1']);
  });

  it('offers no parent for COMPANY and excludes the edited unit itself', () => {
    expect(parentCandidates(units, 'COMPANY')).toEqual([]);
    expect(parentCandidates(units, '').map((item) => item.id)).toEqual(['c1', 'd1', 'dept1']);
    expect(parentCandidates(units, '', 'd1').map((item) => item.id)).toEqual(['c1', 'dept1']);
  });
});

describe('unitPath', () => {
  it('returns the root-to-unit chain', () => {
    const company = unit({ id: 'c1', unitType: 'COMPANY' });
    const directorate = unit({ id: 'd1', unitType: 'DIRECTORATE', parentId: 'c1' });
    const division = unit({ id: 'v1', unitType: 'DIVISION', parentId: 'd1' });
    const byId = new Map([company, directorate, division].map((item) => [item.id, item]));
    expect(unitPath(division, byId).map((item) => item.id)).toEqual(['c1', 'd1', 'v1']);
  });

  it('stops on cycles and missing parents', () => {
    const a = unit({ id: 'a', parentId: 'b' });
    const b = unit({ id: 'b', parentId: 'a' });
    const byId = new Map([a, b].map((item) => [item.id, item]));
    expect(unitPath(a, byId).map((item) => item.id)).toEqual(['b', 'a']);
    const orphan = unit({ id: 'o', parentId: 'missing' });
    expect(unitPath(orphan, new Map([[orphan.id, orphan]])).map((item) => item.id)).toEqual(['o']);
  });
});