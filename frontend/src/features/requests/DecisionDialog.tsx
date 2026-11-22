import { useState } from 'react';
import { Alert } from '../../shared/components/Feedback';
import { Button } from '../../shared/components/Button';
import { Dialog } from '../../shared/components/Dialog';
import { TextareaField } from '../../shared/components/Field';
import { apiMessage } from '../../shared/utils/apiMessage';

interface DecisionDialogProps {
  open: boolean;
  title: string;
  description?: string;
  confirmLabel: string;
  requireComment?: boolean;
  tone?: 'primary' | 'danger';
  onClose: () => void;
  onSubmit: (comment: string) => Promise<void>;
}

export function DecisionDialog({
  open,
  title,
  description,
  confirmLabel,
  requireComment = false,
  tone = 'primary',
  onClose,
  onSubmit,
}: DecisionDialogProps) {
  const [comment, setComment] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [fieldError, setFieldError] = useState<string | undefined>();
  const [pending, setPending] = useState(false);

  async function submit() {
    if (requireComment && !comment.trim()) {
      setFieldError('Alasan wajib diisi.');
      return;
    }
    setPending(true);
    setError(null);
    setFieldError(undefined);
    try {
      await onSubmit(comment.trim());
    } catch (caught) {
      setError(apiMessage(caught));
      setPending(false);
    }
  }

  return (
    <Dialog
      open={open}
      title={title}
      description={description}
      onClose={() => {
        if (!pending) {
          onClose();
        }
      }}
      size="sm"
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={pending}>
            Batal
          </Button>
          <Button type="submit" form="decision-form" variant={tone === 'danger' ? 'danger' : 'primary'} loading={pending}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      <form
        id="decision-form"
        className="space-y-3"
        onSubmit={(event) => {
          event.preventDefault();
          void submit();
        }}
      >
        {error ? <Alert>{error}</Alert> : null}
        <TextareaField
          label="Catatan"
          required={requireComment}
          rows={3}
          maxLength={2000}
          value={comment}
          error={fieldError}
          helper={requireComment ? undefined : 'Catatan akan tercatat pada riwayat request.'}
          onChange={(event) => setComment(event.target.value)}
        />
      </form>
    </Dialog>
  );
}
