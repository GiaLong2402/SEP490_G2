/** Token nguồn: DESIGN.md trên Stitch, khai báo ở src/app/index.css */
// color-mix để các biến CSS dùng được cú pháp độ mờ của Tailwind, ví dụ bg-rowalt/60.
const token = (name) => `color-mix(in srgb, var(--${name}) calc(<alpha-value> * 100%), transparent)`;

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        primary: { DEFAULT: token('primary'), hover: token('primary-hover'), soft: token('primary-soft') },
        sidebar: { DEFAULT: token('sidebar'), active: token('sidebar-active') },
        canvas: token('canvas'),
        card: token('card'),
        rowalt: token('row-alt'),
        thead: token('thead'),
        ink: { DEFAULT: token('ink'), muted: token('ink-muted'), faint: token('ink-faint') },
        line: token('line'),
        field: token('field'),
        coral: token('coral'),
        green: { fg: token('green-fg'), bg: token('green-bg') },
        amber: { fg: token('amber-fg'), bg: token('amber-bg') },
        red: { fg: token('red-fg'), bg: token('red-bg') },
        blue: { fg: token('blue-fg'), bg: token('blue-bg') },
        neutral: { fg: token('neutral-fg'), bg: token('neutral-bg') },
      },
      fontFamily: {
        sans: ['Inter', '-apple-system', 'Segoe UI', 'Roboto', 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'SF Mono', 'ui-monospace', 'monospace'],
      },
      fontSize: {
        display: ['22px', { lineHeight: '28px', fontWeight: '700' }],
        h1: ['18px', { lineHeight: '24px', fontWeight: '700' }],
        h2: ['15px', { lineHeight: '20px', fontWeight: '600' }],
        body: ['13px', '18px'],
        small: ['12px', '16px'],
        micro: ['11px', { lineHeight: '14px', fontWeight: '500' }],
      },
      borderRadius: { sm: '6px', md: '8px', lg: '12px' },
      boxShadow: { card: '0 1px 2px rgba(8,34,45,.05)', pop: '0 8px 28px rgba(8,34,45,.16)' },
    },
  },
  plugins: [],
};
