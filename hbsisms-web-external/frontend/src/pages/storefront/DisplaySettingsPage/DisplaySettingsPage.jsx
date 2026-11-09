import { useState } from 'react'
import { Link } from 'react-router-dom'
import AccountShell from '@/components/storefront/AccountShell/AccountShell'
import Button from '@/components/ui/Button'
import {
  ArrowRightIcon,
  CheckIcon,
  HistoryIcon,
  LoginIcon,
  LogoutIcon,
  MailIcon,
  MapPinIcon,
  MonitorIcon,
  RefreshIcon,
  ShieldIcon,
  SmartphoneIcon,
  SunIcon,
  TextAaIcon,
} from '@/components/icons/Icons'
import { ROUTES } from '@/constants/routes'
import { formatPrice } from '@/utils/formatPrice'
import '../AccountPage/AccountPage.css'
import './DisplaySettingsPage.css'

const FONT_SIZES = [
  { id: 'normal', label: 'Tiêu chuẩn', px: 17 },
  { id: 'large', label: 'Lớn', px: 20 },
  { id: 'xlarge', label: 'Rất lớn', px: 22 },
]

const DEFAULT_SETTINGS = { fontSize: 'normal', highContrast: false }

// Demo: chưa có API phiên đăng nhập / nhật ký hoạt động
const DEMO_DEVICES = [
  { id: 'this', name: 'Chrome trên Windows', meta: 'Đang hoạt động', Icon: MonitorIcon, current: true },
  { id: 'iphone', name: 'Safari trên iPhone', meta: 'Đăng nhập 20/10 lúc 18:05', Icon: SmartphoneIcon },
]

const DEMO_ACTIVITY = [
  { title: 'Đăng nhập trên thiết bị mới', detail: 'Safari trên iPhone', time: '20/10 · 18:05', Icon: LoginIcon, tone: 'accent' },
  { title: 'Đổi địa chỉ mặc định', detail: 'Nhà riêng · Hải Phòng', time: '18/10 · 09:30', Icon: MapPinIcon, tone: 'info' },
  { title: 'Thêm email', detail: 'an.nguyen@gmail.com', time: '15/10 · 20:12', Icon: MailIcon, tone: 'warning' },
]

