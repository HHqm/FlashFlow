<script setup lang="ts">
import { onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { AlertCircle, CheckCircle2, Info, X } from "@lucide/vue";
import LoginView from "./components/LoginView.vue";
import UserStorefront from "./components/UserStorefront.vue";
import AdminConsole from "./components/AdminConsole.vue";
import {
  ApiError,
  addVariant,
  cancelOrder,
  createCampaign,
  createCategory,
  createProduct,
  createReview,
  decideReturn,
  getHealth,
  listAdminCampaigns,
  listAdminCategories,
  listAdminOrders,
  listAdminProducts,
  listAdminReturns,
  listAdminReviews,
  listCampaigns,
  listCategories,
  listMyReviews,
  listNotifications,
  listOrders,
  listProducts,
  listReturns,
  login,
  payOrder,
  placeOrder,
  replyReview,
  requestReturn,
  setCampaignStatus,
  setCategoryStatus,
  setProductStatus,
  setReviewVisibility,
  setVariantPrice,
  setVariantStatus,
  setVariantStock,
  warmupCampaign,
} from "./api";
import type {
  Campaign,
  CampaignInput,
  Category,
  Notification,
  Order,
  Product,
  ProductInput,
  ReturnRequest,
  Review,
  ServiceHealth,
  Session,
  ToastState,
  VariantInput,
} from "./types";

const SESSION_KEY = "flashflow.session";
const session = ref<Session | null>(restoreSession());
const products = ref<Product[]>([]);
const categories = ref<Category[]>([]);
const campaigns = ref<Campaign[]>([]);
const orders = ref<Order[]>([]);
const returns = ref<ReturnRequest[]>([]);
const reviews = ref<Review[]>([]);
const notifications = ref<Notification[]>([]);
const services = reactive<ServiceHealth[]>([
  { key: "gateway", name: "Gateway", status: "CHECKING" },
  { key: "trade", name: "Trade", status: "CHECKING" },
  { key: "notification", name: "Notify", status: "CHECKING" },
]);

const loginBusy = ref(false);
const loginError = ref("");
const loading = ref(false);
const actionBusy = ref("");
const payingOrderNo = ref("");
const lastUpdated = ref<Date | null>(null);
const toast = ref<ToastState | null>(null);
let refreshTimer: number | undefined;
let toastTimer: number | undefined;

function restoreSession(): Session | null {
  try {
    const value = sessionStorage.getItem(SESSION_KEY);
    return value ? (JSON.parse(value) as Session) : null;
  } catch {
    sessionStorage.removeItem(SESSION_KEY);
    return null;
  }
}

function decodeUserId(token: string): number {
  try {
    const payload = token.split(".")[1];
    const normalized = payload.replace(/-/g, "+").replace(/_/g, "/");
    return Number(JSON.parse(decodeURIComponent(escape(atob(normalized)))).uid) || 1;
  } catch {
    return 1;
  }
}

function messageFrom(error: unknown): string {
  if (error instanceof ApiError) {
    return error.traceId
      ? `${error.message}（Trace ${error.traceId.slice(0, 8)}）`
      : error.message;
  }
  return error instanceof Error ? error.message : "操作失败，请稍后再试";
}

function showToast(type: ToastState["type"], message: string) {
  toast.value = { type, message };
  if (toastTimer) window.clearTimeout(toastTimer);
  toastTimer = window.setTimeout(() => (toast.value = null), 4200);
}

async function refreshHealth() {
  const statuses = await Promise.all(
    services.map(async (service) => ({
      key: service.key,
      status: await getHealth(service.key),
    })),
  );
  statuses.forEach(({ key, status }) => {
    const service = services.find((item) => item.key === key);
    if (service) service.status = status;
  });
}

async function refreshData() {
  if (!session.value) return;
  if (session.value.role === "ADMIN") {
    const [categoryData, productData, campaignData, orderData, returnData, reviewData] =
      await Promise.all([
      listAdminCategories(session.value.accessToken),
      listAdminProducts(session.value.accessToken),
      listAdminCampaigns(session.value.accessToken),
      listAdminOrders(session.value.accessToken),
      listAdminReturns(session.value.accessToken),
      listAdminReviews(session.value.accessToken),
    ]);
    categories.value = categoryData;
    products.value = productData;
    campaigns.value = campaignData;
    orders.value = orderData;
    returns.value = returnData;
    reviews.value = reviewData;
    notifications.value = [];
  } else {
    const [categoryData, productData, campaignData, orderData, returnData, reviewData, notificationData] =
      await Promise.all([
        listCategories(),
        listProducts(),
        listCampaigns(),
        listOrders(session.value.accessToken),
        listReturns(session.value.accessToken),
        listMyReviews(session.value.accessToken),
        listNotifications(session.value.userId),
      ]);
    categories.value = categoryData;
    products.value = productData;
    campaigns.value = campaignData;
    orders.value = orderData;
    returns.value = returnData;
    reviews.value = reviewData;
    notifications.value = notificationData;
  }
}

async function refreshAll(options: { quiet?: boolean } = {}) {
  if (loading.value) return;
  loading.value = true;
  try {
    await Promise.all([refreshHealth(), refreshData()]);
    lastUpdated.value = new Date();
  } catch (error) {
    if (!options.quiet) showToast("error", messageFrom(error));
  } finally {
    loading.value = false;
  }
}

async function handleLogin(credentials: { username: string; password: string }) {
  loginBusy.value = true;
  loginError.value = "";
  try {
    const data = await login(credentials.username, credentials.password);
    session.value = {
      accessToken: data.accessToken,
      username: credentials.username,
      role: data.role,
      userId: decodeUserId(data.accessToken),
    };
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(session.value));
    await refreshAll();
    showToast(
      "success",
      data.role === "ADMIN" ? "已进入商城运营后台。" : "欢迎进入 FlashFlow 秒杀商城。",
    );
    startPolling();
  } catch (error) {
    loginError.value = messageFrom(error);
  } finally {
    loginBusy.value = false;
  }
}

