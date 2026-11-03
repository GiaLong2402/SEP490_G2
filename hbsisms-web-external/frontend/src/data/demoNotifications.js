/**
 * Thông báo demo — sau này thay bằng API Spring Boot (vd: GET /api/notifications).
 *
 * Mỗi thông báo:
 * - id:      mã duy nhất
 * - type:    loại thông báo => quyết định icon (xem NOTIFICATION_ICONS trong NotificationBell.jsx)
 * - title:   tiêu đề (chữ đậm)
 * - message: nội dung ngắn
 * - time:    thời gian hiển thị
 * - isRead:  false = chưa đọc (nền xanh nhạt + chấm xanh)
 */
export const DEMO_NOTIFICATIONS = [
  {
    id: 'n1',
    type: 'order-confirmed',
    title: 'Đơn hàng đã được xác nhận',
    message: 'Đơn #HB1026 đang được cửa hàng chuẩn bị.',
    time: '5 phút trước',
    isRead: false,
  },
  {
    id: 'n2',
    type: 'order-shipping',
    title: 'Đơn hàng đang giao',
    message: 'Đơn #HB1024 đang trên đường đến bạn.',
    time: '30 phút trước',
    isRead: false,
  },
  {
    id: 'n3',
    type: 'bulk-request',
    title: 'Yêu cầu đặt số lượng lớn',
    message: 'Cửa hàng đã phản hồi yêu cầu của bạn.',
    time: '1 giờ trước',
    isRead: false,
  },
  {
    id: 'n4',
    type: 'payment-success',
    title: 'Thanh toán thành công',
    message: 'Đã nhận thanh toán cho đơn #HB1026.',
    time: '2 giờ trước',
    isRead: true,
  },
  {
    id: 'n5',
    type: 'order-completed',
    title: 'Đơn hàng đã hoàn thành',
    message: 'Cảm ơn bạn đã mua hàng tại Hòa Bình.',
    time: 'Hôm qua',
    isRead: true,
  },
  {
    id: 'n6',
    type: 'review-request',
    title: 'Đánh giá đơn hàng',
    message: 'Hãy chia sẻ cảm nhận của bạn về sản phẩm.',
    time: 'Hôm qua',
    isRead: true,
  },
]
