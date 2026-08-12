<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import {
  Activity,
  Bell,
  Check,
  ChevronRight,
  Clock3,
  CreditCard,
  Headphones,
  HeartPulse,
  Home,
  LogOut,
  MapPin,
  Minus,
  PackageCheck,
  PackageOpen,
  Plus,
  Radio,
  RefreshCw,
  RotateCcw,
  Search,
  ShieldCheck,
  ShoppingBag,
  Sparkles,
  Star,
  TimerReset,
  Trash2,
  Truck,
  UserRound,
  Watch,
  Zap,
} from "@lucide/vue";
import type {
  Campaign,
  Category,
  Notification,
  Order,
  Product,
  ReturnRequest,
  Review,
  ServiceHealth,
  Session,
  ShippingAddress,
  ShippingAddressInput,
} from "../types";

const props = defineProps<{
  session: Session;
  products: Product[];
  categories: Category[];
  campaigns: Campaign[];
  orders: Order[];
  returns: ReturnRequest[];
  reviews: Review[];
  notifications: Notification[];
  addresses: ShippingAddress[];
  services: ServiceHealth[];
  loading: boolean;
  actionBusy: string;
  payingOrderNo: string;
  lastUpdated: Date | null;
}>();

const emit = defineEmits<{
  logout: [];
  refresh: [];
  placeOrder: [campaignId: number, quantity: number, addressId: number];
  pay: [orderNo: string];
  cancel: [orderNo: string, reason: string];
  requestReturn: [orderNo: string, reason: string];
  review: [orderNo: string, rating: number, content: string];
  createAddress: [payload: ShippingAddressInput];
  updateAddress: [id: number, payload: ShippingAddressInput];
  setDefaultAddress: [id: number];
  deleteAddress: [id: number];
  receive: [orderNo: string];
}>();

const selectedCategory = ref(0);
const keyword = ref("");
const sortMode = ref("recommended");
const quantities = reactive<Record<number, number>>({});
const orderAction = ref<{ type: "cancel" | "return" | "review"; order: Order } | null>(null);
const actionReason = ref("");
const reviewRating = ref(5);
const reviewContent = ref("");
const checkoutCampaign = ref<Campaign | null>(null);
const checkoutAddressId = ref(0);
const editingAddressId = ref<number | null>(null);
const addressForm = reactive<ShippingAddressInput>({
  recipientName: "",
  phone: "",
  province: "",
  city: "",
  district: "",
  detailAddress: "",
  postalCode: "",
  defaultAddress: false,
});
const now = ref(Date.now());
let timer: number | undefined;

onMounted(() => {
  timer = window.setInterval(() => (now.value = Date.now()), 1000);
});
onBeforeUnmount(() => {
  if (timer) window.clearInterval(timer);
});

const filteredCampaigns = computed(() => {
  const result = props.campaigns.filter((campaign) => {
    const product = props.products.find((item) => item.id === campaign.productId);
    const categoryMatch = !selectedCategory.value || product?.categoryId === selectedCategory.value;
    const search = keyword.value.trim().toLowerCase();
    const keywordMatch =
      !search ||
      campaign.productName.toLowerCase().includes(search) ||
      campaign.skuCode.toLowerCase().includes(search) ||
      campaign.categoryName.toLowerCase().includes(search) ||
      campaign.colorName.toLowerCase().includes(search) ||
      campaign.specification.toLowerCase().includes(search) ||
      product?.description.toLowerCase().includes(search);
    return categoryMatch && keywordMatch;
  });
  return result.sort((left, right) => {
    if (sortMode.value === "price-asc") return left.flashPrice - right.flashPrice;
    if (sortMode.value === "price-desc") return right.flashPrice - left.flashPrice;
    if (sortMode.value === "rating") {
      return (productFor(right.productId)?.averageRating ?? 0)
        - (productFor(left.productId)?.averageRating ?? 0);
    }
    return left.id - right.id;
  });
});