function handleLogout() {
  sessionStorage.removeItem(SESSION_KEY);
  session.value = null;
  products.value = [];
  categories.value = [];
  campaigns.value = [];
  orders.value = [];
  returns.value = [];
  reviews.value = [];
  notifications.value = [];
  stopPolling();
}

async function runAction(key: string, successMessage: string, task: () => Promise<unknown>) {
  if (!session.value || actionBusy.value) return;
  actionBusy.value = key;
  try {
    await task();
    showToast("success", successMessage);
    await refreshAll({ quiet: true });
  } catch (error) {
    showToast("error", messageFrom(error));
    throw error;
  } finally {
    actionBusy.value = "";
  }
}

async function handlePlaceOrder(campaignId: number, quantity: number) {
  if (!session.value || session.value.role !== "USER") return;
  await runAction(`order-${campaignId}`, "秒杀订单创建成功，请在订单中心完成支付。", async () => {
    const key = `web-${Date.now()}-${crypto.randomUUID().slice(0, 8)}`;
    await placeOrder(campaignId, quantity, key, session.value!.accessToken);
    window.setTimeout(() => refreshData().catch(() => undefined), 1200);
  }).catch(() => undefined);
}

async function handlePay(orderNo: string) {
  if (!session.value || session.value.role !== "USER" || payingOrderNo.value) return;
  payingOrderNo.value = orderNo;
  try {
    await payOrder(orderNo, session.value.accessToken);
    showToast("success", "支付成功，Kafka 正在异步生成履约通知。");
    await refreshAll({ quiet: true });
    window.setTimeout(() => refreshData().catch(() => undefined), 1400);
  } catch (error) {
    showToast("error", messageFrom(error));
  } finally {
    payingOrderNo.value = "";
  }
}

async function handleCancel(orderNo: string, reason: string) {
  if (!session.value || session.value.role !== "USER") return;
  await runAction(`cancel-${orderNo}`, "订单已取消，库存已恢复。", () =>
    cancelOrder(orderNo, reason, session.value!.accessToken)).catch(() => undefined);
}

async function handleReturn(orderNo: string, reason: string) {
  if (!session.value || session.value.role !== "USER") return;
  await runAction(`return-${orderNo}`, "退款申请已提交，等待管理员审核。", () =>
    requestReturn(orderNo, reason, session.value!.accessToken)).catch(() => undefined);
}

async function handleReview(orderNo: string, rating: number, content: string) {
  if (!session.value || session.value.role !== "USER") return;
  await runAction(`review-${orderNo}`, "评价已发布，感谢你的反馈。", () =>
    createReview(orderNo, rating, content, session.value!.accessToken)).catch(() => undefined);
}

