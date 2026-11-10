import { useEffect, useRef, useState, type ClipboardEvent, type KeyboardEvent } from 'react';

const OTP_LENGTH = 6;

type Props = {
  /** Đổi giá trị này để focus lại ô đầu (sau khi gửi lại mã). */
  resetKey?: number | string;
  disabled?: boolean;
  value: string;
  onChange: (code: string) => void;
};

/** 6 ô OTP — hỗ trợ paste, backspace lùi ô, autofocus. */
export default function OtpCodeInput({ resetKey = 0, disabled = false, value, onChange }: Props) {
  const [digits, setDigits] = useState<string[]>(() => toDigits(value));
  const inputsRef = useRef<Array<HTMLInputElement | null>>([]);

  useEffect(() => {
    setDigits(toDigits(value));
  }, [value]);

  useEffect(() => {
    const t = window.setTimeout(() => inputsRef.current[0]?.focus(), 0);
    return () => window.clearTimeout(t);
  }, [resetKey]);

  const commit = (next: string[]) => {
    setDigits(next);
    onChange(next.join(''));
  };

  const focusIndex = (index: number) => {
    inputsRef.current[index]?.focus();
  };

  const handleChange = (index: number, raw: string) => {
    const onlyDigit = raw.replace(/\D/g, '').slice(-1);
    const next = [...digits];
    next[index] = onlyDigit;
    commit(next);
    if (onlyDigit && index < OTP_LENGTH - 1) focusIndex(index + 1);
  };

  const handleKeyDown = (index: number, e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Backspace' && !digits[index] && index > 0) {
      e.preventDefault();
      const next = [...digits];
      next[index - 1] = '';
      commit(next);
      focusIndex(index - 1);
    }
  };

  const handlePaste = (e: ClipboardEvent<HTMLDivElement>) => {
    e.preventDefault();
    const pasted = e.clipboardData.getData('text').replace(/\D/g, '').slice(0, OTP_LENGTH);
    if (!pasted) return;
    const next = Array(OTP_LENGTH).fill('');
    pasted.split('').forEach((ch, i) => {
      next[i] = ch;
    });
    commit(next);
    focusIndex(Math.min(pasted.length, OTP_LENGTH - 1));
  };

  return (
    <div>
      <p className="mb-1.5 block text-small font-medium text-ink-muted">
        Mã OTP <span className="text-red-fg">*</span>
      </p>
      <div className="flex justify-between gap-1.5 sm:gap-2" onPaste={handlePaste}>
        {digits.map((digit, index) => (
          <input
            key={index}
            ref={(el) => {
              inputsRef.current[index] = el;
            }}
            type="text"
            inputMode="numeric"
            autoComplete={index === 0 ? 'one-time-code' : 'off'}
            maxLength={1}
            value={digit}
            disabled={disabled}
            aria-label={`Số OTP thứ ${index + 1}`}
            className={`h-12 w-11 shrink-0 rounded-md border bg-card text-center text-lg font-semibold tabular-nums outline-none transition sm:h-14 sm:w-12 ${
              digit ? 'border-primary text-ink' : 'border-field text-ink'
            } focus:border-primary focus:ring-2 focus:ring-primary/20 disabled:opacity-60`}
            onChange={(e) => handleChange(index, e.target.value)}
            onKeyDown={(e) => handleKeyDown(index, e)}
          />
        ))}
      </div>
    </div>
  );
}

function toDigits(value: string): string[] {
  const clean = value.replace(/\D/g, '').slice(0, OTP_LENGTH);
  return Array.from({ length: OTP_LENGTH }, (_, i) => clean[i] ?? '');
}