const paidOrders = computed(() =>
  props.orders.filter((order) => ["PAID", "SHIPPED", "RECEIVED"].includes(order.status)).length,
);
const totalRemaining = computed(() =>
  props.campaigns.reduce((sum, campaign) => sum + campaign.remainingStock, 0),
);
const allHealthy = computed(
  () => props.services.length > 0 && props.services.every((item) => item.status === "UP"),
);

function money(value?: number) {
  if (value === undefined || value === null) return "—";
  return new Intl.NumberFormat("zh-CN", {
    style: "currency",
    currency: "CNY",
    minimumFractionDigits: 2,
  }).format(value);
}

function formatTime(value?: string | null) {
  if (!value) return "—";
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
  }).format(new Date(value));
}

function countdown(campaign: Campaign) {
  const target =
    new Date(campaign.startsAt).getTime() > now.value
      ? new Date(campaign.startsAt).getTime()
      : new Date(campaign.endsAt).getTime();
  const remaining = Math.max(0, target - now.value);
  const days = Math.floor(remaining / 86_400_000);
  const hours = Math.floor((remaining % 86_400_000) / 3_600_000);
  const minutes = Math.floor((remaining % 3_600_000) / 60_000);
  const seconds = Math.floor((remaining % 60_000) / 1000);
  const clock = [hours, minutes, seconds].map((item) => String(item).padStart(2, "0")).join(":");
  return `${days ? `${days}天 ` : ""}${clock}`;
}

function campaignPhase(campaign: Campaign) {
  if (campaign.status !== "ACTIVE") return "已结束";
  if (new Date(campaign.startsAt).getTime() > now.value) return "即将开始";
  if (new Date(campaign.endsAt).getTime() <= now.value) return "已结束";
  return "抢购中";
}

function quantity(campaign: Campaign) {
  return quantities[campaign.id] ?? 1;
}

function changeQuantity(campaign: Campaign, delta: number) {
  quantities[campaign.id] = Math.max(
    1,
    Math.min(campaign.purchaseLimit, campaign.remainingStock, quantity(campaign) + delta),
  );
}

function productIcon(category: string) {
  if (category.includes("穿戴")) return Watch;
  if (category.includes("家电")) return Home;
  return Headphones;
}

function statusText(status: string) {
  return (
    {
      PENDING_PAYMENT: "待支付",
      PAID: "已支付",
      SHIPPED: "已发货",
      RECEIVED: "已收货",
      REFUND_REQUESTED: "退款审核中",
      REFUNDED: "已退款",
      CANCELLED: "已关闭",
    }[status] ?? status
  );
}

function productFor(productId: number) {
  return props.products.find((item) => item.id === productId);
}

function reviewFor(orderNo: string) {
  return props.reviews.find((item) => item.orderNo === orderNo);
}

function returnFor(orderNo: string) {
  return props.returns.find((item) => item.orderNo === orderNo);
}

function fullAddress(address: ShippingAddress) {
  return `${address.province}${address.city}${address.district}${address.detailAddress}`;
}

function openCheckout(campaign: Campaign) {
  checkoutCampaign.value = campaign;
  checkoutAddressId.value =
    props.addresses.find((address) => address.defaultAddress)?.id ?? props.addresses[0]?.id ?? 0;
}

function confirmCheckout() {
  if (!checkoutCampaign.value || !checkoutAddressId.value) return;
  emit(
    "placeOrder",
    checkoutCampaign.value.id,
    quantity(checkoutCampaign.value),
    checkoutAddressId.value,
  );
  checkoutCampaign.value = null;
}

function submitAddress() {
  const payload = {
    ...addressForm,
    defaultAddress: addressForm.defaultAddress || props.addresses.length === 0,
  };
  if (editingAddressId.value) {
    emit("updateAddress", editingAddressId.value, payload);
  } else {
    emit("createAddress", payload);
  }
  resetAddressForm();
}

