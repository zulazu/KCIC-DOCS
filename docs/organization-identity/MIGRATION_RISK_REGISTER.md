# KCIC Connect — Migration Risk Register (Organization & Identity)

| Metadata | Nilai |
|---|---|
| Dokumen | Migration Risk Register — E-series |
| Versi | 1.0 |
| Tanggal | 22 November 2026 |
| Status | Perencanaan; tidak ada migration dijalankan pada E0 |
| Prinsip | Additive, no destructive reset, no fabricated NIP/org, no automatic privilege escalation |

## 1. Aturan Migrasi

1. Migration **V1–V13 immutable**. Semua perubahan baru memakai V14+.
2. Tidak ada `DROP`, `TRUNCATE`, rename kolom, atau reset database pada E-series awal.
3. Penambahan kolom dilakukan nullable atau dengan default aman; enforcement (`NOT NULL`) ditunda sampai data terverifikasi.
4. Backfill data nyata hanya dari sumber HR resmi; tidak ada NIP/unit/email sintetis pada environment bersama.
5. Setiap migration harus lulus pada database kosong dan upgrade dari V13 (Testcontainers/Flyway).
6. Backup penuh database sebelum cutover; restore drill mengikuti `DEPLOYMENT.md §6`.
7. Grant ACL/role lama tidak boleh berubah cakupan. Flag descendant default `false`.
8. Tidak ada perubahan pada MinIO/storage, distribution snapshot, atau workflow state machine pada migration ini.

## 2. Staging Migration yang Direncanakan

| Versi | File (rencana) | Isi | Data backfill |
|---|---|---|---|
| V14 | `V14__organization_unit_type.sql` | `organization_units.unit_type` nullable + CHECK; index `(unit_type, unit_status)`; tidak ada NOT NULL | Tidak ada; mapping unit oleh business owner |
| V15 | `V15__employee_master.sql` | `identity.employees`; `organization.employee_placements`; unique NIP/email; partial unique active placement; FK/CHECK/index | Tidak ada; Employee dari HR |
| V16 | `V16__user_employee_link.sql` | `users.employee_id UUID NULL UNIQUE` FK; permission `employee.manage` + grant SYSTEM_ADMIN | Tidak ada; link diverifikasi manual/script |
| V17 | `V17__descendant_scope_flag.sql` | `resource_permissions.include_descendants BOOLEAN NOT NULL DEFAULT FALSE`; `user_role_assignments.include_descendants BOOLEAN NOT NULL DEFAULT FALSE`; CHECK flag hanya untuk principal/scope unit | Semua row lama otomatis false |
| V18 | `V18__enforce_employee_identity.sql` | `users.employee_id NOT NULL`; `organization_units.unit_type NOT NULL`; constraint final | Dijalankan hanya setelah gate data PASS |
| V19 (opsional) | `V19__retire_legacy_unit_memberships.sql` | Menghentikan penggunaan tabel assignment lama; drop hanya dengan persetujuan eksplisit | Setelah tidak ada pembaca |

Catatan: V18 adalah **cutover gate**, bukan bagian dari deploy awal. Jika data belum lengkap, V18 tidak boleh diterapkan.

## 3. Register Risiko

