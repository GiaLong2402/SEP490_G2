import { useState } from 'react'
import AccountShell from '@/components/storefront/AccountShell/AccountShell'
import AddressFormDialog from '@/components/storefront/AddressFormDialog/AddressFormDialog'
import Button from '@/components/ui/Button'
import LoadState from '@/components/ui/LoadState'
import Modal from '@/components/ui/Modal'
import { CheckIcon, MapPinIcon, PencilIcon, PhoneIcon, PlusIcon, TrashIcon } from '@/components/icons/Icons'
import { addressesApi } from '@/api/addresses'
import { ROUTES } from '@/constants/routes'
import { useAuth } from '@/context/AuthContext'
import { useAsync } from '@/hooks/useAsync'
import './AccountPage.css'

/** Sổ địa chỉ — dữ liệu qua addressesApi (GET/POST/PUT/DELETE /me/addresses) */
export default function AddressBookPage() {
  const { user } = useAuth()
  const { data: addresses, loading, error, reload } = useAsync((signal) => addressesApi.list(signal), [])
  const list = addresses || []

  // dialog: null | { mode: 'add' } | { mode: 'edit', address }
  const [dialog, setDialog] = useState(null)
  const [deleting, setDeleting] = useState(null)
  const [busy, setBusy] = useState(false)
  const [actionError, setActionError] = useState('')

  async function run(action) {
    setBusy(true)
    setActionError('')
    try {
      await action()
      reload()
      return true
    } catch (err) {
      setActionError(err.message || 'Thao tác không thành công.')
      return false
    } finally {
      setBusy(false)
    }
  }

  async function confirmDelete() {
    if (await run(() => addressesApi.remove(deleting.id))) setDeleting(null)
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
            <p>{list.length} địa chỉ · Địa chỉ mặc định được chọn sẵn khi thanh toán</p>
          </div>
          <Button
            type="button"
            variant="primary"
            pill
            className="account-addresses__add"
            onClick={() => setDialog({ mode: 'add' })}
            disabled={loading || Boolean(error)}
          >
            <PlusIcon size={18} />
            Thêm địa chỉ
          </Button>
        </div>

        {actionError && (
          <p className="account-note" role="alert">
            {actionError}
          </p>
        )}

        <LoadState
          loading={loading}
          error={error}
          onRetry={reload}
          empty={list.length === 0}
          emptyText="Bạn chưa có địa chỉ nào. Hãy thêm địa chỉ để đặt hàng nhanh hơn."
        >
          <ul className="account-address-list">
            {list.map((addr) => (
              <li key={addr.id} className={`account-address-card ${addr.isDefault ? 'is-default' : ''}`}>
                <div className="account-address-card__top">
                  <span className="account-address-card__icon" aria-hidden="true">
                    <MapPinIcon size={22} />
                  </span>
                  <div className="account-address-card__who">
                    <strong>{addr.recipientName}</strong>
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

                <p className="account-address-card__line">{addr.fullText}</p>

                <div className="account-address-card__actions">
                  <button
                    type="button"
                    className="account-address-card__action"
                    onClick={() => setDialog({ mode: 'edit', address: addr })}
                  >
                    <PencilIcon size={16} />
                    Sửa
                  </button>
                  {!addr.isDefault && (
                    <>
                      <button
                        type="button"
                        className="account-address-card__action"
                        disabled={busy}
                        onClick={() => run(() => addressesApi.setDefault(addr))}
                      >
                        <CheckIcon size={16} />
                        Đặt làm mặc định
                      </button>
                      <button
                        type="button"
                        className="account-address-card__action is-danger"
                        onClick={() => setDeleting(addr)}
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
        </LoadState>
      </div>

      {dialog && (
        <AddressFormDialog
          key={dialog.address?.id || 'new'}
          open
          address={dialog.address || null}
          forceDefault={list.length === 0}
          defaults={{ recipientName: user?.name || '', phone: user?.phone || '' }}
          onClose={() => setDialog(null)}
          onSaved={reload}
        />
      )}

      <Modal
        open={Boolean(deleting)}
        onClose={() => setDeleting(null)}
        busy={busy}
        size="sm"
        tone="danger"
        icon={<TrashIcon size={22} />}
        title="Xóa địa chỉ này?"
        description={deleting?.fullText}
        footer={
          <>
            <Button variant="ghost" pill onClick={() => setDeleting(null)} disabled={busy}>
              Giữ lại
            </Button>
            <Button variant="danger" pill onClick={confirmDelete} disabled={busy}>
              {busy ? 'Đang xóa…' : 'Xóa địa chỉ'}
            </Button>
          </>
        }
      />
    </AccountShell>
  )
}
