#!/usr/bin/env node
/**
 * Seed DATA DUMMY lengkap untuk testing lokal KCIC Connect via REST API resmi.
 * Semua record ditandai DUMMY pada kode/nama/deskripsi. Idempotent: cek dulu,
 * buat bila belum ada. Tidak menghapus/mengubah data yang sudah ada.
 *
 * Pakai:
 *   SEED_ADMIN_USERNAME=admin SEED_ADMIN_PASSWORD='...' node tools/seed-dummy.mjs
 *
 * Kredensial akun dummy dibuat sekali dan disimpan di .local/dummy-credentials.json
 * (gitignored). Jangan commit kredensial.
 */
import { randomBytes } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';

const BASE = process.env.SEED_BASE_URL ?? 'http://localhost:8080';
const ADMIN_USER = process.env.SEED_ADMIN_USERNAME;
const ADMIN_PASS = process.env.SEED_ADMIN_PASSWORD;
if (!ADMIN_USER || !ADMIN_PASS) {
  console.error('Wajib set env SEED_ADMIN_USERNAME dan SEED_ADMIN_PASSWORD (akun admin lokal).');
  process.exit(1);
}

const LOCAL_DIR = path.resolve('.local');
const CRED_FILE = path.join(LOCAL_DIR, 'dummy-credentials.json');
mkdirSync(LOCAL_DIR, { recursive: true });
let credentials;
if (existsSync(CRED_FILE)) {
  credentials = JSON.parse(readFileSync(CRED_FILE, 'utf8'));
} else {
  const password = `Dummy!${randomBytes(6).toString('base64url')}#26`;
  credentials = { password, temporaryPassword: `${password}Tmp` };
  writeFileSync(CRED_FILE, JSON.stringify(credentials, null, 2));
}
const DUMMY_PASS = credentials.password;
const DUMMY_TEMP = credentials.temporaryPassword;

const DUMMY_NOTE = 'DATA DUMMY untuk testing lokal. Bukan data resmi perusahaan.';
const log = (...args) => console.log('[seed]', ...args);

// ------------------------------------------------------------------ HTTP

function makeSession() {
  return { cookies: new Map(), csrf: null, csrfHeader: 'X-XSRF-TOKEN' };
}

function cookieHeader(s) {
  return [...s.cookies.entries()].map(([k, v]) => `${k}=${v}`).join('; ');
}

function storeCookies(s, res) {
  for (const raw of res.headers.getSetCookie?.() ?? []) {
    const [pair] = raw.split(';');
    const idx = pair.indexOf('=');
    if (idx > 0) s.cookies.set(pair.slice(0, idx).trim(), pair.slice(idx + 1).trim());
  }
}

async function refreshCsrf(s) {
  const res = await fetch(`${BASE}/api/v1/auth/csrf`, { headers: { Cookie: cookieHeader(s) } });
  storeCookies(s, res);
  if (!res.ok) throw new Error(`CSRF endpoint gagal: ${res.status}`);
  const payload = await res.json();
  s.csrf = payload.token;
  s.csrfHeader = payload.headerName || 'X-XSRF-TOKEN';
}

async function api(s, method, urlPath, body, { form } = {}) {
  const headers = { Cookie: cookieHeader(s), Accept: 'application/json' };
  if (method !== 'GET' && s.csrf) headers[s.csrfHeader] = s.csrf;
  let payload;
  if (form) {
    payload = form;
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
    payload = JSON.stringify(body);
  }
  let res = await fetch(`${BASE}${urlPath}`, { method, headers, body: payload });
  storeCookies(s, res);
  if (res.status === 403 && method !== 'GET') {
    await refreshCsrf(s);
    headers[s.csrfHeader] = s.csrf;
    res = await fetch(`${BASE}${urlPath}`, { method, headers, body: payload });
    storeCookies(s, res);
  }
  if (res.status === 204) return null;
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    const detail = data?.fieldErrors?.map((f) => `${f.field}: ${f.message}`).join('; ');
    const err = new Error(`${method} ${urlPath} -> ${res.status} ${data?.code ?? ''} ${detail || data?.message || text}`);
    err.status = res.status;
    err.code = data?.code;
    throw err;
  }
  return data;
}

async function login(username, password) {
  const s = makeSession();
  await refreshCsrf(s);
  await api(s, 'POST', '/api/v1/auth/login', { username, password });
  await refreshCsrf(s);
  return s;
}

