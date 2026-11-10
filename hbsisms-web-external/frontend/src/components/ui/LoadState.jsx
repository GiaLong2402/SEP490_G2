import { InfoIcon, RefreshIcon } from '@/components/icons/Icons'
import './LoadState.css'

/**
 * Trạng thái khi tải dữ liệu: đang tải / lỗi (kèm Thử lại) / rỗng.
 *
 *   <LoadState loading={loading} error={error} onRetry={reload} empty={!data?.length} emptyText="Chưa có đơn hàng">
 *     ...nội dung khi có dữ liệu...
 *   </LoadState>
 */
export default function LoadState({ loading, error, onRetry, empty, emptyText = 'Chưa có dữ liệu.', emptyAction, children }) {
  if (loading) {
    return (
      <div className="load-state" role="status">
        <span className="load-state__spinner" aria-hidden="true" />
        Đang tải…
      </div>
    )
  }

  if (error) {
    const notReady = error.status === 404 || error.status === 401 || error.status === 403
    return (
      <div className="load-state load-state--error" role="alert">
        <InfoIcon size={24} />
        <strong>{notReady ? 'Chưa tải được dữ liệu' : 'Có lỗi khi tải dữ liệu'}</strong>
        <span>{error.message || 'Vui lòng thử lại sau.'}</span>
        {onRetry && (
          <button type="button" className="load-state__retry" onClick={onRetry}>
            <RefreshIcon size={16} /> Thử lại
          </button>
        )}
      </div>
    )
  }

  if (empty) {
    return (
      <div className="load-state load-state--empty">
        <span>{emptyText}</span>
        {emptyAction}
      </div>
    )
  }

  return children
}
