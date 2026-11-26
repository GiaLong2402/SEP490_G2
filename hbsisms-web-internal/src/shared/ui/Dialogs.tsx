import { useEffect, useRef, useState, type FormEvent, type ReactNode, type RefObject, type SyntheticEvent } from 'react';
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

/**
 * Chromium/Edge: hủy hộp thoại chọn file (Esc hoặc nút X) bên trong `<dialog>`
 * cũng bắn sự kiện `cancel` lên dialog → đóng luôn modal. Chặn khi vừa mở file picker.
 */
function useIgnoreFilePickerCancel(dialogRef: RefObject<HTMLDialogElement | null>, open: boolean) {
  const filePickerOpenRef = useRef(false);

  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog || !open) return;

    const markFilePicker = (event: Event) => {
      const target = event.target as HTMLElement | null;
      if (target?.closest?.('input[type="file"]')) {
        filePickerOpenRef.current = true;
      }
    };

    const onWindowFocus = () => {
      if (!filePickerOpenRef.current) return;
      // Đợi cancel (nếu có) xử lý xong rồi mới cho Esc đóng modal trở lại.
      window.setTimeout(() => {
        filePickerOpenRef.current = false;
      }, 300);
    };

    dialog.addEventListener('click', markFilePicker, true);
    window.addEventListener('focus', onWindowFocus);
    return () => {
      dialog.removeEventListener('click', markFilePicker, true);
      window.removeEventListener('focus', onWindowFocus);
    };
  }, [dialogRef, open]);

  return filePickerOpenRef;
}

// Chỉ nghe sự kiện cancel (phím Esc): sự kiện close đến sau một nhịp nên có thể đóng nhầm dialog vừa được mở lại.
const closeOnEsc =
  (onClose: () => void, ignoreRef?: RefObject<boolean>) => (event: SyntheticEvent) => {
    event.preventDefault();
    if (ignoreRef?.current) return;
    onClose();
  };

/** Cuộn tới và đặt con trỏ vào ô đầu tiên đang báo lỗi (Field gắn aria-invalid cho ô lỗi). */
function focusFirstInvalid(root: HTMLElement) {
  const el = root.querySelector<HTMLElement>('[aria-invalid="true"]');
  if (!el) return;
  el.scrollIntoView({ block: 'center', behavior: 'smooth' });
  el.focus({ preventScroll: true });
}

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
  const ignoreFilePickerCancel = useIgnoreFilePickerCancel(ref, open);
  const w = { md: 'max-w-[480px]', lg: 'max-w-[680px]', xl: 'max-w-[920px]' }[width];
  // Header + footer cố định, chỉ phần thân cuộn → luôn đúng 1 thanh cuộn dù form dài (vd. phiếu nhập nhiều mặt hàng).
  const frame = 'flex max-h-[calc(100dvh-32px)] flex-col';
  const body = (
    <>
      <header className="flex shrink-0 items-center justify-between border-b border-line px-4 py-3">
        <h2 className="text-h1 text-ink">{title}</h2>
        <IconButton label="Đóng" onClick={onClose}>
          <X size={16} />
        </IconButton>
      </header>
      <div className="max-h-[70vh] min-h-0 flex-1 overflow-y-auto p-4">{open && children}</div>
      {footer && <footer className="flex shrink-0 justify-end gap-2 border-t border-line px-4 py-3">{footer}</footer>}
    </>
  );
  return (
    <dialog
      ref={ref}
      onCancel={closeOnEsc(onClose, ignoreFilePickerCancel)}
      className={`max-h-[calc(100dvh-32px)] w-[calc(100vw-32px)] overflow-hidden ${w} rounded-lg bg-card p-0 text-ink shadow-pop`}
    >
      {onSubmit ? (
        <form
          noValidate
          className={frame}
          onSubmit={(event: FormEvent<HTMLFormElement>) => {
            event.preventDefault();
            const form = event.currentTarget;
            onSubmit();
            // Lỗi nằm ở phần đã cuộn qua (vd. quên Nguồn hàng khi đang ở mặt hàng cuối) → tự cuộn tới ô lỗi đầu tiên.
            requestAnimationFrame(() => focusFirstInvalid(form));
          }}
        >
          {body}
        </form>
      ) : (
        <div className={frame}>{body}</div>
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
        const reason = note.trim();
        if (withNote && noteRequired && !reason) return;
        onConfirm(reason);
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
          <Textarea value={note} onChange={(e) => setNote(e.target.value)} placeholder={noteRequired ? 'Nhập lý do…' : undefined} />
        </Field>
      )}
    </Modal>
  );
}