// -------------------------------------------------------------- file dummy

function pdfEscape(text) {
  return text.replace(/\\/g, '\\\\').replace(/\(/g, '\\(').replace(/\)/g, '\\)');
}

function makePdf(title, body) {
  const content =
    `BT /F1 16 Tf 50 780 Td (${pdfEscape(title)}) Tj ET\n` +
    `BT /F1 11 Tf 50 750 Td (${pdfEscape(body)}) Tj ET\n` +
    `BT /F1 9 Tf 50 730 Td (${pdfEscape(DUMMY_NOTE)}) Tj ET`;
  const objs = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
    `<< /Length ${content.length} >>\nstream\n${content}\nendstream`,
  ];
  let pdf = '%PDF-1.4\n';
  const offsets = [];
  objs.forEach((obj, i) => {
    offsets.push(pdf.length);
    pdf += `${i + 1} 0 obj\n${obj}\nendobj\n`;
  });
  const xref = pdf.length;
  pdf += `xref\n0 ${objs.length + 1}\n0000000000 65535 f \n`;
  pdf += offsets.map((o) => `${String(o).padStart(10, '0')} 00000 n \n`).join('');
  pdf += `trailer\n<< /Size ${objs.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF`;
  return Buffer.from(pdf, 'latin1');
}

function uploadForm(buffer, filename, contentType, fields) {
  const form = new FormData();
  form.append('file', new Blob([buffer], { type: contentType }), filename);
  for (const [key, value] of Object.entries(fields)) {
    if (value !== undefined && value !== null) form.append(key, String(value));
  }
  return form;
}

// ------------------------------------------------------------------ ensure

const eq = (a, b) => (a ?? '').toLowerCase() === (b ?? '').toLowerCase();

async function ensureUnit(admin, code, payload) {
  const units = await api(admin, 'GET', '/api/v1/admin/organization-units');
  const found = units.find((u) => eq(u.unitCode, code));
  if (found) return found;
  const created = await api(admin, 'POST', '/api/v1/admin/organization-units', { unitCode: code, ...payload });
  log('unit dibuat:', code);
  return created;
}

async function ensureTag(admin, tagCode, tagName) {
  const tags = await api(admin, 'GET', '/api/v1/admin/master-data/tags');
  const found = tags.find((t) => eq(t.tagCode, tagCode));
  if (found) return found;
  log('tag dibuat:', tagCode);
  return api(admin, 'POST', '/api/v1/admin/master-data/tags', { tagCode, tagName });
}

async function ensureEmployee(admin, nip, payload) {
  const page = await api(admin, 'GET', `/api/v1/admin/employees?keyword=${encodeURIComponent(nip)}&page=0&size=20`);
  const found = page.items.find((e) => eq(e.nip, nip));
  if (found) return api(admin, 'GET', `/api/v1/admin/employees/${found.id}`);
  log('employee dibuat:', nip);
  return api(admin, 'POST', '/api/v1/admin/employees', { nip, ...payload });
}

async function ensureAccount(admin, employeeDetail, username) {
  if (employeeDetail.linkedAccount) return employeeDetail.linkedAccount;
  log('akun employee dibuat:', username);
  return api(admin, 'POST', `/api/v1/admin/employees/${employeeDetail.employee.id}/account`, {
    username,
    temporaryPassword: DUMMY_TEMP,
  });
}

async function ensureDummyLogin(username) {
  try {
    return await login(username, DUMMY_PASS);
  } catch (err) {
    if (err.status !== 401 && err.status !== 400) throw err;
    const s = await login(username, DUMMY_TEMP);
    await api(s, 'POST', '/api/v1/auth/change-password', {
      currentPassword: DUMMY_TEMP,
      newPassword: DUMMY_PASS,
      confirmPassword: DUMMY_PASS,
    });
    log('password dummy difinalkan:', username);
    return login(username, DUMMY_PASS);
  }
}

async function ensureUnitAssignment(admin, userId, unitId) {
  const members = await api(admin, 'GET', `/api/v1/admin/organization-units/${unitId}/members`);
  if (members.some((m) => m.userId === userId && m.active !== false)) return;
  log('unit assignment:', userId, '->', unitId);
  await api(admin, 'POST', `/api/v1/admin/users/${userId}/units`, {
    organizationUnitId: unitId,
    assignmentType: 'PRIMARY',
  });
}

