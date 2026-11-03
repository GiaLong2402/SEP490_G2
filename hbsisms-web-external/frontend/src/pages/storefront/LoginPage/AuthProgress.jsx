/**
 * Thanh tiến trình "Bước X / 4"
 * current: số bước đang đứng (1 → total)
 */
export default function AuthProgress({ current, total = 4 }) {
  return (
    <div className="auth-progress" aria-label={`Bước ${current} trên ${total}`}>
      <p className="auth-progress__label">
        Bước {current} / {total}
      </p>
      <div className="auth-progress__bars" aria-hidden="true">
        {Array.from({ length: total }, (_, index) => {
          const done = index < current
          return (
            <span
              key={index}
              className={`auth-progress__bar ${done ? 'is-active' : ''}`}
            />
          )
        })}
      </div>
    </div>
  )
}