function editAddress(address: ShippingAddress) {
  editingAddressId.value = address.id;
  Object.assign(addressForm, {
    recipientName: address.recipientName,
    phone: address.phone,
    province: address.province,
    city: address.city,
    district: address.district,
    detailAddress: address.detailAddress,
    postalCode: address.postalCode || "",
    defaultAddress: address.defaultAddress,
  });
  document.querySelector("#addresses")?.scrollIntoView({ behavior: "smooth" });
}

function resetAddressForm() {
  editingAddressId.value = null;
  Object.assign(addressForm, {
    recipientName: "", phone: "", province: "", city: "", district: "",
    detailAddress: "", postalCode: "", defaultAddress: false,
  });
}

function openOrderAction(type: "cancel" | "return" | "review", order: Order) {
  orderAction.value = { type, order };
  actionReason.value = "";
  reviewRating.value = 5;
  reviewContent.value = "";
}

function submitOrderAction() {
  if (!orderAction.value) return;
  const { type, order } = orderAction.value;
  if (type === "review") {
    emit("review", order.orderNo, reviewRating.value, reviewContent.value.trim());
  } else if (type === "return") {
    emit("requestReturn", order.orderNo, actionReason.value.trim());
  } else {
    emit("cancel", order.orderNo, actionReason.value.trim() || "客户主动取消");
  }
  orderAction.value = null;
}

function shortOrderNo(value: string) {
  return `${value.slice(0, 9)}…${value.slice(-5)}`;
}

function notificationTitle(code: string) {
  return ({
    ORDER_CREATED: "订单已创建",
    ORDER_PAID: "支付成功",
    ORDER_CANCELLED: "订单已取消",
    ORDER_REFUND_REQUESTED: "退款已申请",
    ORDER_REFUNDED: "退款已通过",
    ORDER_REFUND_REJECTED: "退款未通过",
    ORDER_SHIPPED: "订单已发货",
    ORDER_RECEIVED: "订单已收货",
  }[code] ?? "订单消息");
}
</script>

