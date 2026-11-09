import { useState } from 'react'
import AccountShell from '@/components/storefront/AccountShell/AccountShell'
import Button from '@/components/ui/Button'
import { CheckIcon, MapPinIcon, PencilIcon, PhoneIcon, PlusIcon, TrashIcon } from '@/components/icons/Icons'
import { ROUTES } from '@/constants/routes'
import { useAuth } from '@/context/AuthContext'
import './AccountPage.css'

/**
 * user.address có thể là chuỗi hoặc object {street, ward, district, province}
 * (form hoàn tất hồ sơ lưu dạng object) → luôn đổi về 1 dòng chữ.
 */
function formatAddress(address) {
  if (!address) return ''
  if (typeof address === 'string') return address
  return [address.street, address.ward, address.district, address.province].filter(Boolean).join(', ')
}

export default function AddressBookPage() {
  const { user } = useAuth()

  const [addresses, setAddresses] = useState(() => [
    {
      id: 1,
      isDefault: true,
      receiver: user?.name || 'Khách hàng',
      phone: user?.phone || '',
      line: formatAddress(user?.address) || 'Chưa cập nhật địa chỉ',
    },
  ])

  // Demo: đổi mặc định / xóa chỉ trên state, chưa gọi API
  function setDefaultAddress(id) {
    setAddresses((list) => list.map((a) => ({ ...a, isDefault: a.id === id })))
  }

  function removeAddress(addr) {
    if (!window.confirm(`Xóa địa chỉ "${addr.line}"?`)) return
    setAddresses((list) => list.filter((a) => a.id !== addr.id))
  }

  return (
    <AccountShell
      title="Sổ địa chỉ"
      breadcrumbs={[
        { label: 'Trang chủ', to: ROUTES.HOME },
        { label: 'Tài khoản', to: ROUTES.ACCOUNT },
        { label: 'Sổ địa chỉ' },
      ]}
    >
      <div className="account-card account-addresses">
        <div className="account-addresses__head">
          <div>
            <h2>Địa chỉ nhận hàng</h2>
            <p>{addresses.length} địa chỉ · Địa chỉ mặc định được chọn sẵn khi thanh toán</p>
          </div>
          <Button type="button" variant="primary" pill className="account-addresses__add">
            <PlusIcon size={18} />
            Thêm địa chỉ
          </Button>
        </div>

        {addresses.length === 0 ? (
          <p className="account-addresses__empty">
            Bạn chưa có địa chỉ nào. Hãy thêm địa chỉ để đặt hàng nhanh hơn.
          </p>
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
    </AccountShell>
  )
}
