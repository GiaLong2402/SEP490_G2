import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import PageBanner from '@/components/storefront/PageBanner/PageBanner'
import Button from '@/components/ui/Button'
import {
  CheckIcon,
  ChevronRightIcon,
  ClipboardIcon,
  MailIcon,
  MapPinIcon,
  PencilIcon,
  PhoneIcon,
  PlusIcon,
  TextAaIcon,
  TrashIcon,
  TruckIcon,
  UserIcon,
} from '@/components/icons/Icons'
import { ROUTES } from '@/constants/routes'
import { useAuth } from '@/context/AuthContext'
import { getInitials } from '@/utils/initials'
import './AccountPage.css'

const TABS = [
  { id: 'profile', label: 'Thông tin cá nhân', Icon: UserIcon },
  { id: 'addresses', label: 'Sổ địa chỉ', Icon: MapPinIcon },
]

function formatJoined(date = new Date()) {
  const d = String(date.getDate()).padStart(2, '0')
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const y = date.getFullYear()
  return `${d}/${m}/${y}`
}

export default function AccountPage() {
  const { user, isLoggedIn } = useAuth()
  const [tab, setTab] = useState('profile')
  const [savedFlash, setSavedFlash] = useState(false)

  const displayName = user?.name || 'Khách hàng'
  const initials = getInitials(displayName)

  const [savedForm, setSavedForm] = useState(() => ({
    fullName: displayName,
    phone: user?.phone || '',
    email: user?.email || '',
    sendReceipt: true,
    sendOrderUpdates: true,
  }))
  const [form, setForm] = useState(savedForm)
  const isDirty = Object.keys(form).some((key) => form[key] !== savedForm[key])
  const hasEmail = form.email.trim() !== ''

  const [addresses, setAddresses] = useState(() => [
    {
      id: 1,
      label: 'Nhà riêng',
      isDefault: true,
      receiver: displayName,
      phone: user?.phone || '',
      line: user?.address || 'Chưa cập nhật địa chỉ',
    },
  ])

  const joinedLabel = useMemo(() => formatJoined(), [])

  // Demo: đổi mặc định / xóa chỉ trên state, chưa gọi API
  function setDefaultAddress(id) {
    setAddresses((list) => list.map((a) => ({ ...a, isDefault: a.id === id })))
  }

  function removeAddress(addr) {
    if (!window.confirm(`Xóa địa chỉ "${addr.line}"?`)) return
    setAddresses((list) => list.filter((a) => a.id !== addr.id))
  }

  function updateField(key, value) {
    setForm((prev) => ({ ...prev, [key]: value }))
  }

  function handleSave(e) {
    e.preventDefault()
    setSavedForm(form)
    setSavedFlash(true)
    window.setTimeout(() => setSavedFlash(false), 2200)
  }

  function handleReset() {
    setForm(savedForm)
  }

  return (
    <div className="account-page">
      <PageBanner
        title="Tài khoản"
        breadcrumbs={[{ label: 'Trang chủ', to: ROUTES.HOME }, { label: 'Tài khoản' }]}
      />

      <div className="container account-page__body">
        <div className="account-tabs" role="tablist" aria-label="Mục tài khoản">
          {TABS.map(({ id, label, Icon }) => (
            <button
              key={id}
              type="button"
              role="tab"
              aria-selected={tab === id}
              className={`account-tabs__item ${tab === id ? 'is-active' : ''}`}
              onClick={() => setTab(id)}
            >
              <Icon size={22} className="account-tabs__icon" />
              <span>{label}</span>
            </button>
          ))}
        </div>

        <div className="account-layout">
          <div className="account-main">
            {tab === 'profile' ? (
              <form className="account-card account-card--flush" onSubmit={handleSave}>
                {/* Phần đầu: avatar + tên + mức hoàn thiện hồ sơ */}
                <div className="account-hero">
                  <span className="account-hero__avatar" aria-hidden="true">
                    {initials}
                  </span>
                  <div className="account-hero__meta">
                    <h2>{form.fullName.trim() || displayName}</h2>
                    <div className="account-hero__chips">
                      <span className="account-chip account-chip--accent">
                        {isLoggedIn ? 'Khách hàng' : 'Chưa đăng nhập'}
                      </span>
                      {isLoggedIn && <span className="account-chip">Tham gia {joinedLabel}</span>}
                    </div>
                  </div>
                </div>

                {/* Thông tin liên hệ */}
                <section className="account-section">
                  <div className="account-grid">
                    <label className="account-field account-field--full">
                      <span className="account-field__label">Họ và tên</span>
                      <span className="account-input">
                        <UserIcon size={18} />
                        <input
                          type="text"
                          value={form.fullName}
                          onChange={(e) => updateField('fullName', e.target.value)}
                          autoComplete="name"
                          placeholder="Nhập họ và tên"
                        />
                      </span>
                    </label>

                    <label className="account-field">
                      <span className="account-field__label">Số điện thoại</span>
                      <span className="account-input">
                        <PhoneIcon size={18} />
                        <input
                          type="tel"
                          value={form.phone}
                          onChange={(e) => updateField('phone', e.target.value)}
                          autoComplete="tel"
                          placeholder="Nhập số điện thoại"
                        />
                        <button type="button" className="account-input__action">
                          Đổi SĐT
                        </button>
                      </span>
                    </label>

                    <label className="account-field">
                      <span className="account-field__label">Email</span>
                      <span className="account-input">
                        <MailIcon size={18} />
                        <input
                          type="email"
                          value={form.email}
                          onChange={(e) => updateField('email', e.target.value)}
                          autoComplete="email"
                          placeholder="you@email.com"
                        />
                      </span>
                    </label>
                  </div>
                </section>

                {/* Thông báo qua email */}
                <section className="account-section">
                  <div className="account-section__head">
                    <h3>Thông báo qua email</h3>
                    <p>Chọn những thông tin Hòa Bình gửi đến email của bạn.</p>
                  </div>

                  {!hasEmail && (
                    <p className="account-note">Nhập email ở phần trên để bật các thông báo này.</p>
                  )}

                  <div className="account-toggles">
                    <label className={`account-toggle ${hasEmail ? '' : 'is-disabled'}`}>
                      <span className="account-toggle__icon" aria-hidden="true">
                        <ClipboardIcon size={20} />
                      </span>
                      <span className="account-toggle__text">
                        <strong>Gửi biên lai</strong>
                        <span>Nhận hóa đơn sau mỗi lần thanh toán</span>
                      </span>
                      <input
                        type="checkbox"
                        checked={hasEmail && form.sendReceipt}
                        disabled={!hasEmail}
                        onChange={(e) => updateField('sendReceipt', e.target.checked)}
                      />
                      <span className="account-toggle__ui" aria-hidden="true" />
                    </label>
                    <label className={`account-toggle ${hasEmail ? '' : 'is-disabled'}`}>
                      <span className="account-toggle__icon" aria-hidden="true">
                        <TruckIcon size={20} />
                      </span>
                      <span className="account-toggle__text">
                        <strong>Gửi cập nhật đơn hàng</strong>
                        <span>Báo khi đơn được xác nhận, giao và hoàn tất</span>
                      </span>
                      <input
                        type="checkbox"
                        checked={hasEmail && form.sendOrderUpdates}
                        disabled={!hasEmail}
                        onChange={(e) => updateField('sendOrderUpdates', e.target.checked)}
                      />
                      <span className="account-toggle__ui" aria-hidden="true" />
                    </label>
                  </div>
                </section>

                {/* Lưu / hủy */}
                <div className="account-actions">
                  {savedFlash && (
                    <span className="account-actions__flash" role="status">
                      <CheckIcon size={16} /> Đã lưu thay đổi
                    </span>
                  )}
                  <Button type="button" variant="ghost" pill onClick={handleReset} disabled={!isDirty}>
                    Hủy thay đổi
                  </Button>
                  <Button type="submit" variant="primary" pill disabled={!isDirty}>
                    Lưu thay đổi
                  </Button>
                </div>
              </form>
            ) : (
              <div className="account-card account-addresses">
                <div className="account-addresses__head">
                  <div>
                    <h2>Địa chỉ nhận hàng</h2>
                    <p>
                      {addresses.length} địa chỉ · Địa chỉ mặc định được chọn sẵn khi thanh toán
                    </p>
                  </div>
                  <Button type="button" variant="primary" pill className="account-addresses__add">
                    <PlusIcon size={18} />
                    Thêm địa chỉ
                  </Button>
                </div>

                {addresses.length === 0 ? (
                  <p className="account-addresses__empty">Bạn chưa có địa chỉ nào. Hãy thêm địa chỉ để đặt hàng nhanh hơn.</p>
                ) : (
                  <ul className="account-address-list">
                    {addresses.map((addr) => (
                      <li key={addr.id} className={`account-address-card ${addr.isDefault ? 'is-default' : ''}`}>
                        <div className="account-address-card__top">
                          <span className="account-address-card__icon" aria-hidden="true">
                            <MapPinIcon size={22} />
                          </span>
                          <div className="account-address-card__who">
                            <strong>{addr.receiver}</strong>
                            <span>
                              <PhoneIcon size={15} />
                              {addr.phone || 'Chưa có số điện thoại'}
                            </span>
                          </div>
                          {addr.isDefault && (
                            <span className="account-address-card__badge">
                              <CheckIcon size={14} />
                              Mặc định
                            </span>
                          )}
                        </div>

                        <p className="account-address-card__line">{addr.line}</p>

                        <div className="account-address-card__actions">
                          <button type="button" className="account-address-card__action">
                            <PencilIcon size={16} />
                            Sửa
                          </button>
                          {!addr.isDefault && (
                            <>
                              <button
                                type="button"
                                className="account-address-card__action"
                                onClick={() => setDefaultAddress(addr.id)}
                              >
                                <CheckIcon size={16} />
                                Đặt làm mặc định
                              </button>
                              <button
                                type="button"
                                className="account-address-card__action is-danger"
                                onClick={() => removeAddress(addr)}
                              >
                                <TrashIcon size={16} />
                                Xóa
                              </button>
                            </>
                          )}
                        </div>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            )}
          </div>

          <aside className="account-side">
            <div className="account-card account-shortcuts">
              <h3>Lối tắt</h3>
              <Link to={ROUTES.ORDERS} className="account-shortcut">
                <span className="account-shortcut__icon">
                  <ClipboardIcon size={24} />
                </span>
                <span className="account-shortcut__text">
                  <strong>Đơn &amp; Yêu cầu của tôi</strong>
                  <span>Theo dõi đơn và yêu cầu</span>
                </span>
                <ChevronRightIcon size={18} className="account-shortcut__chevron" />
              </Link>
              <Link to={ROUTES.DISPLAY_SETTINGS} className="account-shortcut">
                <span className="account-shortcut__icon">
                  <TextAaIcon size={24} />
                </span>
                <span className="account-shortcut__text">
                  <strong>Cài đặt chữ lớn &amp; Hiển thị</strong>
                  <span>Chữ lớn, độ tương phản</span>
                </span>
                <ChevronRightIcon size={18} className="account-shortcut__chevron" />
              </Link>
            </div>
          </aside>
        </div>
      </div>
    </div>
  )
}
