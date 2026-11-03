import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { Loader2 } from 'lucide-react';
import { Link, type LinkProps } from 'react-router-dom';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger';

const base = 'inline-flex h-[34px] items-center justify-center gap-1.5 whitespace-nowrap rounded-md px-3.5 text-body font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-50';
const variants: Record<Variant, string> = {
  primary: 'bg-primary text-white hover:bg-primary-hover',
  secondary: 'border border-field bg-card text-ink hover:bg-rowalt',
  ghost: 'text-ink-muted hover:bg-rowalt hover:text-ink',
  danger: 'border border-red-fg/40 bg-card text-red-fg hover:bg-red-bg',
};

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  loading?: boolean;
  icon?: ReactNode;
}

export function Button({ variant = 'secondary', loading, icon, children, className = '', type = 'button', disabled, ...rest }: ButtonProps) {
  return (
    <button type={type} disabled={disabled || loading} className={`${base} ${variants[variant]} ${className}`} {...rest}>
      {loading ? <Loader2 size={15} className="animate-spin" aria-hidden /> : icon}
      {children}
    </button>
  );
}

export function LinkButton({ variant = 'secondary', icon, children, className = '', ...rest }: LinkProps & { variant?: Variant; icon?: ReactNode }) {
  return (
    <Link className={`${base} ${variants[variant]} ${className}`} {...rest}>
      {icon}
      {children}
    </Link>
  );
}

export function IconButton({ label, className = '', children, ...rest }: ButtonHTMLAttributes<HTMLButtonElement> & { label: string }) {
  return (
    <button type="button" aria-label={label} title={label} className={`inline-flex h-8 w-8 items-center justify-center rounded-md text-ink-muted hover:bg-rowalt hover:text-ink disabled:opacity-40 ${className}`} {...rest}>
      {children}
    </button>
  );
}
