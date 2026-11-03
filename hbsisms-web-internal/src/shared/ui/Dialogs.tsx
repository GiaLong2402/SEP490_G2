import { useEffect, useRef, useState, type FormEvent, type ReactNode, type SyntheticEvent } from 'react';
import { X } from 'lucide-react';
import { Button, IconButton } from './Button';
import { Field, Textarea } from './Form';

function useNativeDialog(open: boolean) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);
  return ref;
}

// Chỉ nghe sự kiện cancel (phím Esc): sự kiện close đến sau một nhịp nên có thể đóng nhầm dialog vừa được mở lại.
const closeOnEsc = (onClose: () => void) => (event: SyntheticEvent) => {
  event.preventDefault();
  onClose();
};

interface ModalProps {
  open: boolean;
  title: ReactNode;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
  width?: 'md' | 'lg' | 'xl';
  /** Khi có, toàn bộ modal là một form và Enter sẽ gửi form. */
  onSubmit?: () => void;
}

// <dialog> gốc đã có bẫy focus, phím Esc và trả focus về nơi mở.
export function Modal({ open, title, onClose, children, footer, width = 'md', onSubmit }: ModalProps) {
  const ref = useNativeDialog(open);
  const w = { md: 'max-w-[480px]', lg: 'max-w-[680px]', xl: 'max-w-[920px]' }[width];
  const body = (
    <>
      <header className="flex items-center justify-between border-b border-line px-4 py-3">
        <h2 className="text-h1 text-ink">{title}</h2>
        <IconButton label="Đóng" onClick={onClose}>
          <X size={16} />
        </IconButton>
      </header>
      <div className="max-h-[70vh] overflow-y-auto p-4">{open && children}</div>
      {footer && <footer className="flex justify-end gap-2 border-t border-line px-4 py-3">{footer}</footer>}
    </>
  );
  return (
    <dialog ref={ref} onCancel={closeOnEsc(onClose)} className={`w-[calc(100vw-32px)] ${w} rounded-lg bg-card p-0 text-ink shadow-pop`}>
      {onSubmit ? (
        <form
          noValidate
          onSubmit={(event: FormEvent) => {
            event.preventDefault();
            onSubmit();
          }}
        >
          {body}
        </form>
      ) : (
        body
      )}
    </dialog>
  );
}

export function Drawer({ open, title, onClose, children, width = 560 }: { open: boolean; title: ReactNode; onClose: () => void; children: ReactNode; width?: number }) {
  const ref = useNativeDialog(open);
  return (
    <dialog
      ref={ref}
      onCancel={closeOnEsc(onClose)}
      style={{ width: `min(${width}px, 100vw)` }}
      className="m-0 ml-auto h-full max-h-none max-w-none bg-card p-0 text-ink shadow-pop"
    >
      <div className="flex h-full flex-col">
        <header className="flex items-center justify-between border-b border-line px-4 py-3">
          <h2 className="text-h1 text-ink">{title}</h2>
          <IconButton label="Đóng" onClick={onClose}>
            <X size={16} />
          </IconButton>
        </header>
        <div className="flex-1 overflow-y-auto p-4">{open && children}</div>
      </div>
    </dialog>
  );
}

interface ConfirmProps {
  open: boolean;
  title: string;
  message?: ReactNode;
  confirmLabel?: string;
  danger?: boolean;
  loading?: boolean;
  /** Bật ô nhập ghi chú, giá trị truyền vào onConfirm. */
  withNote?: boolean;
  noteLabel?: string;
  noteRequired?: boolean;
  onConfirm: (note: string) => void;
  onClose: () => void;
}

export function ConfirmDialog({ open, title, message, confirmLabel = 'Xác nhận', danger, loading, withNote, noteLabel = 'Ghi chú', noteRequired, onConfirm, onClose }: ConfirmProps) {
  const [note, setNote] = useState('');
  const [touched, setTouched] = useState(false);
  useEffect(() => {
    if (open) {
      setNote('');
      setTouched(false);
    }
  }, [open]);
  const missing = withNote && noteRequired && !note.trim();
  return (
    <Modal
      open={open}
      title={title}
      onClose={onClose}
      onSubmit={() => {
        setTouched(true);
        if (!missing) onConfirm(note.trim());
      }}
      footer={
        <>
          <Button onClick={onClose}>Hủy bỏ</Button>
          <Button type="submit" variant={danger ? 'danger' : 'primary'} loading={loading}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      {message && <div className="text-body text-ink-muted">{message}</div>}
      {withNote && (
        <Field label={noteLabel} required={noteRequired} error={touched && missing ? 'Vui lòng nhập nội dung' : undefined} className={message ? 'mt-3' : ''}>
          <Textarea value={note} onChange={(e) => setNote(e.target.value)} />
        </Field>
      )}
    </Modal>
  );
}
