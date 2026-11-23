import { cloneElement, useEffect, useId, useRef, useState, type CSSProperties, type InputHTMLAttributes, type ReactElement, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react';
import { CalendarDays, Info } from 'lucide-react';

const TIP_MAX_WIDTH = 320;

/**
 * Biểu tượng (i): rê chuột hoặc focus để xem ghi chú. Bong bóng dùng vị trí fixed nên không bị cắt
 * trong modal (vùng cuộn) hay bảng cuộn ngang; tự lật lên trên khi gần đáy màn hình.
 */
export function InfoTip({ children, label = 'Xem ghi chú' }: { children: ReactNode; label?: string }) {
  const tipId = useId();
  const ref = useRef<HTMLButtonElement>(null);
  const [pos, setPos] = useState<CSSProperties | null>(null);
  const show = () => {
    const r = ref.current?.getBoundingClientRect();
    if (!r) return;
    const left = Math.max(8, Math.min(r.left - 10, window.innerWidth - TIP_MAX_WIDTH - 8));
    setPos(window.innerHeight - r.bottom > 150 ? { left, top: r.bottom + 6 } : { left, bottom: window.innerHeight - r.top + 6 });
  };
  const hide = () => setPos(null);
  return (
    <span className="inline-flex align-middle">
      <button
        ref={ref}
        type="button"
        aria-label={label}
        aria-describedby={tipId}
        onMouseEnter={show}
        onMouseLeave={hide}
        onFocus={show}
        onBlur={hide}
        onKeyDown={(e) => {
          // Esc đóng bong bóng trước, không đóng luôn modal chứa nó.
          if (e.key === 'Escape' && pos) {
            e.preventDefault();
            e.stopPropagation();
            hide();
          }
        }}
        className="inline-flex h-4 w-4 cursor-help items-center justify-center rounded-full text-ink-faint transition-colors hover:text-primary focus-visible:text-primary"
      >
        <Info size={14} aria-hidden />
      </button>
      <span id={tipId} className="sr-only">{children}</span>
      {pos && (
        <span aria-hidden style={{ ...pos, maxWidth: TIP_MAX_WIDTH }} className="pointer-events-none fixed z-50 w-max space-y-1 whitespace-normal rounded-md bg-ink px-3 py-2.5 text-left text-body font-normal normal-case leading-5 tracking-normal text-card shadow-pop">
          {children}
        </span>
      )}
    </span>
  );
}

interface FieldProps {
  label: string;
  error?: string;
  hint?: ReactNode;
  /** Ghi chú phụ — hiện dạng biểu tượng (i) cạnh nhãn, rê chuột để xem (thay vì dòng chữ dưới ô). */
  tip?: ReactNode;
  required?: boolean;
  className?: string;
  children: ReactElement;
}

/** Gắn nhãn, lỗi và aria-describedby cho một control. */
export function Field({ label, error, hint, tip, required, className = '', children }: FieldProps) {
  const id = useId();
  const messageId = `${id}-msg`;
  const labelEl = (
    <label htmlFor={id} className={`${tip ? '' : 'mb-1 '}block text-small font-medium text-ink-muted`}>
      {label}
      {required && <span className="text-red-fg"> *</span>}
    </label>
  );
  return (
    <div className={className}>
      {tip ? (
        <div className="mb-1 flex items-center gap-1">
          {labelEl}
          <InfoTip label={`Ghi chú: ${label}`}>{tip}</InfoTip>
        </div>
      ) : (
        labelEl
      )}
      {cloneElement(children, { id, 'aria-invalid': !!error || undefined, 'aria-describedby': error || hint ? messageId : undefined })}
      {/* Giữ hint khi có lỗi: hint có thể là nút hành động, ví dụ "Thêm nhà cung cấp mới". */}
      {(error || hint) && (
        <div id={messageId} className="mt-1 text-small">
          {error && <p className="text-red-fg">{error}</p>}
          {hint && <div className="text-ink-faint">{hint}</div>}
        </div>
      )}
    </div>
  );
}

export const Input = ({ className = '', ...rest }: InputHTMLAttributes<HTMLInputElement>) => <input className={`field-control ${className}`} {...rest} />;

const isoToVn = (iso: string) => (/^\d{4}-\d{2}-\d{2}$/.test(iso) ? `${iso.slice(8, 10)}/${iso.slice(5, 7)}/${iso.slice(0, 4)}` : '');
function vnToIso(text: string): string | null {
  const m = /^(\d{2})\/(\d{2})\/(\d{4})$/.exec(text);
  if (!m) return null;
  const [, dd, mm, yyyy] = m;
  const d = new Date(Number(yyyy), Number(mm) - 1, Number(dd));
  return d.getDate() === Number(dd) && d.getMonth() === Number(mm) - 1 ? `${yyyy}-${mm}-${dd}` : null;
}
/** Gõ số → tự chèn dấu "/" thành dd/mm/yyyy. */
function maskDate(raw: string) {
  const digits = raw.replace(/\D/g, '').slice(0, 8);
  return [digits.slice(0, 2), digits.slice(2, 4), digits.slice(4)].filter(Boolean).join('/');
}

type DateInputProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'value' | 'onChange' | 'type' | 'min' | 'max'> & {
  /** yyyy-mm-dd hoặc '' */
  value: string;
  onChange: (iso: string) => void;
  min?: string;
  max?: string;
};

/**
 * Ô ngày luôn hiện dd/mm/yyyy (ô date gốc của trình duyệt hiện theo ngôn ngữ máy, thường là mm/dd/yyyy).
 * Gõ số hoặc bấm biểu tượng lịch để chọn; giá trị trả ra vẫn là yyyy-mm-dd như ô date gốc.
 */
export function DateInput({ value, onChange, min, max, disabled, className = '', ...rest }: DateInputProps) {
  const pickerRef = useRef<HTMLInputElement>(null);
  const [text, setText] = useState(() => isoToVn(value));
  useEffect(() => setText(isoToVn(value)), [value]);
  return (
    <div className={`relative ${className}`}>
      <input
        {...rest}
        type="text"
        inputMode="numeric"
        placeholder="dd/mm/yyyy"
        maxLength={10}
        disabled={disabled}
        className="field-control pr-8 tabular-nums"
        value={text}
        onChange={(e) => {
          const next = maskDate(e.target.value);
          setText(next);
          if (next === '') onChange('');
          else {
            const iso = vnToIso(next);
            if (iso) onChange(iso);
          }
        }}
        // Gõ dở / sai ngày → trả lại ngày đang chọn.
        onBlur={() => {
          if (text && !vnToIso(text)) setText(isoToVn(value));
        }}
      />
      <button
        type="button"
        tabIndex={-1}
        aria-label="Mở lịch chọn ngày"
        disabled={disabled}
        onClick={() => {
          try {
            pickerRef.current?.showPicker();
          } catch {
            pickerRef.current?.focus();
          }
        }}
        className="absolute inset-y-0 right-0 flex w-8 items-center justify-center text-ink-faint hover:text-primary disabled:pointer-events-none disabled:opacity-50"
      >
        <CalendarDays size={15} aria-hidden />
      </button>
      <input
        ref={pickerRef}
        type="date"
        tabIndex={-1}
        aria-hidden
        className="pointer-events-none absolute bottom-0 right-0 h-px w-px opacity-0"
        value={value}
        min={min}
        max={max}
        onChange={(e) => onChange(e.target.value)}
      />
    </div>
  );
}

export function Select({ className = '', children, ...rest }: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <select className={`field-control pr-7 ${className}`} {...rest}>
      {children}
    </select>
  );
}

export const Textarea = ({ className = '', ...rest }: TextareaHTMLAttributes<HTMLTextAreaElement>) => <textarea className={`field-control h-auto py-2 ${className}`} rows={3} {...rest} />;

export function Checkbox({ label, ...rest }: InputHTMLAttributes<HTMLInputElement> & { label: string }) {
  return (
    <label className="inline-flex items-center gap-2 text-body">
      <input type="checkbox" className="h-4 w-4 accent-[var(--primary)]" {...rest} />
      {label}
    </label>
  );
}

/** Tạo options từ bảng nhãn enum, ví dụ saleChannelLabels. */
export function enumOptions(labels: Record<string, string | { label: string }>) {
  return Object.entries(labels).map(([value, meta]) => (
    <option key={value} value={value}>
      {typeof meta === 'string' ? meta : meta.label}
    </option>
  ));
}