<template>
  <div class="store-shell">
    <header class="store-header">
      <a class="commerce-brand" href="#home">
        <span><Zap :size="20" fill="currentColor" /></span>
        <strong>FlashFlow</strong>
        <small>秒杀商城</small>
      </a>
      <nav>
        <a href="#home">首页</a>
        <a href="#flash-sale">限时秒杀</a>
        <a href="#addresses">收货地址</a>
        <a href="#orders">我的订单</a>
      </nav>
      <div class="header-search">
        <Search :size="17" />
        <input
          v-model="keyword"
          data-testid="store-search"
          placeholder="搜索名称、类目、颜色或规格"
          aria-label="搜索商品"
        />
      </div>
      <div class="header-user">
        <span class="health-pill" :class="{ danger: !allHealthy }">
          <i></i>{{ allHealthy ? "服务在线" : "服务异常" }}
        </span>
        <span class="avatar"><UserRound :size="17" /></span>
        <span><strong>{{ session.username }}</strong><small>商城用户</small></span>
        <button type="button" aria-label="刷新" :disabled="loading" @click="emit('refresh')">
          <RefreshCw :size="17" :class="{ spin: loading }" />
        </button>
        <button type="button" aria-label="退出登录" @click="emit('logout')"><LogOut :size="17" /></button>
      </div>
    </header>

    <main id="home">
      <section class="store-hero">
        <div class="hero-content">
          <span class="hero-badge"><Sparkles :size="15" /> 本周超级秒杀季</span>
          <h1>好物限时抢，<br /><em>库存实时可见。</em></h1>
          <p>精选数码、穿戴与生活家电。下单库存由 Redis 原子预扣，重复请求自动幂等处理。</p>
          <div class="hero-actions">
            <a href="#flash-sale">立即抢购 <ChevronRight :size="18" /></a>
            <a class="ghost" href="#orders">查看订单</a>
          </div>
          <div class="hero-stats">
            <span><strong>{{ campaigns.length }}</strong><small>在售活动</small></span>
            <span><strong>{{ totalRemaining }}</strong><small>剩余库存</small></span>
            <span><strong>{{ paidOrders }}</strong><small>已支付订单</small></span>
          </div>
        </div>
        <div class="hero-art">
          <span class="art-orbit orbit-a"></span>
          <span class="art-orbit orbit-b"></span>
          <div class="art-product"><Headphones :size="190" :stroke-width="1.1" /></div>
          <div class="floating-card price-float"><small>FLASH PRICE</small><strong>¥399</strong></div>
          <div class="floating-card secure-float"><ShieldCheck :size="19" /><span>原子库存<br /><small>防止超卖</small></span></div>
        </div>
      </section>

      <section class="category-strip" aria-label="商品分类">
        <button :class="{ active: selectedCategory === 0 }" @click="selectedCategory = 0">全部精选</button>
        <button
          v-for="category in categories"
          :key="category.id"
          :class="{ active: selectedCategory === category.id }"
          @click="selectedCategory = category.id"
        >
          {{ category.name }}
        </button>
        <span>最近同步 {{ lastUpdated?.toLocaleTimeString("zh-CN") ?? "—" }}</span>
      </section>

      <section id="flash-sale" class="sale-section">
        <div class="store-section-heading">
          <div>
            <p>LIMITED-TIME OFFERS</p>
            <h2><Zap :size="24" fill="currentColor" /> 限时秒杀</h2>
            <span>每个 SKU 独立库存 · 每场活动独立限购</span>
          </div>
          <div class="sale-toolbar">
            <select v-model="sortMode" aria-label="商品排序">
              <option value="recommended">综合排序</option>
              <option value="price-asc">价格从低到高</option>
              <option value="price-desc">价格从高到低</option>
              <option value="rating">评分优先</option>
            </select>
            <div class="live-sale"><i></i> LIVE · {{ filteredCampaigns.length }} 场进行中</div>
          </div>
        </div>

        <div v-if="filteredCampaigns.length" class="product-grid">
          <article
            v-for="campaign in filteredCampaigns"
            :key="campaign.id"
            class="product-card"
            :data-testid="`campaign-${campaign.id}`"
          >
            <div class="product-media" :class="`tone-${campaign.id % 3}`">
              <span class="product-category">{{ campaign.categoryName }}</span>
              <span class="sale-flag">直降 {{ Math.round((1 - campaign.flashPrice / campaign.originalPrice) * 100) }}%</span>
              <img v-if="campaign.imageUrl" :src="campaign.imageUrl" :alt="campaign.productName" />
              <component v-else :is="productIcon(campaign.categoryName)" :size="105" :stroke-width="1.2" />
            </div>
            <div class="product-body">
              <div class="product-meta">
                <span class="phase" :class="{ upcoming: campaignPhase(campaign) === '即将开始' }">
                  <i></i>{{ campaignPhase(campaign) }}
                </span>
                <code>{{ campaign.skuCode }}</code>
              </div>
              <h3>{{ campaign.productName }}</h3>
              <div class="product-rating">
                <Star :size="15" fill="currentColor" />
                <strong>{{ productFor(campaign.productId)?.averageRating || "暂无评分" }}</strong>
                <span>{{ productFor(campaign.productId)?.reviewCount || 0 }} 条评价</span>
              </div>
              <div class="variant-tags">
                <span>{{ campaign.colorName }}</span>
                <span>{{ campaign.specification }}</span>
              </div>
              <div class="price-line">
                <strong>{{ money(campaign.flashPrice) }}</strong>
                <del>{{ money(campaign.originalPrice) }}</del>
              </div>
              <div class="sale-clock">
                <TimerReset :size="16" />
                <span>{{ campaignPhase(campaign) === "即将开始" ? "距开始" : "距结束" }}</span>
                <strong>{{ countdown(campaign) }}</strong>
              </div>
              <div class="inventory">
                <div>
                  <span>剩余 {{ campaign.remainingStock }} 件</span>
                  <span>共 {{ campaign.totalStock }} 件</span>
                </div>
                <div class="inventory-track">
                  <span :style="{ width: `${Math.max(2, (campaign.remainingStock / campaign.totalStock) * 100)}%` }"></span>
                </div>
              </div>
              <div class="buy-row">
                <div class="quantity-control">
                  <button type="button" aria-label="减少数量" @click="changeQuantity(campaign, -1)">
                    <Minus :size="14" />
                  </button>
                  <strong>{{ quantity(campaign) }}</strong>
                  <button type="button" aria-label="增加数量" @click="changeQuantity(campaign, 1)">
                    <Plus :size="14" />
                  </button>
                </div>
                <button
                  class="buy-button"
                  type="button"
                  :disabled="
                    actionBusy === `order-${campaign.id}` ||
                    campaignPhase(campaign) !== '抢购中' ||
                    campaign.remainingStock < 1
                  "
                  :data-testid="`buy-${campaign.id}`"
                  @click="openCheckout(campaign)"
                >
                  <Activity v-if="actionBusy === `order-${campaign.id}`" class="spin" :size="17" />
                  <Zap v-else :size="17" fill="currentColor" />
                  {{ actionBusy === `order-${campaign.id}` ? "抢购中" : "立即抢购" }}
                </button>
              </div>
              <p class="limit-copy">本场每位用户限购 {{ campaign.purchaseLimit }} 件</p>
            </div>
          </article>
        </div>
        <div v-else class="store-empty">
          <Search :size="32" />
          <strong>没有找到符合条件的秒杀商品</strong>
          <button type="button" @click="selectedCategory = 0; keyword = ''">查看全部商品</button>
        </div>
      </section>

      <section id="addresses" class="address-section">
        <div class="store-section-heading compact">
          <div>
            <p>DELIVERY PROFILE</p>
            <h2><MapPin :size="23" /> 收货地址</h2>
            <span>订单会保存地址快照，后续修改地址不会影响历史订单</span>
          </div>
          <span class="order-count">{{ addresses.length }} 个地址</span>
        </div>
        <div class="address-layout">
          <form class="address-form" data-testid="address-form" @submit.prevent="submitAddress">
            <div class="address-form-title">
              <span><MapPin :size="18" /></span>
              <div><strong>{{ editingAddressId ? "编辑收货地址" : "新增收货地址" }}</strong><small>请填写真实有效的收货信息</small></div>
            </div>
            <div class="address-form-grid two">
              <label><span>收货人</span><input v-model.trim="addressForm.recipientName" required maxlength="80" placeholder="姓名" data-testid="address-recipient" /></label>
              <label><span>手机号</span><input v-model.trim="addressForm.phone" required maxlength="32" pattern="[0-9+ -]{7,32}" placeholder="13800000000" /></label>
            </div>
            <div class="address-form-grid three">
              <label><span>省份</span><input v-model.trim="addressForm.province" required maxlength="80" placeholder="上海市" /></label>
              <label><span>城市</span><input v-model.trim="addressForm.city" required maxlength="80" placeholder="上海市" /></label>
              <label><span>区县</span><input v-model.trim="addressForm.district" required maxlength="80" placeholder="浦东新区" /></label>
            </div>
            <div class="address-form-grid detail-postal">
              <label><span>详细地址</span><input v-model.trim="addressForm.detailAddress" required maxlength="240" placeholder="街道、门牌号、小区和楼栋" /></label>
              <label><span>邮政编码</span><input v-model.trim="addressForm.postalCode" maxlength="20" placeholder="可选" /></label>
            </div>
            <div class="address-form-footer">
              <label class="default-check"><input v-model="addressForm.defaultAddress" type="checkbox" /> 设为默认地址</label>
              <span class="address-form-actions">
                <button v-if="editingAddressId" class="secondary" type="button" @click="resetAddressForm">取消编辑</button>
                <button type="submit" :disabled="actionBusy === 'address-create' || actionBusy === `address-update-${editingAddressId}`">
                  <Activity v-if="actionBusy.startsWith('address-')" class="spin" :size="16" />
                  <Plus v-else :size="16" /> {{ editingAddressId ? "保存修改" : "保存地址" }}
                </button>
              </span>
            </div>
          </form>

          <div class="address-book">
            <article v-for="address in addresses" :key="address.id" class="address-card" :class="{ default: address.defaultAddress }">
              <div class="address-card-head">
                <span><MapPin :size="17" /></span>
                <strong>{{ address.recipientName }}</strong>
                <b v-if="address.defaultAddress">默认</b>
              </div>
              <p>{{ fullAddress(address) }}</p>
              <small>{{ address.phone }}<template v-if="address.postalCode"> · {{ address.postalCode }}</template></small>
              <div>
                <button v-if="!address.defaultAddress" type="button" @click="emit('setDefaultAddress', address.id)">设为默认</button>
                <button type="button" @click="editAddress(address)">编辑</button>
                <button class="danger" type="button" :disabled="actionBusy === `address-delete-${address.id}`" @click="emit('deleteAddress', address.id)">
                  <Trash2 :size="14" /> 删除
                </button>
              </div>
            </article>
            <div v-if="!addresses.length" class="address-empty">
              <MapPin :size="28" /><strong>还没有收货地址</strong><span>先添加地址，再参与商品抢购。</span>
            </div>
          </div>
        </div>
      </section>

      <section id="orders" class="member-section">
        <div class="orders-panel">
          <div class="store-section-heading compact">
            <div>
              <p>MY PURCHASES</p>
              <h2><ShoppingBag :size="23" /> 我的订单</h2>
              <span>订单创建后 15 分钟内完成支付</span>
            </div>
            <span class="order-count">{{ orders.length }} 笔订单</span>
          </div>
          <div v-if="orders.length" class="order-list">
            <article v-for="order in orders" :key="order.orderNo" class="order-row">
              <div class="order-product-icon"><PackageCheck :size="21" /></div>
              <div class="order-main">
                <strong>{{ order.productName || "秒杀商品" }}</strong>
                <span>{{ order.variantLabel || `商品 #${order.productId}` }} · × {{ order.quantity }}</span>
                <code :title="order.orderNo">{{ shortOrderNo(order.orderNo) }}</code>
                <small class="order-address"><MapPin :size="12" /> {{ order.recipientName }} · {{ order.shippingAddress }}</small>
                <small v-if="order.trackingNo" class="order-tracking"><Truck :size="12" /> {{ order.carrier }} · {{ order.trackingNo }}</small>
              </div>
              <div class="order-time"><Clock3 :size="14" /> {{ formatTime(order.createdAt) }}</div>
              <strong class="order-amount">{{ money(order.amount) }}</strong>
              <span class="order-status" :class="order.status.toLowerCase()">{{ statusText(order.status) }}</span>
              <div class="order-actions">
                <button
                  v-if="order.status === 'PENDING_PAYMENT'"
                  class="pay-button"
                  type="button"
                  :disabled="payingOrderNo === order.orderNo"
                  :data-testid="`pay-${order.orderNo}`"
                  @click="emit('pay', order.orderNo)"
                >
                  <Activity v-if="payingOrderNo === order.orderNo" class="spin" :size="15" />
                  <CreditCard v-else :size="15" /> 支付
                </button>
                <button
                  v-if="order.status === 'PENDING_PAYMENT'"
                  class="order-secondary"
                  type="button"
                  @click="openOrderAction('cancel', order)"
                >
                  取消
                </button>
                <button
                  v-if="order.status === 'RECEIVED' && !reviewFor(order.orderNo)"
                  class="order-secondary"
                  type="button"
                  @click="openOrderAction('review', order)"
                >
                  <Star :size="14" /> 评价
                </button>
                <button
                  v-if="order.status === 'SHIPPED'"
                  class="pay-button receive-button"
                  type="button"
                  :disabled="actionBusy === `receive-${order.orderNo}`"
                  @click="emit('receive', order.orderNo)"
                >
                  <PackageOpen :size="15" /> 确认收货
                </button>
                <button
                  v-if="['PAID', 'SHIPPED', 'RECEIVED'].includes(order.status) && !returnFor(order.orderNo)"
                  class="order-secondary danger"
                  type="button"
                  @click="openOrderAction('return', order)"
                >
                  <RotateCcw :size="14" /> 申请退款
                </button>
                <span v-if="reviewFor(order.orderNo)" class="fulfilled">
                  <Star :size="14" fill="currentColor" /> 已评价
                </span>
                <span
                  v-if="returnFor(order.orderNo)"
                  class="return-hint"
                  :class="returnFor(order.orderNo)?.status.toLowerCase()"
                >
                  {{
                    returnFor(order.orderNo)?.status === "PENDING"
                      ? "退款审核中"
                      : returnFor(order.orderNo)?.status === "APPROVED"
                        ? "退款已通过"
                        : "退款未通过"
                  }}
                </span>
              </div>
            </article>
          </div>
          <div v-else class="orders-empty">
            <ShoppingBag :size="28" />
            <strong>还没有订单</strong>
            <span>选择上方商品参与第一场秒杀。</span>
          </div>
        </div>

        <aside class="notification-panel">
          <div class="notification-heading">
            <span><Radio :size="19" /></span>
            <div><strong>履约消息</strong><small>Kafka 实时消费</small></div>
            <b>{{ notifications.length }}</b>
          </div>
          <div v-if="notifications.length" class="notification-list">
            <article v-for="item in notifications.slice(0, 6)" :key="item.id">
              <span :class="{ paid: item.templateCode === 'ORDER_PAID' }">
                <CreditCard v-if="item.templateCode === 'ORDER_PAID'" :size="16" />
                <Truck v-else-if="item.templateCode === 'ORDER_SHIPPED'" :size="16" />
                <PackageOpen v-else-if="item.templateCode === 'ORDER_RECEIVED'" :size="16" />
                <Bell v-else :size="16" />
              </span>
              <div>
                <strong>{{ notificationTitle(item.templateCode) }}</strong>
                <p>{{ item.content }}</p>
                <time>{{ formatTime(item.createdAt) }}</time>
              </div>
            </article>
          </div>
          <div v-else class="notification-empty"><Bell :size="24" /><span>等待业务消息</span></div>
          <div class="service-health-list">
            <span v-for="service in services" :key="service.key">
              <i :class="service.status.toLowerCase()"></i>{{ service.name }} <b>{{ service.status }}</b>
            </span>
          </div>
        </aside>
      </section>
    </main>

    <div v-if="checkoutCampaign" class="commerce-modal-backdrop" @click.self="checkoutCampaign = null">
      <form class="commerce-modal checkout-modal" data-testid="checkout-dialog" @submit.prevent="confirmCheckout">
        <div class="modal-heading">
          <div><small>ORDER CONFIRMATION</small><h3>确认订单与收货地址</h3></div>
          <button type="button" aria-label="关闭" @click="checkoutCampaign = null">×</button>
        </div>
        <div class="modal-order-summary">
          <strong>{{ checkoutCampaign.productName }}</strong>
          <span>{{ checkoutCampaign.colorName }} · {{ checkoutCampaign.specification }} · × {{ quantity(checkoutCampaign) }}</span>
          <b>{{ money(checkoutCampaign.flashPrice * quantity(checkoutCampaign)) }}</b>
        </div>
        <div v-if="addresses.length" class="checkout-addresses">
          <label v-for="address in addresses" :key="address.id" :class="{ selected: checkoutAddressId === address.id }">
            <input v-model.number="checkoutAddressId" type="radio" name="checkout-address" :value="address.id" />
            <span><strong>{{ address.recipientName }} · {{ address.phone }}</strong><small>{{ fullAddress(address) }}</small></span>
            <b v-if="address.defaultAddress">默认</b>
          </label>
        </div>
        <div v-else class="checkout-address-empty">
          <MapPin :size="28" /><strong>请先添加收货地址</strong><span>关闭窗口后可在“收货地址”区域新增。</span>
        </div>
        <p class="modal-tip">提交时会执行 Redis 原子库存预扣，订单创建后请在 15 分钟内支付。</p>
        <div class="modal-actions">
          <button type="button" @click="checkoutCampaign = null">继续选购</button>
          <button class="primary" type="submit" :disabled="!checkoutAddressId || actionBusy === `order-${checkoutCampaign.id}`">
            <Zap :size="16" /> 提交秒杀订单
          </button>
        </div>
      </form>
    </div>

    <div v-if="orderAction" class="commerce-modal-backdrop" @click.self="orderAction = null">
      <form class="commerce-modal" data-testid="order-action-dialog" @submit.prevent="submitOrderAction">
        <div class="modal-heading">
          <div>
            <small>ORDER SERVICE</small>
            <h3>
              {{
                orderAction.type === "review"
                  ? "发表商品评价"
                  : orderAction.type === "return"
                    ? "申请退款"
                    : "取消订单"
              }}
            </h3>
          </div>
          <button type="button" aria-label="关闭" @click="orderAction = null">×</button>
        </div>
        <div class="modal-order-summary">
          <strong>{{ orderAction.order.productName }}</strong>
          <span>{{ orderAction.order.variantLabel }} · × {{ orderAction.order.quantity }}</span>
          <b>{{ money(orderAction.order.amount) }}</b>
        </div>
        <template v-if="orderAction.type === 'review'">
          <label class="rating-picker">
            <span>商品评分</span>
            <span>
              <button
                v-for="value in 5"
                :key="value"
                type="button"
                :class="{ active: value <= reviewRating }"
                @click="reviewRating = value"
              >
                <Star :size="24" fill="currentColor" />
              </button>
              <b>{{ reviewRating }}.0</b>
            </span>
          </label>
          <label>
            <span>评价内容</span>
            <textarea
              v-model="reviewContent"
              required
              minlength="3"
              maxlength="1000"
              rows="4"
              placeholder="分享商品品质、外观或使用体验"
            ></textarea>
          </label>
        </template>
        <label v-else>
          <span>{{ orderAction.type === "return" ? "退款原因" : "取消原因" }}</span>
          <textarea
            v-model="actionReason"
            :required="orderAction.type === 'return'"
            :minlength="orderAction.type === 'return' ? 5 : 0"
            maxlength="500"
            rows="4"
            :placeholder="orderAction.type === 'return' ? '请说明退款原因（至少 5 个字）' : '可选：告诉我们取消原因'"
          ></textarea>
        </label>
        <p v-if="orderAction.type === 'return'" class="modal-tip">
          提交后订单进入退款审核，管理员通过后会恢复活动库存。
        </p>
        <div class="modal-actions">
          <button type="button" @click="orderAction = null">暂不操作</button>
          <button class="primary" type="submit">
            {{ orderAction.type === "review" ? "发布评价" : orderAction.type === "return" ? "提交申请" : "确认取消" }}
          </button>
        </div>
      </form>
    </div>

    <footer class="store-footer">
      <div class="commerce-brand"><span><Zap :size="17" fill="currentColor" /></span><strong>FlashFlow</strong></div>
      <p>商品域、SKU 库存、秒杀交易与异步履约一体化演示商城</p>
      <span><HeartPulse :size="15" /> Java 21 · Redis · Kafka</span>
    </footer>
  </div>
</template>