async function ensureRoleAssignment(admin, userId, payload) {
  const assignments = await api(admin, 'GET', `/api/v1/admin/users/${userId}/role-assignments`);
  const exists = assignments.some(
    (a) =>
      a.status === 'ACTIVE' &&
      a.roleCode === payload.roleCode &&
      a.scopeType === payload.scopeType &&
      (a.organizationUnitId ?? null) === (payload.organizationUnitId ?? null) &&
      (a.workspaceId ?? null) === (payload.workspaceId ?? null),
  );
  if (exists) return;
  log('role assignment:', payload.roleCode, payload.scopeType, '->', userId);
  await api(admin, 'POST', `/api/v1/admin/users/${userId}/role-assignments`, payload);
}

async function ensureWorkspace(admin, code, payload) {
  const list = await api(admin, 'GET', '/api/v1/workspaces');
  const found = list.find((w) => eq(w.workspaceCode, code));
  if (found) return found;
  log('workspace dibuat:', code);
  return api(admin, 'POST', '/api/v1/workspaces', { workspaceCode: code, ...payload });
}

async function ensureFolder(admin, workspaceId, folderName) {
  const list = await api(admin, 'GET', `/api/v1/workspaces/${workspaceId}/folders`);
  const found = list.find((f) => eq(f.folderName, folderName));
  if (found) return found;
  log('folder dibuat:', folderName);
  return api(admin, 'POST', `/api/v1/workspaces/${workspaceId}/folders`, { folderName, description: DUMMY_NOTE });
}

async function ensureDocument(admin, workspaceId, documentCode, payload) {
  const page = await api(
    admin,
    'GET',
    `/api/v1/documents?workspaceId=${workspaceId}&keyword=${encodeURIComponent(documentCode)}&page=0&size=20`,
  );
  const found = page.items.find((d) => eq(d.documentCode, documentCode));
  if (found) return api(admin, 'GET', `/api/v1/documents/${found.id}`);
  log('dokumen dibuat:', documentCode);
  return api(admin, 'POST', `/api/v1/workspaces/${workspaceId}/documents`, { documentCode, ...payload });
}

async function ensureVersion(admin, documentId, minCount, buffer, filename, contentType, label, summary) {
  const versions = await api(admin, 'GET', `/api/v1/documents/${documentId}/versions`);
  if (versions.length >= minCount) return versions;
  log('versi diunggah:', documentId, label);
  await api(admin, 'POST', `/api/v1/documents/${documentId}/versions`, undefined, {
    form: uploadForm(buffer, filename, contentType, { versionLabel: label, changeSummary: summary }),
  });
  return api(admin, 'GET', `/api/v1/documents/${documentId}/versions`);
}

async function ensureActive(admin, detail) {
  if (detail.summary.status === 'ACTIVE') return detail;
  log('dokumen diaktifkan:', detail.summary.documentCode);
  return api(admin, 'POST', `/api/v1/documents/${detail.summary.id}/activate`);
}

async function ensureGrant(admin, documentId, payload) {
  const acl = await api(admin, 'GET', `/api/v1/documents/${documentId}/access`);
  const exists = acl.active.some(
    (g) =>
      g.principalType === payload.principalType &&
      g.principalId === payload.principalId &&
      g.permissionCode === payload.permissionCode &&
      g.effect === payload.effect,
  );
  if (exists) return;
  log('ACL grant:', payload.principalType, payload.permissionCode, payload.effect);
  await api(admin, 'POST', `/api/v1/documents/${documentId}/access`, payload);
}

async function ensureTemplate(admin, templateCode, payload) {
  for (const status of ['PUBLISHED', 'DRAFT']) {
    const page = await api(admin, 'GET', `/api/v1/templates?keyword=Dummy&status=${status}&page=0&size=50`);
    const found = page.items.find((t) => eq(t.templateCode, templateCode));
    if (found) return api(admin, 'GET', `/api/v1/templates/${found.id}`);
  }
  log('template dibuat:', templateCode);
  return api(admin, 'POST', '/api/v1/templates', { templateCode, ...payload });
}

