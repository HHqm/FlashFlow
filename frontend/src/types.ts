export interface ApiEnvelope<T> {
  code: string;
  message: string;
  data: T;
  timestamp: string;
  traceId: string;
}

export interface LoginData {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  role: "USER" | "ADMIN";
}

export interface Session {
  accessToken: string;
  username: string;
  role: "USER" | "ADMIN";
  userId: number;
}

export interface Category {
  id: number;
  name: string;
  sortOrder: number;
  active: boolean;
}

export interface ProductVariant {
  id: number;
  skuCode: string;
  colorName: string;
  specification: string;
  price: number;
  stock: number;
  active: boolean;
}

export interface Product {
  id: number;
  categoryId: number;
  categoryName: string;
  name: string;
  description: string;
  imageUrl: string;
  active: boolean;
  minPrice: number;
  totalStock: number;
  averageRating: number;
  reviewCount: number;
  variants: ProductVariant[];
}

export interface Campaign {
  id: number;
  productId: number;
  variantId: number;
  productName: string;
  categoryName: string;
  imageUrl: string;
  skuCode: string;
  colorName: string;
  specification: string;
  originalPrice: number;
  flashPrice: number;
  totalStock: number;
  remainingStock: number;
  purchaseLimit: number;
  startsAt: string;
  endsAt: string;
  status: "ACTIVE" | "PAUSED" | "ENDED" | string;
  productActive: boolean;
  variantActive: boolean;
}

export interface Order {
  orderNo: string;
  campaignId: number;
  productId: number;
  variantId: number | null;
  productName: string;
  variantLabel: string;
  quantity: number;
  amount: number;
  status:
    | "PENDING_PAYMENT"
    | "PAID"
    | "SHIPPED"
    | "RECEIVED"
    | "REFUND_REQUESTED"
    | "REFUNDED"
    | "CANCELLED"
    | string;
  createdAt: string;
  paidAt: string | null;
  cancelledAt: string | null;
  cancelReason: string | null;
  refundedAt: string | null;
  recipientName: string;
  recipientPhone: string;
  shippingAddress: string;
  carrier: string | null;
  trackingNo: string | null;
  shippedAt: string | null;
  receivedAt: string | null;
  replayed: boolean;
}

export interface ShippingAddress {
  id: number;
  recipientName: string;
  phone: string;
  province: string;
  city: string;
  district: string;
  detailAddress: string;
  postalCode: string | null;
  defaultAddress: boolean;
}

export interface ShippingAddressInput {
  recipientName: string;
  phone: string;
  province: string;
  city: string;
  district: string;
  detailAddress: string;
  postalCode: string;
  defaultAddress: boolean;
}

export interface ReturnRequest {
  id: number;
  returnNo: string;
  orderNo: string;
  userId: number;
  productName: string;
  variantLabel: string;
  quantity: number;
  amount: number;
  reason: string;
  status: "PENDING" | "APPROVED" | "REJECTED";
  adminNote: string | null;
  requestedAt: string;
  processedAt: string | null;
}

export interface Review {
  id: number;
  orderNo: string;
  userId: number;
  reviewerName: string;
  productId: number;
  productName: string;
  variantLabel: string;
  rating: number;
  content: string;
  visible: boolean;
  merchantReply: string | null;
  repliedAt: string | null;
  createdAt: string;
}

export interface Notification {
  id: number;
  eventId: string;
  channel: string;
  templateCode: "ORDER_CREATED" | "ORDER_PAID" | string;
  content: string;
  status: string;
  createdAt: string;
}

export interface ServiceHealth {
  key: "gateway" | "trade" | "notification";
  name: string;
  status: "UP" | "DOWN" | "CHECKING";
}

export interface ToastState {
  type: "success" | "error" | "info";
  message: string;
}

export interface VariantInput {
  skuCode: string;
  colorName: string;
  specification: string;
  price: number;
  stock: number;
}

export interface ProductInput {
  categoryId: number;
  name: string;
  description: string;
  imageUrl: string;
  variants: VariantInput[];
}

export interface CampaignInput {
  variantId: number;
  flashPrice: number;
  totalStock: number;
  purchaseLimit: number;
  startsAt: string;
  endsAt: string;
}
