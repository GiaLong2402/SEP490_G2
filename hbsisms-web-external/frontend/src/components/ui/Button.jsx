import './Button.css'

/**
 * Nút dùng chung.
 * variant: primary | secondary | outline | navy | ghost | link | danger
 */
export default function Button({
  children,
  variant = 'primary',
  type = 'button',
  className = '',
  fullWidth = false,
  pill = false,
  disabled = false,
  onClick,
  ...rest
}) {
  const classes = [
    'btn',
    `btn--${variant}`,
    fullWidth ? 'btn--full' : '',
    pill ? 'btn--pill' : '',
    className,
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <button
      type={type}
      className={classes}
      disabled={disabled}
      onClick={onClick}
      {...rest}
    >
      {children}
    </button>
  )
}
