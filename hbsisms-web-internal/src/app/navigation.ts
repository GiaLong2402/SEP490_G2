import { BarChart3, Boxes, ClipboardCheck, ClipboardList, ClipboardPen, CreditCard, Factory, FileText, HandCoins, History, LayoutDashboard, PackagePlus, PackageX, RotateCcw, ScanBarcode, Settings, ShieldCheck, ShoppingCart, Star, Store, Tags, TicketPercent, Truck, Users, UsersRound, Warehouse, type LucideIcon } from 'lucide-react';
import type { Permission } from '@/shared/domain/enums';

export interface NavItem {
  to: string;
  label: string;
  icon: LucideIcon;
  permission?: Permission | Permission[];
}

export interface NavGroup {
  label?: string;
  items: NavItem[];
}

export const navigation: NavGroup[] = [
  { items: [{ to: '/', label: 'Tổng quan', icon: LayoutDashboard }] },
  {
    label: 'Bán hàng',
    items: [
      { to: '/pos', label: 'Bán tại quầy', icon: ScanBarcode, permission: 'orders' },
      { to: '/requests', label: 'Yêu cầu', icon: ClipboardList, permission: 'orders' },
      { to: '/quotes', label: 'Báo giá', icon: FileText, permission: 'orders' },
      { to: '/orders', label: 'Đơn hàng', icon: ShoppingCart, permission: 'orders' },
      { to: '/deliveries', label: 'Giao hàng', icon: Truck, permission: ['orders', 'inventory'] },
      { to: '/returns', label: 'Trả hàng', icon: RotateCcw, permission: 'orders' },
    ],
  },
  {
    label: 'Kho',
    items: [
      { to: '/inventory', label: 'Tồn kho', icon: Boxes, permission: 'inventory' },
      { to: '/receipts', label: 'Nhập kho', icon: PackagePlus, permission: 'inventory' },
      { to: '/stocktakes', label: 'Kiểm kê', icon: ClipboardPen, permission: 'inventory' },
      { to: '/damage', label: 'Báo hỏng', icon: PackageX, permission: 'inventory' },
      { to: '/suppliers', label: 'Nhà cung cấp', icon: Factory, permission: 'inventory' },
      { to: '/warehouses', label: 'Kho và vị trí', icon: Warehouse, permission: 'inventory' },
    ],
  },
  {
    label: 'Tài chính',
    items: [
      { to: '/payments', label: 'Thanh toán', icon: CreditCard, permission: 'debt' },
      { to: '/debt', label: 'Công nợ', icon: HandCoins, permission: 'debt' },
    ],
  },
  {
    label: 'Danh mục',
    items: [
      { to: '/customers', label: 'Khách hàng', icon: Users, permission: 'customers' },
      { to: '/products', label: 'Sản phẩm', icon: Store, permission: 'products' },
      { to: '/categories', label: 'Loại sản phẩm', icon: Tags, permission: 'products' },
      { to: '/vouchers', label: 'Voucher', icon: TicketPercent, permission: 'customers' },
      { to: '/reviews', label: 'Đánh giá', icon: Star, permission: 'customers' },
    ],
  },
  {
    label: 'Điều hành',
    items: [
      { to: '/approvals', label: 'Phê duyệt', icon: ClipboardCheck, permission: 'approve' },
      { to: '/reports', label: 'Báo cáo', icon: BarChart3, permission: 'reports' },
    ],
  },
  {
    label: 'Quản trị',
    items: [
      { to: '/admin/users', label: 'Người dùng', icon: UsersRound, permission: 'admin' },
      { to: '/admin/roles', label: 'Phân quyền', icon: ShieldCheck, permission: 'admin' },
      { to: '/admin/config', label: 'Cấu hình', icon: Settings, permission: 'admin' },
      { to: '/admin/audit', label: 'Nhật ký', icon: History, permission: 'admin' },
    ],
  },
];