const token = () => session.value!.accessToken;
const adminActions = {
  createCategory: (payload: { name: string; sortOrder: number }) =>
    runAction("category-create", "商品分类已创建。", () =>
      createCategory(payload.name, payload.sortOrder, token())),
  setCategoryStatus: (id: number, active: boolean) =>
    runAction(`category-${id}`, active ? "分类已启用。" : "分类已停用。", () =>
      setCategoryStatus(id, active, token())),
  createProduct: (payload: ProductInput) =>
    runAction("product-create", "商品及 SKU 已创建。", () =>
      createProduct(payload, token())),
  setProductStatus: (id: number, active: boolean) =>
    runAction(`product-${id}`, active ? "商品已上架。" : "商品已下架。", () =>
      setProductStatus(id, active, token())),
  addVariant: (productId: number, payload: VariantInput) =>
    runAction(`variant-add-${productId}`, "新颜色规格已添加。", () =>
      addVariant(productId, payload, token())),
  setVariantStock: (id: number, stock: number) =>
    runAction(`stock-${id}`, "SKU 库存已更新。", () =>
      setVariantStock(id, stock, token())),
  setVariantPrice: (id: number, price: number) =>
    runAction(`price-${id}`, "SKU 售价已更新。", () =>
      setVariantPrice(id, price, token())),
  setVariantStatus: (id: number, active: boolean) =>
    runAction(`variant-${id}`, active ? "SKU 已启用。" : "SKU 已停用。", () =>
      setVariantStatus(id, active, token())),
  createCampaign: (payload: CampaignInput) =>
    runAction("campaign-create", "秒杀活动已创建并预热 Redis 库存。", () =>
      createCampaign(payload, token())),
  setCampaignStatus: (id: number, status: string) =>
    runAction(`campaign-${id}`, "活动状态已更新。", () =>
      setCampaignStatus(id, status, token())),
  warmup: (id: number) =>
    runAction(`warmup-${id}`, "Redis 活动库存已重建。", () =>
      warmupCampaign(id, token())),
  decideReturn: (id: number, approved: boolean, note: string) =>
    runAction(`return-decision-${id}`, approved ? "退款已通过并恢复库存。" : "退款申请已驳回。", () =>
      decideReturn(id, approved, note, token())),
  setReviewVisibility: (id: number, visible: boolean) =>
    runAction(`review-visible-${id}`, visible ? "评价已公开。" : "评价已隐藏。", () =>
      setReviewVisibility(id, visible, token())),
  replyReview: (id: number, reply: string) =>
    runAction(`review-reply-${id}`, "商家回复已保存。", () =>
      replyReview(id, reply, token())),
};

function startPolling() {
  stopPolling();
  refreshTimer = window.setInterval(() => refreshAll({ quiet: true }), 10000);
}
function stopPolling() {
  if (refreshTimer) window.clearInterval(refreshTimer);
  refreshTimer = undefined;
}

onMounted(async () => {
  await refreshHealth();
  if (session.value) {
    await refreshAll();
    startPolling();
  }
});
onBeforeUnmount(() => {
  stopPolling();
  if (toastTimer) window.clearTimeout(toastTimer);
});
</script>

<template>
  <LoginView
    v-if="!session"
    :busy="loginBusy"
    :error="loginError"
    :services="services"
    @login="handleLogin"
  />

  <AdminConsole
    v-else-if="session.role === 'ADMIN'"
    :session="session"
    :products="products"
    :categories="categories"
    :campaigns="campaigns"
    :orders="orders"
    :returns="returns"
    :reviews="reviews"
    :services="services"
    :loading="loading"
    :action-busy="actionBusy"
    :last-updated="lastUpdated"
    @logout="handleLogout"
    @refresh="refreshAll"
    @create-category="adminActions.createCategory"
    @set-category-status="adminActions.setCategoryStatus"
    @create-product="adminActions.createProduct"
    @set-product-status="adminActions.setProductStatus"
    @add-variant="adminActions.addVariant"
    @set-variant-stock="adminActions.setVariantStock"
    @set-variant-price="adminActions.setVariantPrice"
    @set-variant-status="adminActions.setVariantStatus"
    @create-campaign="adminActions.createCampaign"
    @set-campaign-status="adminActions.setCampaignStatus"
    @warmup="adminActions.warmup"
    @decide-return="adminActions.decideReturn"
    @set-review-visibility="adminActions.setReviewVisibility"
    @reply-review="adminActions.replyReview"
  />

  <UserStorefront
    v-else
    :session="session"
    :products="products"
    :categories="categories"
    :campaigns="campaigns"
    :orders="orders"
    :returns="returns"
    :reviews="reviews"
    :notifications="notifications"
    :services="services"
    :loading="loading"
    :action-busy="actionBusy"
    :paying-order-no="payingOrderNo"
    :last-updated="lastUpdated"
    @logout="handleLogout"
    @refresh="refreshAll"
    @place-order="handlePlaceOrder"
    @pay="handlePay"
    @cancel="handleCancel"
    @request-return="handleReturn"
    @review="handleReview"
  />

  <Transition name="toast">
    <div v-if="toast" class="toast" :class="toast.type" role="status" data-testid="toast">
      <CheckCircle2 v-if="toast.type === 'success'" :size="19" />
      <AlertCircle v-else-if="toast.type === 'error'" :size="19" />
      <Info v-else :size="19" />
      <span>{{ toast.message }}</span>
      <button type="button" aria-label="关闭提示" @click="toast = null"><X :size="16" /></button>
    </div>
  </Transition>
</template>