| ID | Risiko | Pemicu | Dampak | Likelihood | Severity | Mitigasi | Deteksi | Phase |
|---|---|---|---|---|---|---|---|---|
| R-01 | Grant lama otomatis lebih luas | Descendant diaktifkan default/global | Cross-unit/all-document access; pelanggaran least privilege | Medium | **Critical** | `include_descendants=false` default; opt-in per grant; regression test legacy exact | Test + query grant lama tetap false | E4/E6 |
| R-02 | NIP/unit/placement fiktif | Agent/operator mengisi data placeholder | Data HR palsu, audit tidak dapat dipercaya | Medium | **Critical** | Hanya impor dari HR; staging review; larangan seed unit nyata | Review data source + checksum import | E2/E6 |
| R-03 | Duplicate/blank NIP atau email | Data HR kotor | Migration gagal atau identitas ambigu | High | **High** | Preflight duplicate/blank; normalisasi email; staging report | Preflight query | E2/E6 |
| R-04 | Akun lama kehilangan akses | `employee_id NOT NULL` sebelum backfill | Login/admin lumpuh | Medium | **Critical** | V18 hanya setelah 100% link; feature flag; backup | Gate query + smoke login | E3/E6 |
| R-05 | Konflik email User vs Employee | Email User berbeda dengan HR | Login by email berubah; duplikasi data | High | **High** | Mapping eksplisit; Employee email authoritative; update login lookup setelah backfill | Reconciliation report | E3/E6 |
| R-06 | Multiple active placement | Data lama SECONDARY/PRIMARY | Partial unique index gagal; Employee punya dua unit | High | **High** | Preflight; pilih placement resmi per Employee; tutup sisanya dengan alasan | Query count >1 | E2/E6 |
| R-07 | Unit bertipe salah | `KCIC-HO`/`KCIC-OPS` ditebak | Hierarchy dan akses salah | High | **High** | Jangan tebak; business owner memetakan; unit untyped tetap sampai keputusan | Review mapping sign-off | E1/E6 |
| R-08 | Document owner unit non-Biro/Division | Dokumen dimiliki unit generik | Descendant semantics tidak relevan/salah | Medium | **Medium** | Jangan ubah owner; flag false; evaluasi exact tetap; dokumentasikan | Inventory owner unit | E4/E6 |
| R-09 | Role scope berubah diam-diam | Flag role assignment disamakan dengan ACL | User unit-scope mendapat akses descendant | Medium | **High** | Flag terpisah di `user_role_assignments`; default false; test | Authorization regression | E4 |
| R-10 | Nama audit berubah | Enrichment pindah dari `users.display_name` ke Employee | Nama aktor historis berbeda/tidak ditemukan | Medium | **Low/Medium** | Fallback ke snapshot/username; audit lama tidak diubah | Audit integration test | E3 |
| R-11 | Distribution snapshot rusak | User di-merge/ diganti | Recipient/grant historis hilang | Low | **High** | User id stabil; tidak ada merge; employee link additive | Recipient count + regression test | E3/E7 |
| R-12 | API create user berubah breaking | Employee-first create mengganti kontrak | Integrasi/UI lama gagal | Medium | **Medium** | Endpoint lama dipertahankan; endpoint baru additive; deprecation window | Contract tests | E3/E5 |
| R-13 | Frontend cache/route lama | User membuka URL lama setelah deploy | Error/empty state | Medium | **Low** | Redirect/kompatibilitas route; invalidasi query; E2E | Browser UAT | E5/E7 |
| R-14 | Bootstrap/service account tanpa Employee | Admin awal belum punya NIP | Gate 1:1 gagal | High | **High** | Baseline: wajib link sebelum go-live; pengecualian service account harus diputuskan formal | Gate query | E3/E6 |
| R-15 | Data uji sintetis bocor ke shared | Fixture Testcontainers dipakai ulang | Data palsu di environment bersama | Low | **High** | Fixture hanya di Testcontainers; import produksi terpisah | Environment review | E2/E6 |
| R-16 | Migration lama diedit | Perbaikan dilakukan di V1–V13 | Checksum Flyway rusak | Low | **Critical** | Review diff; CI checksum; migration baru | Flyway validate | Semua |
| R-17 | Rollback setelah enforcement | V18 diterapkan lalu data bermasalah | Tidak bisa kembali tanpa drop constraint | Medium | **High** | Backup; V18 terpisah; rollback plan drop NOT NULL + kode sebelumnya | Rehearsal | E6 |
| R-18 | Routing request tidak sesuai harapan | Manager Directorate diharapkan menangani Biro/Division | Request salah approver | Medium | **Medium** | Baseline exact; keputusan routing terpisah; dokumentasi | Workflow UAT | E4/E7 |
| R-19 | Performa lookup ancestor | Recursive query per row pada listing | Catalog melambat | Medium | **Medium** | Resolve sekali per request; depth ≤4; index parent; EXPLAIN | Load test pilot | E4 |
| R-20 | Unit inactive masih direferensikan | Nonaktifkan unit beranggota | Placement aktif menunjuk unit nonaktif | Medium | **Medium** | Tolak placement baru; kebijakan existing; audit | Query unit inactive + active placement | E1/E2 |
| R-21 | Perubahan tipe unit merusak hierarchy | Ubah Directorate menjadi Biro/Division saat punya child | Child tipe tidak sah | Medium | **High** | Validasi tipe anak; blokir perubahan jika melanggar; test | Domain tests | E1 |
| R-22 | NIP format salah | Format belum disepakati | Validasi terlalu ketat/longgar | Medium | **Medium** | Baseline `VARCHAR(30)` + trim; format final menunggu HR; jangan hardcode regex sempit | Data review | E2 |

## 4. Data Readiness Checklist (Blocker)

| # | Item | Sumber | Gate |
|---|---|---|---|
| 1 | Daftar NIP, full name, email, employment status untuk seluruh Employee | HR resmi | 100% baris valid, tanpa duplikat |
| 2 | Mapping setiap User lama → NIP/Employee | HR + IT | `count(users WHERE employee_id IS NULL)=0` |
| 3 | Struktur KCIC/Directorate/Department/Biro/Division resmi | Business owner | Semua unit aktif memiliki tipe sah; tepat satu COMPANY |
| 4 | Biro/Division untuk setiap Employee | HR | Setiap Employee tepat satu active placement |
| 5 | Keputusan service/bootstrap account | Product/IT | Pengecualian tertulis atau link Employee |
| 6 | Keputusan email login | Product/IT | Lookup email tetap berfungsi atau diganti username |
| 7 | Keputusan default descendant grant baru | Product/Security | Default false disetujui atau alasan perubahan |

