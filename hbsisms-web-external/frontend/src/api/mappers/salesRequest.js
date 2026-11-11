import { checkDto } from './checkDto'

/** Field giao diện bắt buộc cần (API_CONTRACT.md § 3) */
const REQUIRED = ['id', 'code', 'summary', 'status', 'receivedAt']

/**
 * MeSalesRequestResponse / SalesRequestResponse → yêu cầu trên giao diện.
 * `/me` trả status UPPERCASE; staff API lowercase — normalize để UI dùng NEW/….
 */
export function fromSalesRequestDto(dto) {
  checkDto(dto, REQUIRED, 'SalesRequestResponse', '3. Yêu cầu')
  return {
    id: dto.id,
    code: dto.code,
    summary: dto.summary || '',
    status: dto.status == null ? dto.status : String(dto.status).toUpperCase(),
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
