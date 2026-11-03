import { cloneElement, useId, type InputHTMLAttributes, type ReactElement, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react';

interface FieldProps {
  label: string;
  error?: string;
  hint?: ReactNode;
  required?: boolean;
  className?: string;
  children: ReactElement;
}

/** Gắn nhãn, lỗi và aria-describedby cho một control. */
export function Field({ label, error, hint, required, className = '', children }: FieldProps) {
  const id = useId();
  const messageId = `${id}-msg`;
  return (
    <div className={className}>
      <label htmlFor={id} className="mb-1 block text-small font-medium text-ink-muted">
        {label}
        {required && <span className="text-red-fg"> *</span>}
      </label>
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
