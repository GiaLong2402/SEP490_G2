/**
 * Data demo cho trang "Đơn & Yêu cầu của tôi" (chưa gọi API).
 * Ngày theo dạng YYYY-MM-DD; price là đơn giá 1 khay.
 * status đơn: awaiting_payment | preparing | shipping | completed | cancelled
 * status yêu cầu: reviewing | confirmed | rejected
 */
const IMG = {
  cha: '/images/demo-products/product-cha-muc.png',
  muc: '/images/demo-products/product-muc-ong.png',
  tom: '/images/demo-products/product-tom-su.png',
  ca: '/images/demo-products/product-ca-thu.png',
}

export const DEMO_ORDERS = [
  {
    code: 'HB00131',
    date: '2026-11-09',
    status: 'awaiting_payment',
    message: 'Còn 14:32 để thanh toán',
    shippingFee: 25000,
    lines: [
      { name: 'Mực nguyên con làm sạch', variant: 'Khay 500g', qty: 2, price: 120000, image: IMG.muc },
    ],
  },
  {
    code: 'HB00128',
    date: '2026-11-09',
    status: 'shipping',
    message: 'Đơn hàng đang trên đường giao đến bạn',
    shippingFee: 25000,
    lines: [
      { name: 'Mực ống loại 1', variant: 'Khay 500g', qty: 2, price: 140000, image: IMG.muc },
      { name: 'Cá thu cắt khúc', variant: 'Khay 500g', qty: 1, price: 150000, image: IMG.ca },
    ],
  },
  {
    code: 'HB00125',
    date: '2026-11-08',
    status: 'preparing',
    message: 'Cửa hàng đang sơ chế và đóng khay',
    shippingFee: 25000,
    lines: [
      { name: 'Chả mực Hạ Long', variant: 'Khay 500g', qty: 3, price: 110000, image: IMG.cha },
    ],
  },
  {
    code: 'HB00112',
    date: '2026-11-05',
    status: 'completed',
    message: 'Đã nhận hàng ngày 05/11/2026',
    shippingFee: 25000,
    lines: [
      { name: 'Chả mực Hạ Long', variant: 'Khay 500g', qty: 2, price: 110000, image: IMG.cha },
    ],
  },
  {
    code: 'HB00104',
    date: '2026-10-18',
    status: 'completed',
    message: 'Đã nhận hàng ngày 18/10/2026',
    shippingFee: 25000,
    lines: [
      { name: 'Tôm sú', variant: 'Khay 500g', qty: 1, price: 180000, image: IMG.tom },
      { name: 'Chả mực Hạ Long', variant: 'Khay 500g', qty: 1, price: 110000, image: IMG.cha },
    ],
  },
  {
    code: 'HB00097',
    date: '2026-09-02',
    status: 'cancelled',
    message: 'Bạn đã hủy đơn này',
    shippingFee: 25000,
    lines: [
      { name: 'Cá thu cắt khúc', variant: 'Khay 500g', qty: 2, price: 150000, image: IMG.ca },
    ],
  },
]

/** price = null: cửa hàng chưa báo giá */
export const DEMO_REQUESTS = [
  {
    code: 'YC-0007',
    date: '2026-11-08',
    wantDate: '2026-11-12',
    status: 'reviewing',
    message: 'Cửa hàng sẽ phản hồi trước ngày 10/11',
    lines: [
      { name: 'Chả mực Hạ Long', variant: 'Khay 500g', qty: 30, price: null, image: IMG.cha },
    ],
  },
  {
    code: 'YC-0005',
    date: '2026-11-06',
    wantDate: '2026-11-10',
    status: 'confirmed',
    message: 'Đã chốt giá, thanh toán để cửa hàng chuẩn bị hàng',
    lines: [
      { name: 'Chả tôm', variant: 'Khay 1kg', qty: 12, price: 200000, image: IMG.tom },
    ],
  },
  {
    code: 'YC-0003',
    date: '2026-10-17',
    wantDate: '2026-10-19',
    status: 'rejected',
    message: 'Cửa hàng không đáp ứng được yêu cầu này',
    reason:
      'Lượng yêu cầu vượt khả năng chế biến trong 2 ngày. Bạn có thể gửi lại với số lượng nhỏ hơn hoặc ngày nhận muộn hơn.',
    lines: [
      { name: 'Chả mực Hạ Long', variant: 'Khay 1kg', qty: 30, price: null, image: IMG.cha },
    ],
  },
]
