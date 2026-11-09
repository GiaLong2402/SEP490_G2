/**
 * Bộ icon SVG thống nhất — không gen icon thành ảnh bitmap.
 * size mặc định 20; truyền className nếu cần style thêm.
 */

function baseProps(size, className) {
  return {
    width: size,
    height: size,
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: 2,
    strokeLinecap: 'round',
    strokeLinejoin: 'round',
    className,
    'aria-hidden': true,
    focusable: false,
  }
}

export function SearchIcon({ size = 20, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <circle cx="11" cy="11" r="7" />
      <path d="m20 20-3.5-3.5" />
    </svg>
  )
}

export function UserIcon({ size = 20, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <circle cx="12" cy="8" r="4" />
      <path d="M4 20c1.5-3.5 4.5-5 8-5s6.5 1.5 8 5" />
    </svg>
  )
}

export function CartIcon({ size = 20, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <path d="M3 5h2l2.2 10.2a2 2 0 0 0 2 1.6h7.6a2 2 0 0 0 2-1.5L20 8H7" />
      <circle cx="10" cy="20" r="1.2" fill="currentColor" stroke="none" />
      <circle cx="17" cy="20" r="1.2" fill="currentColor" stroke="none" />
    </svg>
  )
}

export function BellIcon({ size = 20, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <path d="M6 9a6 6 0 0 1 12 0c0 7 3 7 3 9H3c0-2 3-2 3-9" />
      <path d="M10 20a2 2 0 0 0 4 0" />
    </svg>
  )
}

export function LogoutIcon({ size = 20, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <path d="M10 4H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h4" />
      <path d="M16 12H9" />
      <path d="m14 8 4 4-4 4" />
    </svg>
  )
}

export function TextAaIcon({ size = 20, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <path d="M4 18 9 6h2l5 12" />
      <path d="M6.5 13h5" />
      <path d="M15 18v-3h5v3" />
      <path d="M17.5 15V9h1.2A1.8 1.8 0 0 1 20.5 11v0A1.8 1.8 0 0 1 18.7 13H17.5" />
    </svg>
  )
}

export function MenuIcon({ size = 20, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <path d="M4 7h16M4 12h16M4 17h16" />
    </svg>
  )
}

export function ChevronDownIcon({ size = 18, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <path d="m6 9 6 6 6-6" />
    </svg>
  )
}

export function ChevronUpIcon({ size = 18, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <path d="m6 15 6-6 6 6" />
    </svg>
  )
}

export function ChevronLeftIcon({ size = 20, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <path d="m15 6-6 6 6 6" />
    </svg>
  )
}

export function ChevronRightIcon({ size = 20, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <path d="m9 6 6 6-6 6" />
    </svg>
  )
}

export function PlusIcon({ size = 18, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <path d="M12 5v14M5 12h14" />
    </svg>
  )
}

export function MinusIcon({ size = 18, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <path d="M5 12h14" />
    </svg>
  )
}

export function TrashIcon({ size = 18, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <path d="M4 7h16" />
      <path d="M10 11v6M14 11v6" />
      <path d="M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12" />
      <path d="M9 7V5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2" />
    </svg>
  )
}

export function TruckIcon({ size = 20, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <path d="M3 7h11v10H3zM14 10h4l3 3v4h-7V10z" />
      <circle cx="7" cy="18" r="1.5" />
      <circle cx="17" cy="18" r="1.5" />
    </svg>
  )
}

export function CheckIcon({ size = 16, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <path d="m5 12 4 4 10-10" />
    </svg>
  )
}

export function LockIcon({ size = 18, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <rect x="5" y="11" width="14" height="10" rx="2" />
      <path d="M8 11V8a4 4 0 0 1 8 0v3" />
    </svg>
  )
}

export function FilterIcon({ size = 18, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <path d="M4 5h16l-6 7v5l-4 2v-7L4 5z" />
    </svg>
  )
}

export function InfoIcon({ size = 18, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 10v6M12 7h.01" />
    </svg>
  )
}

export function PackageIcon({ size = 22, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <path d="M21 8.5 12 3 3 8.5v7L12 21l9-5.5v-7z" />
      <path d="M12 12v9M3 8.5l9 3.5 9-3.5" />
    </svg>
  )
}

