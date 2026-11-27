import { Navigate, useParams } from 'react-router-dom';
import { Alert, ErrorState, PageLoading } from '@/shared/ui';
import { CompletePanel } from '../components/CompletePanel';
import { IssuePanel } from '../components/IssuePanel';
import { MaterialPlanCard } from '../components/MaterialPlanCard';
import { HistoryCard, OrderShell, StepLayout, orderHistory } from '../components/OrderShell';
import { PlanEditor } from '../components/PlanEditor';
import { ReceiptPanel } from '../components/ReceiptPanel';
import { SummaryPanel } from '../components/SummaryPanel';
import { useProductionOrder } from '../hooks';

/*
 * Đã chốt 24/11/2026 — việc còn lại theo bước (xem bản thiết kế "Lệnh sản xuất - thiết kế tham khảo"):
 * - Nháp (Duy): thiếu NVL thì khóa Duyệt + lối tắt tạo phiếu nhập NVL; tồn dùng được không tính lô hết hạn.
 * - Đã duyệt (Cường): POST /material-issues/{id}/confirm bỏ allocations chọn tay, lọc lô hết hạn (FEFO), nhận note.
 * - Đang sản xuất (Cường): /complete nhận thêm finishedDate, defectQty, defectReason, leftovers[] (tạo lô -TL, trừ giá vốn);
 *   lưu dòng hàng thật cho phiếu nhập chờ. Đạt + hỏng không được vượt kế hoạch.
 * - Chờ nhập kho (Long): /goods-receipts/{id}/complete nhận phiếu thành phẩm; số khay sửa được;
 *   giá vốn / khay vào đơn giá dòng phiếu; nhập xong lệnh tự chuyển completed.
 * - Hoàn thành: giá vốn chia theo số khay nhập kho; Task 59 — không tự đổi listPrice (Áp dụng ở màn SP).
 */

/** Tạo lệnh mới: bước 01 với form trống. */
export function NewProductionOrderPage() {
  return (
    <OrderShell order={null} status="draft">
      <PlanEditor order={null} />
    </OrderShell>
  );
}

/** Đường dẫn cũ /:id/edit → trang chi tiết (lệnh nháp sửa ngay tại đó). */
export function EditRedirect() {
  return <Navigate to={`/production-orders/${useParams().id}`} replace />;
}

/** Chi tiết lệnh: hiện đúng màn của bước đang làm (B → F trong bản thiết kế). */
export default function ProductionOrderDetailPage() {
  const id = Number(useParams().id);
  const { data: order, isLoading, error, refetch } = useProductionOrder(id);
  if (isLoading) return <PageLoading />;
  if (error || !order) return <ErrorState error={error} onRetry={refetch} />;

  return (
    <OrderShell order={order} status={order.status}>
      {order.status === 'draft' && <PlanEditor order={order} />}
      {order.status === 'approved' && <IssuePanel order={order} />}
      {order.status === 'in_progress' && <CompletePanel order={order} />}
      {order.status === 'awaiting_receipt' && <ReceiptPanel order={order} />}
      {order.status === 'completed' && <SummaryPanel order={order} />}
      {order.status === 'cancelled' && (
        <StepLayout aside={<HistoryCard events={orderHistory(order)} />}>
          <Alert tone="danger">Lệnh đã hủy.</Alert>
          <MaterialPlanCard order={order} />
        </StepLayout>
      )}
    </OrderShell>
  );
}
