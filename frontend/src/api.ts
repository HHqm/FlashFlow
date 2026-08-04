import type {
  ApiEnvelope,
  Campaign,
  CampaignInput,
  Category,
  LoginData,
  Notification,
  Order,
  Product,
  ProductInput,
  ProductVariant,
  ReturnRequest,
  Review,
  VariantInput,
} from "./types";

export class ApiError extends Error {
  code: string;
  traceId?: string;

  constructor(message: string, code = "REQUEST_FAILED", traceId?: string) {
    super(message);
    this.name = "ApiError";
    this.code = code;
    this.traceId = traceId;
  }
}

async function readPayload(response: Response): Promise<unknown> {
  const contentType = response.headers.get("content-type") ?? "";
  return contentType.includes("application/json") ? response.json() : null;
}

async function request<T>(
  path: string,
  options: RequestInit = {},
  token?: string,
): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set("Accept", "application/json");
  if (options.body) headers.set("Content-Type", "application/json");
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const response = await fetch(path, { ...options, headers });
  const payload = (await readPayload(response)) as ApiEnvelope<T> | null;
  if (!response.ok || (payload?.code && payload.code !== "OK")) {
    throw new ApiError(
      payload?.message || `请求失败（HTTP ${response.status}）`,
      payload?.code || `HTTP_${response.status}`,
      payload?.traceId,
    );
  }
  if (!payload) throw new ApiError("服务返回了无法解析的响应");
  return payload.data;
}

export function login(username: string, password: string): Promise<LoginData> {
  return request<LoginData>("/api/v1/auth/login", {
    method: "POST",
    body: JSON.stringify({ username, password }),
  });
}

export const listProducts = (keyword = "", categoryId?: number) => {
  const params = new URLSearchParams();
  if (keyword.trim()) params.set("keyword", keyword.trim());
  if (categoryId) params.set("categoryId", String(categoryId));
  const query = params.toString();
  return request<Product[]>(`/api/v1/products${query ? `?${query}` : ""}`);
};
export const listCategories = () => request<Category[]>("/api/v1/categories");
export const listCampaigns = (keyword = "", categoryId?: number) => {
  const params = new URLSearchParams();
  if (keyword.trim()) params.set("keyword", keyword.trim());
  if (categoryId) params.set("categoryId", String(categoryId));
  const query = params.toString();
  return request<Campaign[]>(`/api/v1/campaigns${query ? `?${query}` : ""}`);
};
export const listOrders = (token: string) => request<Order[]>("/api/v1/orders", {}, token);
export const listReturns = (token: string) =>
  request<ReturnRequest[]>("/api/v1/returns", {}, token);
export const listMyReviews = (token: string) =>
  request<Review[]>("/api/v1/reviews", {}, token);

export function listProductReviews(productId: number): Promise<Review[]> {
  return request<Review[]>(`/api/v1/products/${productId}/reviews`);
}

export function placeOrder(
  campaignId: number,
  quantity: number,
  idempotencyKey: string,
  token: string,
): Promise<Order> {
  return request<Order>(
    `/api/v1/campaigns/${campaignId}/orders`,
    {
      method: "POST",
      headers: { "Idempotency-Key": idempotencyKey },
      body: JSON.stringify({ quantity }),
    },
    token,
  );
}

export function payOrder(orderNo: string, token: string): Promise<Order> {
  return request<Order>(
    `/api/v1/orders/${encodeURIComponent(orderNo)}/pay`,
    { method: "POST" },
    token,
  );
}

export function cancelOrder(orderNo: string, reason: string, token: string): Promise<Order> {
  return request<Order>(
    `/api/v1/orders/${encodeURIComponent(orderNo)}/cancel`,
    { method: "POST", body: JSON.stringify({ reason }) },
    token,
  );
}

export function requestReturn(
  orderNo: string,
  reason: string,
  token: string,
): Promise<ReturnRequest> {
  return request<ReturnRequest>(
    `/api/v1/orders/${encodeURIComponent(orderNo)}/returns`,
    { method: "POST", body: JSON.stringify({ reason }) },
    token,
  );
}

export function createReview(
  orderNo: string,
  rating: number,
  content: string,
  token: string,
): Promise<Review> {
  return request<Review>(
    `/api/v1/orders/${encodeURIComponent(orderNo)}/reviews`,
    { method: "POST", body: JSON.stringify({ rating, content }) },
    token,
  );
}

export const listAdminProducts = (token: string) =>
  request<Product[]>("/api/v1/admin/products", {}, token);
export const listAdminCategories = (token: string) =>
  request<Category[]>("/api/v1/admin/categories", {}, token);
export const listAdminCampaigns = (token: string) =>
  request<Campaign[]>("/api/v1/admin/campaigns", {}, token);