export function PhoneIcon({ size = 20, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <path d="M6 3h4l2 5-3 2a12 12 0 0 0 5 5l2-3 5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 4 5a2 2 0 0 1 2-2z" />
    </svg>
  )
}

export function ScaleIcon({ size = 22, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <path d="M12 3v18M5 7h14M7 7l-3 7h6L7 7zm10 0-3 7h6l-3-7z" />
    </svg>
  )
}

export function PencilIcon({ size = 18, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <path d="M4 20h4L19 9a2.8 2.8 0 0 0-4-4L4 16v4z" />
      <path d="m13.5 6.5 4 4" />
    </svg>
  )
}

export function MailIcon({ size = 20, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <rect x="3" y="5" width="18" height="14" rx="2" />
      <path d="m4 7 8 6 8-6" />
    </svg>
  )
}

export function MapPinIcon({ size = 20, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <path d="M12 21s7-5.2 7-11a7 7 0 1 0-14 0c0 5.8 7 11 7 11z" />
      <circle cx="12" cy="10" r="2.5" />
    </svg>
  )
}

export function SunIcon({ size = 20, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <circle cx="12" cy="12" r="4" />
      <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
    </svg>
  )
}

export function MonitorIcon({ size = 20, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <rect x="3" y="4" width="18" height="12" rx="2" />
      <path d="M8 20h8M12 16v4" />
    </svg>
  )
}

export function SmartphoneIcon({ size = 20, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <rect x="7" y="3" width="10" height="18" rx="2" />
      <path d="M11 18h2" />
    </svg>
  )
}

export function HistoryIcon({ size = 20, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <path d="M3 12a9 9 0 1 0 2.6-6.4" />
      <path d="M3 4v4h4M12 8v4l3 2" />
    </svg>
  )
}

export function LoginIcon({ size = 20, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <path d="M14 4h4a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-4" />
      <path d="M4 12h10" />
      <path d="m10 8 4 4-4 4" />
    </svg>
  )
}

export function ShieldIcon({ size = 20, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <path d="M12 3 5 6v6c0 4.5 3 7.5 7 9 4-1.5 7-4.5 7-9V6l-7-3z" />
      <path d="m9 12 2 2 4-4" />
    </svg>
  )
}

export function CalendarIcon({ size = 18, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <rect x="4" y="5" width="16" height="15" rx="2" />
      <path d="M8 3v4M16 3v4M4 10h16" />
    </svg>
  )
}

export function ClockIcon({ size = 18, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3 2" />
    </svg>
  )
}

export function ClipboardIcon({ size = 22, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <rect x="6" y="4" width="12" height="16" rx="2" />
      <path d="M9 4V3h6v1M9 10h6M9 14h4" />
    </svg>
  )
}

export function SlidersIcon({ size = 22, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <path d="M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12M20 18h0" />
      <circle cx="16" cy="6" r="2" />
      <circle cx="10" cy="12" r="2" />
      <circle cx="18" cy="18" r="2" />
    </svg>
  )
}

export function RefreshIcon({ size = 18, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <path d="M4 12a8 8 0 1 0 2.5-5.8" />
      <path d="M4 4v4h4" />
    </svg>
  )
}

export function HeadsetIcon({ size = 22, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <path d="M4 14v-2a8 8 0 0 1 16 0v2" />
      <rect x="3" y="13" width="4" height="6" rx="1.5" />
      <rect x="17" y="13" width="4" height="6" rx="1.5" />
      <path d="M19 19c0 1.5-2 2.5-5 2.5" />
    </svg>
  )
}

export function ZoomInIcon({ size = 20, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <circle cx="11" cy="11" r="7" />
      <path d="m20 20-3.5-3.5M11 8v6M8 11h6" />
    </svg>
  )
}

export function CloseIcon({ size = 20, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <path d="M6 6l12 12M18 6 6 18" />
    </svg>
  )
}

export function ArrowRightIcon({ size = 20, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <path d="M5 12h14M13 6l6 6-6 6" />
    </svg>
  )
}

export function ArrowLeftIcon({ size = 20, className }) {
  return (
    <svg {...baseProps(size, className)}>
      <path d="M19 12H5M11 6l-6 6 6 6" />
    </svg>
  )
}
