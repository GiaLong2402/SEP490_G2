import { checkDto } from './checkDto'

/** Field giao diện bắt buộc cần (API_CONTRACT.md § 3) */
const REQUIRED = ['id', 'code', 'summary', 'status', 'receivedAt']

/**
 * MeSalesRequestResponse / SalesRequestResponse → yêu cầu trên giao diện.
 * `/me` trả status UPPERCASE; staff API lowercase — normalize để UI dùng NEW/….
 */
export function fromSalesRequestDto(dto) {
  checkDto(dto, REQUIRED, 'SalesRequestResponse', '3. Yêu cầu')
  const quote = dto.quote
    ? {
        id: dto.quote.id,
        code: dto.quote.code || '',
        status: dto.quote.status == null ? null : String(dto.quote.status).toUpperCase(),
        subtotal: dto.quote.subtotal ?? null,
        total: dto.quote.total ?? null,
        validUntil: dto.quote.validUntil || null,
        lines: (dto.quote.lines || []).map((l) => ({
          productName: l.productName || '',
          packLabel: l.packLabel || '',
          sku: l.sku || '',
          qty: l.qty ?? 0,
          qtyKhay: l.qtyKhay ?? 0,
          unitPrice: l.unitPrice ?? null,
          lineTotal: l.lineTotal ?? null,
          priceBasis: l.priceBasis || null,
        })),
      }
    : null
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
    lines: Array.isArray(dto.lines)
      ? dto.lines.map((l) => ({
          id: l.id,
          variantId: l.variantId,
          sku: l.sku || '',
          productName: l.productName || '',
          packLabel: l.packLabel || '',
          packPerCase: l.packPerCase ?? 1,
          unit: l.unit == null ? 'pack' : String(l.unit).toLowerCase(),
          qty: l.qty ?? 0,
          qtyKhay: l.qtyKhay ?? 0,
          fulfillableQty: l.fulfillableQty ?? null,
          shortageQty: l.shortageQty ?? null,
        }))
      : [],
    quote,
  }
}