export const listAdminOrders = (token: string) =>
  request<Order[]>("/api/v1/admin/orders", {}, token);
export const listAdminReturns = (token: string) =>
  request<ReturnRequest[]>("/api/v1/admin/returns", {}, token);
export const listAdminReviews = (token: string) =>
  request<Review[]>("/api/v1/admin/reviews", {}, token);

export function createCategory(
  name: string,
  sortOrder: number,
  token: string,
): Promise<Category> {
  return request<Category>(
    "/api/v1/admin/categories",
    { method: "POST", body: JSON.stringify({ name, sortOrder }) },
    token,
  );
}

export function setCategoryStatus(id: number, active: boolean, token: string) {
  return request<Category>(
    `/api/v1/admin/categories/${id}/status`,
    { method: "PATCH", body: JSON.stringify({ active }) },
    token,
  );
}

export function createProduct(payload: ProductInput, token: string): Promise<Product> {
  return request<Product>(
    "/api/v1/admin/products",
    { method: "POST", body: JSON.stringify(payload) },
    token,
  );
}

export function setProductStatus(id: number, active: boolean, token: string) {
  return request<Product>(
    `/api/v1/admin/products/${id}/status`,
    { method: "PATCH", body: JSON.stringify({ active }) },
    token,
  );
}

export function addVariant(
  productId: number,
  payload: VariantInput,
  token: string,
): Promise<ProductVariant> {
  return request<ProductVariant>(
    `/api/v1/admin/products/${productId}/variants`,
    { method: "POST", body: JSON.stringify(payload) },
    token,
  );
}

export function setVariantStock(id: number, stock: number, token: string) {
  return request<ProductVariant>(
    `/api/v1/admin/variants/${id}/stock`,
    { method: "PATCH", body: JSON.stringify({ stock }) },
    token,
  );
}

export function setVariantPrice(id: number, price: number, token: string) {
  return request<ProductVariant>(
    `/api/v1/admin/variants/${id}/price`,
    { method: "PATCH", body: JSON.stringify({ price }) },
    token,
  );
}

export function setVariantStatus(id: number, active: boolean, token: string) {
  return request<ProductVariant>(
    `/api/v1/admin/variants/${id}/status`,
    { method: "PATCH", body: JSON.stringify({ active }) },
    token,
  );
}

export function createCampaign(payload: CampaignInput, token: string): Promise<Campaign> {
  return request<Campaign>(
    "/api/v1/admin/campaigns",
    {
      method: "POST",
      body: JSON.stringify({
        ...payload,
        startsAt: new Date(payload.startsAt).toISOString(),
        endsAt: new Date(payload.endsAt).toISOString(),
      }),
    },
    token,
  );
}

export function setCampaignStatus(id: number, status: string, token: string) {
  return request<Campaign>(
    `/api/v1/admin/campaigns/${id}/status`,
    { method: "PATCH", body: JSON.stringify({ status }) },
    token,
  );
}

export function warmupCampaign(campaignId: number, token: string): Promise<void> {
  return request<void>(
    `/api/v1/admin/campaigns/${campaignId}/warmup`,
    { method: "POST" },
    token,
  );
}

export function decideReturn(
  id: number,
  approved: boolean,
  note: string,
  token: string,
): Promise<ReturnRequest> {
  return request<ReturnRequest>(
    `/api/v1/admin/returns/${id}/decision`,
    { method: "PATCH", body: JSON.stringify({ approved, note }) },
    token,
  );
}

export function setReviewVisibility(id: number, visible: boolean, token: string) {
  return request<Review>(
    `/api/v1/admin/reviews/${id}/visibility`,
    { method: "PATCH", body: JSON.stringify({ visible }) },
    token,
  );
}

export function replyReview(id: number, reply: string, token: string) {
  return request<Review>(
    `/api/v1/admin/reviews/${id}/reply`,
    { method: "PATCH", body: JSON.stringify({ reply }) },
    token,
  );
}

export async function listNotifications(userId: number): Promise<Notification[]> {
  const response = await fetch(
    `/notification-api?userId=${encodeURIComponent(userId)}`,
    { headers: { Accept: "application/json" } },
  );
  if (!response.ok) throw new ApiError(`通知服务不可用（HTTP ${response.status}）`);
  return response.json();
}

export async function getHealth(
  service: "gateway" | "trade" | "notification",
): Promise<"UP" | "DOWN"> {
  try {
    const response = await fetch(`/platform-health/${service}`, {
      headers: { Accept: "application/json" },
      cache: "no-store",
    });
    if (!response.ok) return "DOWN";
    const payload = (await response.json()) as { status?: string };
    return payload.status === "UP" ? "UP" : "DOWN";
  } catch {
    return "DOWN";
  }
}
