import { PageHeader } from '../../shared/components/Surface';
import { MasterDataPanel } from './MasterDataPanel';
import { positionConfig } from './positionConfig';

export function PositionsPage() {
  return (
    <>
      <PageHeader
        title="Jabatan"
        description="Katalog jabatan (position) untuk data employee. Jabatan adalah atribut kepegawaian, bukan tingkat unit organisasi."
      />
      <MasterDataPanel config={positionConfig} />
    </>
  );
}