async function ensureRequest(session, scope, title, payload) {
  const page = await api(
    session,
    'GET',
    `/api/v1/requests?scope=${scope}&keyword=${encodeURIComponent(title)}&page=0&size=20`,
  );
  const found = page.items.find((r) => r.title === title);
  if (found) return api(session, 'GET', `/api/v1/requests/${found.id}`);
  log('request dibuat:', title);
  return api(session, 'POST', '/api/v1/requests', payload);
}

async function ensureDistribution(admin, title, payload) {
  const page = await api(
    admin,
    'GET',
    `/api/v1/distributions?box=sent&keyword=${encodeURIComponent(title)}&page=0&size=20`,
  );
  const found = page.items.find((d) => d.title === title);
  if (found) return api(admin, 'GET', `/api/v1/distributions/${found.id}`);
  log('distribusi dibuat:', title);
  return api(admin, 'POST', '/api/v1/distributions', payload);
}

// -------------------------------------------------------------------- main

async function main() {
  const admin = await login(ADMIN_USER, ADMIN_PASS);
  log('login admin OK');

  // 1. Organisasi bertipe (root COMPANY baru; unit legacy tidak disentuh).
  const company = await ensureUnit(admin, 'DUMMY-CO', {
    unitType: 'COMPANY',
    unitName: 'PT KCIC Dummy Sejahtera',
    description: DUMMY_NOTE,
  });
  const dirOps = await ensureUnit(admin, 'DUMMY-DIR-OPS', {
    parentId: company.id, unitType: 'DIRECTORATE', unitName: 'Direktorat Operasi (Dummy)', description: DUMMY_NOTE,
  });
  const dirKsd = await ensureUnit(admin, 'DUMMY-DIR-KSD', {
    parentId: company.id, unitType: 'DIRECTORATE', unitName: 'Direktorat Keuangan & SDM (Dummy)', description: DUMMY_NOTE,
  });
  const depOp = await ensureUnit(admin, 'DUMMY-DEP-OP', {
    parentId: dirOps.id, unitType: 'DEPARTMENT', unitName: 'Departemen Operasi (Dummy)', description: DUMMY_NOTE,
  });
  const depSdm = await ensureUnit(admin, 'DUMMY-DEP-SDM', {
    parentId: dirKsd.id, unitType: 'DEPARTMENT', unitName: 'Departemen SDM (Dummy)', description: DUMMY_NOTE,
  });
  const divSar = await ensureUnit(admin, 'DUMMY-DIV-SAR', {
    parentId: depOp.id, unitType: 'DIVISION', unitName: 'Biro Sarana (Dummy)', description: DUMMY_NOTE,
  });
  const divSin = await ensureUnit(admin, 'DUMMY-DIV-SIN', {
    parentId: depOp.id, unitType: 'DIVISION', unitName: 'Biro Sinyal & Telekomunikasi (Dummy)', description: DUMMY_NOTE,
  });
  const divPgs = await ensureUnit(admin, 'DUMMY-DIV-PGS', {
    parentId: depSdm.id, unitType: 'DIVISION', unitName: 'Biro Pengembangan SDM (Dummy)', description: DUMMY_NOTE,
  });

  // 2. Tag dummy (kategori/jenis/klasifikasi memakai master data resmi V11).
  const tagOps = await ensureTag(admin, 'DUMMY-OPERASI', 'Dummy Operasi');
  const tagSdm = await ensureTag(admin, 'DUMMY-SDM', 'Dummy SDM');
  await ensureTag(admin, 'DUMMY-UJI', 'Dummy Uji Coba');

  const categories = await api(admin, 'GET', '/api/v1/master-data/categories');
  const types = await api(admin, 'GET', '/api/v1/master-data/document-types');
  const classifications = await api(admin, 'GET', '/api/v1/master-data/classifications');
  const category = categories.find((c) => c.active) ?? categories[0];
  const typeBy = (code) => {
    const found = types.find((t) => eq(t.typeCode, code));
    if (!found) throw new Error(`Document type ${code} tidak ditemukan di master data.`);
    return found.id;
  };
  const classBy = (code) => {
    const found = classifications.find((c) => eq(c.classificationCode, code));
    if (!found) throw new Error(`Classification ${code} tidak ditemukan di master data.`);
    return found.id;
  };

  // 3. Employee dummy + penempatan (placement dibuat otomatis oleh API).
  const employees = [
    { nip: 'DUMMY-0001', fullName: 'Budi Dummy Santoso', email: 'budi.dummy@example.com', division: divSar, username: 'dummy.budi' },
    { nip: 'DUMMY-0002', fullName: 'Sari Dummy Lestari', email: 'sari.dummy@example.com', division: divSin, username: 'dummy.sari' },
    { nip: 'DUMMY-0003', fullName: 'Agus Dummy Pratama', email: 'agus.dummy@example.com', division: divPgs, username: 'dummy.agus' },
    { nip: 'DUMMY-0004', fullName: 'Rina Dummy Wulandari', email: 'rina.dummy@example.com', division: divPgs, username: 'dummy.rina' },
    { nip: 'DUMMY-0005', fullName: 'Dedi Dummy Kurnia', email: 'dedi.dummy@example.com', division: divSar, username: null },
    { nip: 'DUMMY-0006', fullName: 'Maya Dummy Anggraini', email: 'maya.dummy@example.com', division: divSin, username: null },
  ];
  const accounts = {};
  for (const spec of employees) {
    const detail = await ensureEmployee(admin, spec.nip, {
      fullName: spec.fullName,
      email: spec.email,
      employmentStatus: 'ACTIVE',
      divisionUnitId: spec.division.id,
    });
    spec.employeeId = detail.employee.id;
    if (spec.username) {
      const account = await ensureAccount(admin, detail, spec.username);
      spec.userId = account.userId;
      accounts[spec.username] = spec;
    }
  }

  // 4. Finalkan password dummy, assignment unit + role/scoped.
  const sessions = {};
  for (const spec of Object.values(accounts)) {
    sessions[spec.username] = await ensureDummyLogin(spec.username);
    await ensureUnitAssignment(admin, spec.userId, spec.division.id);
    await ensureRoleAssignment(admin, spec.userId, {
      roleCode: 'EMPLOYEE', scopeType: 'ORGANIZATION_UNIT', organizationUnitId: spec.division.id,
    });
  }
  await ensureRoleAssignment(admin, accounts['dummy.budi'].userId, {
    roleCode: 'PIC', scopeType: 'ORGANIZATION_UNIT', organizationUnitId: dirOps.id, includeDescendants: true,
  });
  await ensureRoleAssignment(admin, accounts['dummy.sari'].userId, {
    roleCode: 'UNIT_MANAGER', scopeType: 'ORGANIZATION_UNIT', organizationUnitId: depOp.id, includeDescendants: true,
  });
  await ensureRoleAssignment(admin, accounts['dummy.rina'].userId, {
    roleCode: 'DOCUMENT_ADMIN', scopeType: 'GLOBAL',
  });

  // 5. Workspace + folder.
  const wsOps = await ensureWorkspace(admin, 'DUMMY-WS-OPS', {
    workspaceName: 'Workspace Operasi (Dummy)', ownerOrganizationUnitId: depOp.id, description: DUMMY_NOTE,
  });
  const wsSdm = await ensureWorkspace(admin, 'DUMMY-WS-SDM', {
    workspaceName: 'Workspace SDM (Dummy)', ownerOrganizationUnitId: depSdm.id, description: DUMMY_NOTE,
  });
  await ensureRoleAssignment(admin, accounts['dummy.agus'].userId, {
    roleCode: 'CONTRIBUTOR', scopeType: 'WORKSPACE', workspaceId: wsOps.id,
  });
  const folderSop = await ensureFolder(admin, wsOps.id, 'SOP & Instruksi Kerja (Dummy)');
  const folderLap = await ensureFolder(admin, wsOps.id, 'Laporan (Dummy)');
  const folderForm = await ensureFolder(admin, wsSdm.id, 'Formulir (Dummy)');

  // 6. Dokumen + versi + aktivasi.
  const doc1 = await ensureActive(admin, await ensureDocument(admin, wsOps.id, 'DUMMY-DOC-001', {
    title: 'SOP Perawatan Sarana (Dummy)',
    description: DUMMY_NOTE,
    folderId: folderSop.id,
    categoryId: category.id,
    documentTypeId: typeBy('SOP'),
    classificationId: classBy('INTERNAL'),
    ownerOrganizationUnitId: divSar.id,
    tagIds: [tagOps.id],
    effectiveDate: '2026-01-05',
    reviewDate: '2027-01-05',
  }));
  await ensureVersion(admin, doc1.summary.id, 1,
    makePdf('SOP Perawatan Sarana (Dummy)', 'Versi 1 - dokumen uji coba.'),
    'dummy-sop-sarana-v1.pdf', 'application/pdf', 'v1.0', 'Rilis awal dummy.');
  await ensureVersion(admin, doc1.summary.id, 2,
    makePdf('SOP Perawatan Sarana (Dummy)', 'Versi 2 - revisi jadwal perawatan.'),
    'dummy-sop-sarana-v2.pdf', 'application/pdf', 'v2.0', 'Revisi dummy: jadwal perawatan.');

  const doc2 = await ensureActive(admin, await ensureDocument(admin, wsOps.id, 'DUMMY-DOC-002', {
    title: 'Laporan Kinerja Operasi Q3 (Dummy)',
    description: DUMMY_NOTE,
    folderId: folderLap.id,
    categoryId: category.id,
    documentTypeId: typeBy('REPORT'),
    classificationId: classBy('CONFIDENTIAL'),
    ownerOrganizationUnitId: depOp.id,
    tagIds: [tagOps.id],
    effectiveDate: '2026-10-01',
  }));
  await ensureVersion(admin, doc2.summary.id, 1,
    makePdf('Laporan Kinerja Operasi Q3 (Dummy)', 'Laporan triwulan uji coba.'),
    'dummy-laporan-q3.pdf', 'application/pdf', 'v1.0', 'Rilis awal dummy.');

  const doc3 = await ensureActive(admin, await ensureDocument(admin, wsOps.id, 'DUMMY-DOC-003', {
    title: 'Instruksi Kerja Sinyal (Dummy)',
    description: DUMMY_NOTE,
    folderId: folderSop.id,
    categoryId: category.id,
    documentTypeId: typeBy('WORK_INSTRUCTION'),
    classificationId: classBy('INTERNAL'),
    ownerOrganizationUnitId: divSin.id,
    tagIds: [tagOps.id],
  }));
  await ensureVersion(admin, doc3.summary.id, 1,
    Buffer.from(`INSTRUKSI KERJA SINYAL (DUMMY)\n${DUMMY_NOTE}\nLangkah 1: periksa panel.\nLangkah 2: catat hasil.\n`, 'utf8'),
    'dummy-ik-sinyal.txt', 'text/plain', 'v1.0', 'Rilis awal dummy.');

  const doc4 = await ensureActive(admin, await ensureDocument(admin, wsSdm.id, 'DUMMY-DOC-004', {
    title: 'Formulir Cuti Karyawan (Dummy)',
    description: DUMMY_NOTE,
    folderId: folderForm.id,
    categoryId: category.id,
    documentTypeId: typeBy('FORM'),
    classificationId: classBy('INTERNAL'),
    ownerOrganizationUnitId: depSdm.id,
    tagIds: [tagSdm.id],
  }));
  await ensureVersion(admin, doc4.summary.id, 1,
    makePdf('Formulir Cuti Karyawan (Dummy)', 'Nama / NIP / Tanggal cuti / Alasan.'),
    'dummy-form-cuti.pdf', 'application/pdf', 'v1.0', 'Rilis awal dummy.');

  const doc5 = await ensureDocument(admin, wsSdm.id, 'DUMMY-DOC-005', {
    title: 'Memo Rapat Koordinasi (Dummy)',
    description: `${DUMMY_NOTE} Versi berupa tautan eksternal (Office/eksternal).`,
    categoryId: category.id,
    documentTypeId: typeBy('MEMO'),
    classificationId: classBy('INTERNAL'),
    ownerOrganizationUnitId: depSdm.id,
    tagIds: [tagSdm.id],
  });
  const doc5Versions = await api(admin, 'GET', `/api/v1/documents/${doc5.summary.id}/versions`);
  if (doc5Versions.length === 0) {
    log('versi external-link:', 'DUMMY-DOC-005');
    await api(admin, 'POST', `/api/v1/documents/${doc5.summary.id}/versions/external-link`, {
      url: 'https://example.com/dummy-memo-rapat',
      linkTitle: 'Memo Rapat (Dummy, tautan contoh)',
      versionLabel: 'v1.0',
      changeSummary: 'Tautan eksternal dummy.',
    });
  }
  await ensureActive(admin, await api(admin, 'GET', `/api/v1/documents/${doc5.summary.id}`));

  // Sengaja DRAFT untuk menguji state draft.
  await ensureDocument(admin, wsOps.id, 'DUMMY-DOC-006', {
    title: 'Kontrak Contoh Belum Final (Dummy)',
    description: `${DUMMY_NOTE} Sengaja berstatus DRAFT.`,
    categoryId: category.id,
    documentTypeId: typeBy('CONTRACT'),
    classificationId: classBy('RESTRICTED'),
    ownerOrganizationUnitId: depOp.id,
  });

  // 7. ACL contoh: allow unit + explicit deny user.
  await ensureGrant(admin, doc2.summary.id, {
    principalType: 'ORGANIZATION_UNIT', principalId: divSar.id,
    permissionCode: 'document.download', effect: 'ALLOW', includeDescendants: true,
  });
  await ensureGrant(admin, doc2.summary.id, {
    principalType: 'USER', principalId: accounts['dummy.rina'].userId,
    permissionCode: 'document.download', effect: 'DENY',
  });

  // 8. Template: satu published (+usage), satu draft.
  const tplCuti = await ensureTemplate(admin, 'DUMMY-TPL-CUTI', {
    documentId: doc4.summary.id,
    templateName: 'Template Formulir Cuti (Dummy)',
    description: DUMMY_NOTE,
  });
  if (tplCuti.summary.status === 'DRAFT') {
    log('template dipublikasikan:', 'DUMMY-TPL-CUTI');
    await api(admin, 'POST', `/api/v1/templates/${tplCuti.summary.id}/publish`, {});
    await api(admin, 'POST', `/api/v1/templates/${tplCuti.summary.id}/usage`, { usageType: 'VIEW' });
    await api(admin, 'POST', `/api/v1/templates/${tplCuti.summary.id}/usage`, { usageType: 'DOWNLOAD' });
  }
  await ensureTemplate(admin, 'DUMMY-TPL-SOP', {
    documentId: doc1.summary.id,
    templateName: 'Template SOP Perawatan (Dummy, draft)',
    description: `${DUMMY_NOTE} Sengaja berstatus DRAFT.`,
  });

  // 9. Request workflow: fulfilled, fulfilled, rejected, submitted.
  const r1 = await ensureRequest(sessions['dummy.sari'], 'own', '[DUMMY] Akses laporan kinerja Q3', {
    requestType: 'ACCESS',
    title: '[DUMMY] Akses laporan kinerja Q3',
    reason: 'Keperluan uji coba alur permintaan akses dokumen confidential.',
    targetDocumentId: doc2.summary.id,
    neededByDate: '2026-12-01',
  });
  if (r1.summary.status === 'SUBMITTED') {
    await api(admin, 'POST', `/api/v1/requests/${r1.summary.id}/start-review`, { comment: 'Review dummy.' });
    await api(admin, 'POST', `/api/v1/requests/${r1.summary.id}/approve`, { comment: 'Disetujui untuk uji coba.' });
    await api(admin, 'POST', `/api/v1/requests/${r1.summary.id}/fulfill`, {
      fulfillmentType: 'ACCESS_GRANTED', note: 'Grant akses dummy.',
    });
    log('request fulfilled (ACCESS_GRANTED):', r1.summary.requestCode);
  }

  const r2 = await ensureRequest(sessions['dummy.agus'], 'own', '[DUMMY] Minta salinan SOP perawatan', {
    requestType: 'DOCUMENT_DELIVERY',
    title: '[DUMMY] Minta salinan SOP perawatan',
    reason: 'Uji coba pemenuhan berupa dokumen tertaut.',
    targetOrganizationUnitId: depOp.id,
  });
  if (r2.summary.status === 'SUBMITTED') {
    await api(admin, 'POST', `/api/v1/requests/${r2.summary.id}/start-review`, {});
    await api(admin, 'POST', `/api/v1/requests/${r2.summary.id}/approve`, { comment: 'OK dummy.' });
    await api(admin, 'POST', `/api/v1/requests/${r2.summary.id}/fulfill`, {
      fulfillmentType: 'DOCUMENT_LINKED', documentIds: [doc1.summary.id], note: 'SOP dummy dilampirkan.',
    });
    log('request fulfilled (DOCUMENT_LINKED):', r2.summary.requestCode);
  }

  const r3 = await ensureRequest(sessions['dummy.rina'], 'own', '[DUMMY] Akses instruksi kerja sinyal', {
    requestType: 'ACCESS',
    title: '[DUMMY] Akses instruksi kerja sinyal',
    reason: 'Uji coba alur penolakan request.',
    targetDocumentId: doc3.summary.id,
  });
  if (r3.summary.status === 'SUBMITTED') {
    await api(admin, 'POST', `/api/v1/requests/${r3.summary.id}/start-review`, {});
    await api(admin, 'POST', `/api/v1/requests/${r3.summary.id}/reject`, { comment: 'Ditolak: contoh alur reject dummy.' });
    log('request rejected:', r3.summary.requestCode);
  }

  await ensureRequest(sessions['dummy.agus'], 'own', '[DUMMY] Akses SOP perawatan (menunggu)', {
    requestType: 'ACCESS',
    title: '[DUMMY] Akses SOP perawatan (menunggu)',
    reason: 'Uji coba state SUBMITTED yang menunggu review.',
    targetDocumentId: doc1.summary.id,
  });

  // 10. Distribusi: satu aktif (user + unit), satu dicabut.
  const expires = new Date(Date.now() + 30 * 24 * 3600 * 1000).toISOString();
  await ensureDistribution(admin, '[DUMMY] Edaran SOP & IK operasi', {
    title: '[DUMMY] Edaran SOP & IK operasi',
    message: DUMMY_NOTE,
    expiresAt: expires,
    recipientUserIds: [accounts['dummy.budi'].userId, accounts['dummy.sari'].userId],
    recipientOrganizationUnitIds: [divSin.id],
    items: [
      { documentId: doc1.summary.id, accessLevel: 'DOWNLOAD' },
      { documentId: doc3.summary.id, accessLevel: 'VIEW' },
    ],
  });
  const d2 = await ensureDistribution(admin, '[DUMMY] Formulir cuti (akan dicabut)', {
    title: '[DUMMY] Formulir cuti (akan dicabut)',
    message: `${DUMMY_NOTE} Contoh distribusi yang dicabut.`,
    recipientUserIds: [accounts['dummy.agus'].userId],
    items: [{ documentId: doc4.summary.id, accessLevel: 'PREVIEW' }],
  });
  if (d2.summary.status === 'SENT' || d2.summary.status === 'ACTIVE') {
    log('distribusi dicabut:', d2.summary.distributionCode);
    await api(admin, 'POST', `/api/v1/distributions/${d2.summary.id}/revoke`, {});
  }

  // 11. Ringkasan verifikasi.
  const counts = {
    unitDummy: (await api(admin, 'GET', '/api/v1/admin/organization-units')).filter((u) => u.unitCode.startsWith('DUMMY-')).length,
    employeeDummy: (await api(admin, 'GET', '/api/v1/admin/employees?keyword=DUMMY-&page=0&size=50')).totalItems,
    userDummy: (await api(admin, 'GET', '/api/v1/admin/users?keyword=dummy.&page=0&size=50')).totalItems,
    workspaceDummy: (await api(admin, 'GET', '/api/v1/workspaces')).filter((w) => w.workspaceCode.startsWith('DUMMY-')).length,
    dokumenDummy: (await api(admin, 'GET', '/api/v1/documents?keyword=DUMMY-DOC&page=0&size=50')).totalItems,
    templateDummy:
      (await api(admin, 'GET', '/api/v1/templates?keyword=Dummy&status=PUBLISHED&page=0&size=50')).totalItems +
      (await api(admin, 'GET', '/api/v1/templates?keyword=Dummy&status=DRAFT&page=0&size=50')).totalItems,
    requestDummy: (await api(admin, 'GET', '/api/v1/requests?scope=assigned&keyword=%5BDUMMY%5D&page=0&size=50')).totalItems,
    distribusiDummy: (await api(admin, 'GET', '/api/v1/distributions?box=sent&keyword=%5BDUMMY%5D&page=0&size=50')).totalItems,
  };
  log('SELESAI. Ringkasan:', JSON.stringify(counts, null, 2));
  log(`Kredensial akun dummy (username dummy.budi/sari/agus/rina) tersimpan di ${CRED_FILE}`);
}

main().catch((err) => {
  console.error('[seed] GAGAL:', err.message);
  process.exit(1);
});