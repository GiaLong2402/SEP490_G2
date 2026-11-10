import { checkDto } from './checkDto'

/** Field giao diện bắt buộc cần (API_CONTRACT.md § 3) */
const REQUIRED = ['id', 'code', 'summary', 'status', 'receivedAt']

/**
 * SalesRequestResponse (backend) → yêu cầu dùng trên giao diện.
 * neededDate / requestedQty / availableQty / orderCode: đề xuất bổ sung (xem API_CONTRACT.md),
 * chưa có thì để null.
 */
export function fromSalesRequestDto(dto) {
  checkDto(dto, REQUIRED, 'SalesRequestResponse', '3. Yêu cầu')
  return {
    id: dto.id,
    code: dto.code,
    summary: dto.summary || '',
    status: dto.status,
    receivedAt: dto.receivedAt || null,
    neededDate: dto.neededDate || null,
    requestedQty: dto.requestedQty ?? null,
    availableQty: dto.availableQty ?? null,
    feasible: dto.feasible ?? null,
    fulfillableQty: dto.fulfillableQty ?? null,
    shortageQty: dto.shortageQty ?? null,
    expectedDate: dto.expectedDate || null,
    rejectReason: dto.rejectReason || '',
    note: dto.note || '',
    orderCode: dto.orderCode || null,
  }
}