export default function DisplaySettingsPage() {
  const [saved, setSaved] = useState(DEFAULT_SETTINGS)
  const [settings, setSettings] = useState(DEFAULT_SETTINGS)
  const [devices, setDevices] = useState(DEMO_DEVICES)
  const [flash, setFlash] = useState(false)

  const isDirty = settings.fontSize !== saved.fontSize || settings.highContrast !== saved.highContrast
  const isDefault =
    settings.fontSize === DEFAULT_SETTINGS.fontSize && settings.highContrast === DEFAULT_SETTINGS.highContrast
  const previewPx = FONT_SIZES.find((f) => f.id === settings.fontSize).px
  const otherDevices = devices.filter((d) => !d.current)

  function update(key, value) {
    setSettings((prev) => ({ ...prev, [key]: value }))
  }

  function handleSave() {
    setSaved(settings)
    setFlash(true)
    window.setTimeout(() => setFlash(false), 2200)
  }

  function logoutDevice(id) {
    setDevices((list) => list.filter((d) => d.id !== id))
  }

  return (
    <AccountShell
      title="Cài đặt hiển thị"
      breadcrumbs={[
        { label: 'Trang chủ', to: ROUTES.HOME },
        { label: 'Tài khoản', to: ROUTES.ACCOUNT },
        { label: 'Cài đặt hiển thị' },
      ]}
    >
      <div className="settings">
        {/* Cỡ chữ + tương phản */}
        <section className="account-card settings-card">
          <div className="settings-card__head">
            <span className="settings-card__icon" aria-hidden="true">
              <TextAaIcon size={22} />
            </span>
            <div>
              <h2>Cỡ chữ &amp; hiển thị</h2>
              <p>Chọn cách hiển thị phù hợp với bạn.</p>
            </div>
          </div>

          <div className="settings-sizes" role="radiogroup" aria-label="Cỡ chữ">
            {FONT_SIZES.map((f) => {
              const active = settings.fontSize === f.id
              return (
                <button
                  key={f.id}
                  type="button"
                  role="radio"
                  aria-checked={active}
                  className={`settings-size ${active ? 'is-active' : ''}`}
                  onClick={() => update('fontSize', f.id)}
                >
                  {active && (
                    <span className="settings-size__check" aria-hidden="true">
                      <CheckIcon size={14} />
                    </span>
                  )}
                  <span className="settings-size__sample" style={{ fontSize: f.px * 1.7 }}>
                    Aa
                  </span>
                  <strong>{f.label}</strong>
                  <span>{f.px}px</span>
                </button>
              )
            })}
          </div>

          <div
            className={`settings-preview ${settings.highContrast ? 'is-contrast' : ''}`}
            style={{ fontSize: previewPx }}
          >
            <img src="/images/demo-products/product-cha-muc.png" alt="" />
            <div className="settings-preview__text">
              <span className="settings-preview__label">Xem trước</span>
              <strong>Chả mực Hòa Bình</strong>
              <span>Khay 0,5 kg</span>
            </div>
            <strong className="settings-preview__price">{formatPrice(85000)}</strong>
          </div>

          <label className="account-toggle settings-contrast">
            <span className="account-toggle__icon" aria-hidden="true">
              <SunIcon size={20} />
            </span>
            <span className="account-toggle__text">
              <strong>Tương phản cao</strong>
              <span>Tăng độ tương phản để nội dung dễ đọc hơn</span>
            </span>
            <input
              type="checkbox"
              checked={settings.highContrast}
              onChange={(e) => update('highContrast', e.target.checked)}
            />
            <span className="account-toggle__ui" aria-hidden="true" />
          </label>
        </section>

        <div className="settings-grid">
          {/* Thiết bị đăng nhập */}
          <section className="account-card settings-card">
            <div className="settings-card__head">
              <span className="settings-card__icon" aria-hidden="true">
                <MonitorIcon size={22} />
              </span>
              <div>
                <h2>
                  Thiết bị đăng nhập <span className="settings-count">{devices.length} thiết bị</span>
                </h2>
              </div>
            </div>

            <ul className="settings-list">
              {devices.map(({ id, name, meta, Icon, current }) => (
                <li key={id} className="settings-device">
                  <span className="settings-device__icon" aria-hidden="true">
                    <Icon size={20} />
                  </span>
                  <span className="settings-device__text">
                    <strong>{name}</strong>
                    <span>{meta}</span>
                  </span>
                  {current ? (
                    <span className="settings-badge">
                      <CheckIcon size={14} /> Thiết bị này
                    </span>
                  ) : (
                    <button type="button" className="settings-link-danger" onClick={() => logoutDevice(id)}>
                      Đăng xuất
                    </button>
                  )}
                </li>
              ))}
            </ul>

            <button
              type="button"
              className="settings-logout-all"
              disabled={otherDevices.length === 0}
              onClick={() => setDevices((list) => list.filter((d) => d.current))}
            >
              <LogoutIcon size={18} />
              Đăng xuất thiết bị khác
            </button>
          </section>

          {/* Hoạt động gần đây */}
          <section className="account-card settings-card">
            <div className="settings-card__head">
              <span className="settings-card__icon" aria-hidden="true">
                <HistoryIcon size={22} />
              </span>
              <div>
                <h2>Hoạt động gần đây</h2>
                <p>Các thay đổi mới nhất của bạn.</p>
              </div>
            </div>

            <ol className="settings-timeline">
              {DEMO_ACTIVITY.map(({ title, detail, time, Icon, tone }) => (
                <li key={title} className="settings-event">
                  <span className={`settings-event__icon settings-event__icon--${tone}`} aria-hidden="true">
                    <Icon size={18} />
                  </span>
                  <span className="settings-event__text">
                    <strong>{title}</strong>
                    <span>{detail}</span>
                  </span>
                  <time className="settings-event__time">{time}</time>
                </li>
              ))}
            </ol>
          </section>
        </div>

        <p className="settings-help">
          <ShieldIcon size={20} />
          <span>Không phải bạn thực hiện?</span>
          <Link to={ROUTES.CONTACT}>
            Liên hệ cửa hàng <ArrowRightIcon size={16} />
          </Link>
        </p>

        {/* Lưu / khôi phục */}
        <div className="account-card settings-actions">
          <span className="settings-actions__note" role="status">
            {flash ? (
              <span className="settings-actions__flash">
                <CheckIcon size={16} /> Đã lưu cài đặt
              </span>
            ) : (
              'Thay đổi được áp dụng sau khi lưu'
            )}
          </span>
          <Button type="button" variant="ghost" pill disabled={isDefault} onClick={() => setSettings(DEFAULT_SETTINGS)}>
            <RefreshIcon size={18} />
            Khôi phục mặc định
          </Button>
          <Button type="button" variant="primary" pill disabled={!isDirty} onClick={handleSave}>
            <CheckIcon size={18} />
            Lưu cài đặt
          </Button>
        </div>
      </div>
    </AccountShell>
  )
}