## 5. Preflight SQL (read-only)

```sql
-- 1. Users tanpa Employee (target 0 sebelum V18)
SELECT count(*) FROM identity.users WHERE employee_id IS NULL;

-- 2. Employee tanpa tepat satu active placement (setelah import)
SELECT e.id, e.nip, count(p.id) AS active_placements
FROM identity.employees e
LEFT JOIN organization.employee_placements p
  ON p.employee_id = e.id AND p.is_active
GROUP BY e.id, e.nip
HAVING count(p.id) <> 1;

-- 3. Duplicate NIP/email calon import
SELECT nip, count(*) FROM identity.employees GROUP BY nip HAVING count(*) > 1;
SELECT lower(email), count(*) FROM identity.employees GROUP BY lower(email) HAVING count(*) > 1;

-- 4. Unit aktif tanpa tipe / parent tidak sah (sebelum V18)
SELECT id, unit_code, unit_type, parent_id
FROM organization.organization_units
WHERE unit_status = 'ACTIVE'
  AND (unit_type IS NULL OR (unit_type <> 'COMPANY' AND parent_id IS NULL));

-- 5. Placement aktif pada unit non-DIVISION/nonaktif
SELECT p.id, p.employee_id, u.unit_code, u.unit_type, u.unit_status
FROM organization.employee_placements p
JOIN organization.organization_units u ON u.id = p.division_unit_id
WHERE p.is_active AND (u.unit_type <> 'DIVISION' OR u.unit_status <> 'ACTIVE');

-- 6. Legacy grants/role assignments yang tidak boleh otomatis melebar
SELECT count(*) FROM "authorization".resource_permissions WHERE include_descendants;
SELECT count(*) FROM "authorization".user_role_assignments WHERE include_descendants;
-- Target setelah V17: keduanya 0
```

## 6. Dry-Run dan Verifikasi Migration

1. Jalankan Flyway pada database kosong sampai V18; pastikan sukses.
2. Restore salinan database produksi ke environment staging (bukan produksi) dan jalankan V14–V17.
3. Impor data HR ke staging; jalankan preflight §5 sampai bersih.
4. Jalankan V18 pada staging; ukur waktu dan lock.
5. Smoke test: login semua akun, katalog dokumen, ACL allow/deny, request/approval, distribution, template, audit.
6. Bandingkan count sebelum/sesudah: users, roles, assignments, grants, documents, versions, recipients, audit.
7. Rehearse rollback (drop NOT NULL/constraint, restore backup) pada salinan.

## 7. Cutover Sequence

```text
1. Freeze admin writes; announce maintenance window.
2. Backup database + storage metadata; record checksums/counts.
3. Deploy application version yang kompatibel dengan schema nullable (E1–E3 features off).
4. Apply V14–V17.
5. Import Employee/placement/unit mapping dari HR (idempotent script, staging review).
6. Run preflight queries; resolve every exception; capture report.
7. Enable Employee/descendant features behind config flag; smoke test.
8. Apply V18 (NOT NULL enforcement) after gate PASS.
9. Post-cutover verification + audit review.
10. Remove flag only after UAT sign-off.
```

Tidak ada langkah yang boleh menggabungkan backup, import, dan enforcement dalam satu transaksi tanpa checkpoint.

## 8. Rollback Plan

| Tahap | Tindakan | Catatan |
|---|---|---|
| Sebelum V18 | Hentikan app baru, restore backup bila data import salah; V14–V17 additive dan dapat ditinggalkan | Tidak ada kehilangan data lama |
| Setelah V18 | Rollback code ke versi sebelumnya; drop `NOT NULL` pada `users.employee_id`/`organization_units.unit_type` bila perlu | Data tetap; butuh jendela maintenance |
| Setelah UI baru | Matikan feature flag; route lama tetap tersedia | Jangan hapus data Employee/placement |
| Data import salah | Hapus hanya baris import yang teridentifikasi (dengan approval); restore backup jika ambigu | Tidak menggunakan reset database |

## 9. Post-Migration Acceptance

- [ ] Semua migration sukses di DB kosong dan upgrade V13→V18.
- [ ] `users.employee_id` unique dan (setelah V18) NOT NULL.
- [ ] Satu active placement per Employee; history tetap ada.
- [ ] NIP/email unique; tidak ada data sintetis di shared environment.
- [ ] Typed hierarchy valid; satu COMPANY root.
- [ ] Legacy grant/role `include_descendants=false`; tidak ada widening.
- [ ] Descendant ALLOW/DENY test lulus; DENY tetap menang.
- [ ] Login, katalog, request, distribution, template, audit smoke test lulus.
- [ ] Backup/restore drill ulang setelah perubahan schema.
- [ ] UAT bisnis menerima data Employee/organization.
