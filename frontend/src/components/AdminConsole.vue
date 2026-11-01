<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import {
  Activity,
  Archive,
  Boxes,
  CalendarClock,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  ClipboardList,
  CircleOff,
  Database,
  FolderTree,
  Gauge,
  Layers3,
  LogOut,
  Menu,
  MessageSquareText,
  PackagePlus,
  Plus,
  Radio,
  RefreshCw,
  RotateCcw,
  Save,
  Scale,
  Search,
  Send,
  Settings2,
  ShieldCheck,
  Store,
  Star,
  Tag,
  TimerReset,
  Trash2,
  Truck,
  UserRoundCog,
  Zap,
} from "@lucide/vue";
import type {
  Campaign,
  CampaignInput,
  Category,
  Product,
  ProductInput,
  ReturnRequest,
  Review,
  Order,
  ServiceHealth,
  Session,
  VariantInput,
} from "../types";
import InventoryPanel from "./InventoryPanel.vue";

const props = defineProps<{
  session: Session;
  products: Product[];
  categories: Category[];
  campaigns: Campaign[];
  orders: Order[];
  returns: ReturnRequest[];
  reviews: Review[];
  services: ServiceHealth[];
  loading: boolean;
  actionBusy: string;
  lastUpdated: Date | null;
}>();

const emit = defineEmits<{
  logout: [];
  refresh: [];
  createCategory: [payload: { name: string; sortOrder: number }];
  setCategoryStatus: [id: number, active: boolean];
  createProduct: [payload: ProductInput];
  setProductStatus: [id: number, active: boolean];
  addVariant: [productId: number, payload: VariantInput];
  setVariantStock: [id: number, stock: number];
  setVariantPrice: [id: number, price: number];
  setVariantStatus: [id: number, active: boolean];
  createCampaign: [payload: CampaignInput];
  setCampaignStatus: [id: number, status: string];
  warmup: [id: number];
  decideReturn: [id: number, approved: boolean, note: string];
  setReviewVisibility: [id: number, visible: boolean];
  replyReview: [id: number, reply: string];
  shipOrder: [orderNo: string, carrier: string, trackingNo: string];
}>();

type AdminTab = "overview" | "products" | "campaigns" | "inventory" | "orders" | "reviews" | "categories";
const activeTab = ref<AdminTab>("overview");
const expandedProducts = reactive<Record<number, boolean>>({});
const stockDrafts = reactive<Record<number, number>>({});
const priceDrafts = reactive<Record<number, number>>({});
const returnNotes = reactive<Record<number, string>>({});
const reviewReplies = reactive<Record<number, string>>({});
const shippingDrafts = reactive<Record<string, { carrier: string; trackingNo: string }>>({});
const orderKeyword = ref("");
const orderStatus = ref("ALL");
const variantForms = reactive<Record<number, VariantInput>>({});

const categoryForm = reactive({ name: "", sortOrder: 50 });
const productForm = reactive<ProductInput>({
  categoryId: 0,
  name: "",
  description: "",
  imageUrl: "/product-images/headphone.svg",
  variants: [emptyVariant()],
});

const starts = new Date(Date.now() - 5 * 60_000);
const ends = new Date(Date.now() + 7 * 86_400_000);
const campaignForm = reactive<CampaignInput>({
  variantId: 0,
  flashPrice: 399,
  totalStock: 50,
  purchaseLimit: 1,
  startsAt: localDateTime(starts),
  endsAt: localDateTime(ends),
});

const allVariants = computed(() =>
  props.products.flatMap((product) =>
    product.variants.map((variant) => ({
      ...variant,
      productId: product.id,
      productName: product.name,
      productActive: product.active,
    })),
  ),
);
const listedProducts = computed(() => props.products.filter((item) => item.active).length);
const liveCampaigns = computed(() => {
  const now = Date.now();
  return props.campaigns.filter(
    (item) =>
      item.status === "ACTIVE" &&
      item.productActive &&
      item.variantActive &&
      new Date(item.startsAt).getTime() <= now &&
      new Date(item.endsAt).getTime() > now,
  );
});
const activeCampaigns = computed(() => liveCampaigns.value.length);
const skuCount = computed(() => props.products.reduce((sum, item) => sum + item.variants.length, 0));
const skuStock = computed(() =>
  props.products.reduce(
    (sum, item) => sum + item.variants.reduce((inner, variant) => inner + variant.stock, 0),
    0,
  ),
);
const pendingReturns = computed(() => props.returns.filter((item) => item.status === "PENDING").length);
const visibleReviews = computed(() => props.reviews.filter((item) => item.visible).length);
const filteredOrders = computed(() => {
  const keyword = orderKeyword.value.trim().toLowerCase();
  return props.orders.filter((order) => {
    const statusMatch = orderStatus.value === "ALL" || order.status === orderStatus.value;
    const keywordMatch =
      !keyword ||
      order.orderNo.toLowerCase().includes(keyword) ||
      order.productName.toLowerCase().includes(keyword) ||
      order.variantLabel.toLowerCase().includes(keyword) ||
      order.recipientName.toLowerCase().includes(keyword) ||
      order.recipientPhone.includes(keyword) ||
      (order.trackingNo?.toLowerCase().includes(keyword) ?? false);
    return statusMatch && keywordMatch;
  });
});
const allHealthy = computed(
  () => props.services.length > 0 && props.services.every((item) => item.status === "UP"),
);

function emptyVariant(): VariantInput {
  return { skuCode: "", colorName: "", specification: "", price: 0, stock: 0 };
}

function localDateTime(date: Date) {
  const shifted = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return shifted.toISOString().slice(0, 16);
}

function money(value?: number) {
  if (value === undefined || value === null) return "—";
  return new Intl.NumberFormat("zh-CN", {
    style: "currency",
    currency: "CNY",
    minimumFractionDigits: 2,
  }).format(value);
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(value));
}

function addProductVariant() {
  productForm.variants.push(emptyVariant());
}

function removeProductVariant(index: number) {
  if (productForm.variants.length > 1) productForm.variants.splice(index, 1);
}

function submitProduct() {
  emit("createProduct", {
    ...productForm,
    categoryId: Number(productForm.categoryId),
    variants: productForm.variants.map((variant) => ({
      ...variant,
      price: Number(variant.price),
      stock: Number(variant.stock),
    })),
  });
}

function addCategory() {
  emit("createCategory", { name: categoryForm.name.trim(), sortOrder: Number(categoryForm.sortOrder) });
  categoryForm.name = "";
}

function ensureVariantForm(productId: number) {
  if (!variantForms[productId]) variantForms[productId] = emptyVariant();
  return variantForms[productId];
}

function submitVariant(productId: number) {
  const form = ensureVariantForm(productId);
  emit("addVariant", productId, {
    ...form,
    price: Number(form.price),
    stock: Number(form.stock),
  });
}

function currentStock(variantId: number, fallback: number) {
  return stockDrafts[variantId] ?? fallback;
}

function currentPrice(variantId: number, fallback: number) {
  return priceDrafts[variantId] ?? fallback;
}

function shippingDraft(orderNo: string) {
  if (!shippingDrafts[orderNo]) {
    shippingDrafts[orderNo] = { carrier: "顺丰速运", trackingNo: "" };
  }
  return shippingDrafts[orderNo];
}

function submitShipment(orderNo: string) {
  const draft = shippingDraft(orderNo);
  emit("shipOrder", orderNo, draft.carrier.trim(), draft.trackingNo.trim());
}

function orderStatusText(status: string) {
  return ({
    PENDING_PAYMENT: "待支付",
    PAID: "已支付",
    SHIPPED: "已发货",
    RECEIVED: "已收货",
    REFUND_REQUESTED: "退款审核中",
    REFUNDED: "已退款",
    CANCELLED: "已关闭",
  }[status] ?? status);
}

function returnStatusText(status: string) {
  return ({ PENDING: "待审核", APPROVED: "已通过", REJECTED: "已驳回" }[status] ?? status);
}

function statusText(status: string) {
  return ({ ACTIVE: "进行中", PAUSED: "已暂停", ENDED: "已结束" }[status] ?? status);
}
</script>

<template>
  <div class="admin-shell">
    <aside class="admin-sidebar">
      <div class="admin-brand">
        <span><Zap :size="20" fill="currentColor" /></span>
        <div><strong>FlashFlow</strong><small>商城运营后台</small></div>
      </div>
      <nav>
        <button :class="{ active: activeTab === 'overview' }" @click="activeTab = 'overview'">
          <Gauge :size="18" /> 运营概览
        </button>
        <button
          :class="{ active: activeTab === 'products' }"
          data-testid="nav-products"
          @click="activeTab = 'products'"
        >
          <Boxes :size="18" /> 商品与 SKU
          <b>{{ products.length }}</b>
        </button>
        <button
          :class="{ active: activeTab === 'campaigns' }"
          data-testid="nav-campaigns"
          @click="activeTab = 'campaigns'"
        >
          <CalendarClock :size="18" /> 秒杀活动
          <b>{{ campaigns.length }}</b>
        </button>
        <button
          :class="{ active: activeTab === 'inventory' }"
          data-testid="nav-inventory"
          @click="activeTab = 'inventory'"
        >
          <Scale :size="18" /> 库存对账
        </button>
        <button
          :class="{ active: activeTab === 'orders' }"
          data-testid="nav-orders"
          @click="activeTab = 'orders'"
        >
          <ClipboardList :size="18" /> 订单与售后
          <b>{{ pendingReturns || orders.length }}</b>
        </button>
        <button
          :class="{ active: activeTab === 'reviews' }"
          data-testid="nav-reviews"
          @click="activeTab = 'reviews'"
        >
          <MessageSquareText :size="18" /> 商品评价
          <b>{{ reviews.length }}</b>
        </button>
        <button
          :class="{ active: activeTab === 'categories' }"
          data-testid="nav-categories"
          @click="activeTab = 'categories'"
        >
          <FolderTree :size="18" /> 商品分类
          <b>{{ categories.length }}</b>
        </button>
      </nav>
      <div class="admin-system">
        <p>基础设施状态</p>
        <span v-for="service in services" :key="service.key">
          <i :class="service.status.toLowerCase()"></i>
          {{ service.name }}
          <b>{{ service.status }}</b>
        </span>
        <a href="http://localhost:8090" target="_blank" rel="noreferrer">
          <Radio :size="15" /> Kafka 管理台 <ChevronRight :size="14" />
        </a>
      </div>
      <div class="admin-profile">
        <span><UserRoundCog :size="18" /></span>
        <div><strong>{{ session.username }}</strong><small>系统管理员</small></div>
        <button type="button" aria-label="退出登录" @click="emit('logout')"><LogOut :size="17" /></button>
      </div>
    </aside>

    <div class="admin-main">
      <header class="admin-topbar">
        <div>
          <button class="mobile-menu" type="button"><Menu :size="19" /></button>
          <div>
            <strong>
              {{
                {
                  overview: "运营概览",
                  products: "商品与 SKU 管理",
                  campaigns: "秒杀活动管理",
                  inventory: "库存对账与补偿",
                  orders: "订单与售后管理",
                  reviews: "商品评价管理",
                  categories: "商品分类管理",
                }[activeTab]
              }}
            </strong>
            <small>最后同步 {{ lastUpdated?.toLocaleTimeString("zh-CN") ?? "—" }}</small>
          </div>
        </div>
        <div>
          <span class="admin-role"><ShieldCheck :size="15" /> ADMIN · 仅运营权限</span>
          <span class="health-pill" :class="{ danger: !allHealthy }"><i></i>{{ allHealthy ? "系统正常" : "服务异常" }}</span>
          <button
            class="refresh-admin"
            type="button"
            :disabled="loading"
            data-testid="admin-refresh"
            @click="emit('refresh')"
          >
            <RefreshCw :size="17" :class="{ spin: loading }" /> 刷新数据
          </button>
        </div>
      </header>

      <main class="admin-content">
        <section v-if="activeTab === 'overview'" class="admin-overview">
          <div class="admin-welcome">
            <div>
              <p>COMMERCE OPERATIONS</p>
              <h1>下午好，{{ session.username }}</h1>
              <span>从商品建档、SKU 库存到秒杀投放，在一套后台完成运营闭环。</span>
            </div>
            <div class="permission-note">
              <ShieldCheck :size="24" />
              <div><strong>权限边界已启用</strong><span>管理员账号无法访问下单与支付接口</span></div>
            </div>
          </div>

          <div class="admin-metrics">
            <article><span><Store :size="21" /></span><div><small>商品总数</small><strong>{{ products.length }}</strong><p>{{ listedProducts }} 个正在销售</p></div></article>
            <article><span><Layers3 :size="21" /></span><div><small>SKU 数量</small><strong>{{ skuCount }}</strong><p>颜色与规格独立管理</p></div></article>
            <article><span><Archive :size="21" /></span><div><small>SKU 可用库存</small><strong>{{ skuStock }}</strong><p>活动库存单独分配</p></div></article>
            <article><span><Zap :size="21" /></span><div><small>秒杀活动</small><strong>{{ activeCampaigns }}</strong><p>{{ campaigns.length }} 场累计配置</p></div></article>
            <article><span><RotateCcw :size="21" /></span><div><small>待审退款</small><strong>{{ pendingReturns }}</strong><p>{{ returns.length }} 笔售后申请</p></div></article>
            <article><span><Star :size="21" /></span><div><small>商品评价</small><strong>{{ reviews.length }}</strong><p>{{ visibleReviews }} 条前台可见</p></div></article>
          </div>

          <div class="operation-flow">
            <div class="admin-section-title">
              <div><p>CATALOG WORKFLOW</p><h2>商品运营流程</h2></div>
            </div>
            <div class="flow-grid">
              <button @click="activeTab = 'categories'"><span>01</span><FolderTree :size="25" /><strong>建立分类</strong><p>维护前台类目与排序</p><ChevronRight :size="17" /></button>
              <button @click="activeTab = 'products'"><span>02</span><PackagePlus :size="25" /><strong>创建商品</strong><p>一次录入多个颜色 SKU</p><ChevronRight :size="17" /></button>
              <button @click="activeTab = 'products'"><span>03</span><Database :size="25" /><strong>维护库存</strong><p>SKU 级库存与启停状态</p><ChevronRight :size="17" /></button>
              <button @click="activeTab = 'campaigns'"><span>04</span><TimerReset :size="25" /><strong>投放秒杀</strong><p>活动库存、限购与时段</p><ChevronRight :size="17" /></button>
            </div>
          </div>

          <div class="recent-campaigns">
            <div class="admin-section-title"><div><p>RECENT CAMPAIGNS</p><h2>最近活动</h2></div><button @click="activeTab = 'campaigns'">管理全部 <ChevronRight :size="16" /></button></div>
            <div class="mini-campaign-list">
              <article v-for="campaign in liveCampaigns.slice(0, 4)" :key="campaign.id">
                <img :src="campaign.imageUrl || '/product-images/headphone.svg'" alt="" />
                <div><strong>{{ campaign.productName }}</strong><span>{{ campaign.colorName }} · {{ campaign.specification }}</span></div>
                <b>{{ money(campaign.flashPrice) }}</b>
                <span class="campaign-status" :class="campaign.status.toLowerCase()">{{ statusText(campaign.status) }}</span>
                <small>库存 {{ campaign.remainingStock }}/{{ campaign.totalStock }}</small>
              </article>
            </div>
          </div>
        </section>

        <section v-else-if="activeTab === 'products'" class="admin-products">
          <div class="split-heading">
            <div><p>PRODUCT CATALOG</p><h1>商品与 SKU 管理</h1><span>SPU 负责商品信息，SKU 独立维护颜色、规格、价格与库存。</span></div>
            <span class="scope-note"><ShieldCheck :size="16" /> 管理员仅运营，不参与购买</span>
          </div>

          <div class="admin-two-columns">
            <form class="admin-form-card product-create-form" data-testid="product-form" @submit.prevent="submitProduct">
              <div class="form-card-heading"><span><PackagePlus :size="21" /></span><div><strong>创建新商品</strong><small>同时录入至少一个 SKU</small></div></div>
              <div class="form-grid two">
                <label><span>商品名称</span><input v-model="productForm.name" required placeholder="例如：FlashFlow 智能音箱" data-testid="product-name" /></label>
                <label><span>商品分类</span><select v-model.number="productForm.categoryId" required data-testid="product-category"><option :value="0" disabled>请选择分类</option><option v-for="category in categories.filter((item) => item.active)" :key="category.id" :value="category.id">{{ category.name }}</option></select></label>
              </div>
              <label><span>商品卖点</span><textarea v-model="productForm.description" required rows="2" placeholder="一句话描述商品核心卖点"></textarea></label>
              <label><span>商品图片路径</span><select v-model="productForm.imageUrl"><option value="/product-images/headphone.svg">数码耳机图</option><option value="/product-images/watch.svg">智能手表图</option><option value="/product-images/coffee.svg">生活家电图</option></select></label>

              <div class="variant-editor">
                <div><strong>颜色与规格 SKU</strong><button type="button" @click="addProductVariant"><Plus :size="15" /> 添加一行</button></div>
                <div v-for="(variant, index) in productForm.variants" :key="index" class="variant-input-row">
                  <span class="row-number">{{ index + 1 }}</span>
                  <input v-model="variant.skuCode" required placeholder="SKU 编码" :data-testid="`new-sku-${index}`" />
                  <input v-model="variant.colorName" required placeholder="颜色" />
                  <input v-model="variant.specification" required placeholder="规格/版本" />
                  <input v-model.number="variant.price" required min="0.01" step="0.01" type="number" placeholder="价格" />
                  <input v-model.number="variant.stock" required min="0" type="number" placeholder="库存" />
                  <button type="button" aria-label="删除 SKU 行" :disabled="productForm.variants.length === 1" @click="removeProductVariant(index)"><Trash2 :size="15" /></button>
                </div>
              </div>
              <button class="admin-primary" type="submit" :disabled="actionBusy === 'product-create'" data-testid="create-product-submit">
                <Activity v-if="actionBusy === 'product-create'" class="spin" :size="17" />
                <Save v-else :size="17" /> 创建商品并保存 SKU
              </button>
            </form>

            <div class="catalog-list-card">
              <div class="list-card-heading"><div><strong>商品目录</strong><small>{{ listedProducts }} 上架 / {{ products.length - listedProducts }} 下架</small></div><span>{{ products.length }} SPU · {{ skuCount }} SKU</span></div>
              <div class="catalog-list">
                <article v-for="product in products" :key="product.id" class="catalog-product" :class="{ offline: !product.active }">
                  <button class="product-summary" type="button" @click="expandedProducts[product.id] = !expandedProducts[product.id]">
                    <img :src="product.imageUrl || '/product-images/headphone.svg'" alt="" />
                    <div><strong>{{ product.name }}</strong><span>{{ product.categoryName }} · {{ product.variants.length }} 个 SKU</span></div>
                    <b>{{ money(product.minPrice) }} 起</b>
                    <span class="publish-status" :class="{ active: product.active }">{{ product.active ? "已上架" : "已下架" }}</span>
                    <ChevronDown :size="17" :class="{ rotated: expandedProducts[product.id] }" />
                  </button>
                  <div v-if="expandedProducts[product.id]" class="product-detail">
                    <div class="product-detail-actions">
                      <p>{{ product.description }}</p>
                      <button type="button" :class="{ danger: product.active }" :data-testid="`toggle-product-${product.id}`" @click="emit('setProductStatus', product.id, !product.active)">
                        <CircleOff v-if="product.active" :size="15" /><CheckCircle2 v-else :size="15" />
                        {{ product.active ? "下架商品" : "重新上架" }}
                      </button>
                    </div>
                    <div class="sku-table">
                      <div class="sku-head"><span>SKU 编码</span><span>颜色 / 规格</span><span>售价</span><span>可用库存</span><span>状态</span><span>操作</span></div>
                      <div v-for="variant in product.variants" :key="variant.id" class="sku-row">
                        <code>{{ variant.skuCode }}</code>
                        <span><b>{{ variant.colorName }}</b><small>{{ variant.specification }}</small></span>
                        <span class="price-editor">
                          <input
                            :value="currentPrice(variant.id, variant.price)"
                            min="0.01"
                            step="0.01"
                            type="number"
                            :data-testid="`price-${variant.id}`"
                            @input="priceDrafts[variant.id] = Number(($event.target as HTMLInputElement).value)"
                          />
                          <button
                            type="button"
                            title="保存售价"
                            @click="emit('setVariantPrice', variant.id, currentPrice(variant.id, variant.price))"
                          ><Save :size="13" /></button>
                        </span>
                        <input :value="currentStock(variant.id, variant.stock)" min="0" type="number" @input="stockDrafts[variant.id] = Number(($event.target as HTMLInputElement).value)" />
                        <span class="sku-status" :class="{ active: variant.active }">{{ variant.active ? "启用" : "停用" }}</span>
                        <span class="sku-actions">
                          <button type="button" title="保存库存" :data-testid="`save-stock-${variant.id}`" @click="emit('setVariantStock', variant.id, currentStock(variant.id, variant.stock))"><Save :size="14" /></button>
                          <button type="button" :title="variant.active ? '停用 SKU' : '启用 SKU'" @click="emit('setVariantStatus', variant.id, !variant.active)"><Settings2 :size="14" /></button>
                        </span>
                      </div>
                    </div>
                    <form class="inline-variant-form" @submit.prevent="submitVariant(product.id)">
                      <strong><Plus :size="15" /> 增加颜色/规格</strong>
                      <input v-model="ensureVariantForm(product.id).skuCode" required placeholder="SKU 编码" />
                      <input v-model="ensureVariantForm(product.id).colorName" required placeholder="颜色" />
                      <input v-model="ensureVariantForm(product.id).specification" required placeholder="规格" />
                      <input v-model.number="ensureVariantForm(product.id).price" required min="0.01" step="0.01" type="number" placeholder="价格" />
                      <input v-model.number="ensureVariantForm(product.id).stock" required min="0" type="number" placeholder="库存" />
                      <button type="submit"><Plus :size="15" /> 添加 SKU</button>
                    </form>
                  </div>
                </article>
              </div>
            </div>
          </div>
        </section>

        <section v-else-if="activeTab === 'campaigns'" class="admin-campaigns">
          <div class="split-heading">
            <div><p>FLASH CAMPAIGNS</p><h1>秒杀活动管理</h1><span>从 SKU 库存分配活动库存，并控制每位用户的最大购买数量。</span></div>
            <span class="scope-note"><Database :size="16" /> 创建后自动预热 Redis</span>
          </div>
          <div class="admin-two-columns campaign-layout">
            <form class="admin-form-card campaign-create-form" data-testid="campaign-form" @submit.prevent="emit('createCampaign', { ...campaignForm })">
              <div class="form-card-heading"><span><Zap :size="21" /></span><div><strong>新建秒杀活动</strong><small>库存会从所选 SKU 中分配</small></div></div>
              <label><span>选择商品 SKU</span><select v-model.number="campaignForm.variantId" required data-testid="campaign-variant"><option :value="0" disabled>请选择已上架 SKU</option><option v-for="variant in allVariants.filter((item) => item.active && item.productActive)" :key="variant.id" :value="variant.id">{{ variant.productName }} / {{ variant.colorName }} / {{ variant.specification }}（库存 {{ variant.stock }}）</option></select></label>
              <div class="form-grid three">
                <label><span>秒杀价</span><input v-model.number="campaignForm.flashPrice" required min="0.01" step="0.01" type="number" data-testid="campaign-price" /></label>
                <label><span>活动库存</span><input v-model.number="campaignForm.totalStock" required min="1" type="number" data-testid="campaign-stock" /></label>
                <label><span>单人限购</span><input v-model.number="campaignForm.purchaseLimit" required min="1" type="number" data-testid="campaign-limit" /></label>
              </div>
              <div class="form-grid two">
                <label><span>开始时间</span><input v-model="campaignForm.startsAt" required type="datetime-local" /></label>
                <label><span>结束时间</span><input v-model="campaignForm.endsAt" required type="datetime-local" /></label>
              </div>
              <div class="allocation-note"><Database :size="18" /><p><strong>库存分配规则</strong><span>活动库存不能超过 SKU 可用库存；用户端会同时校验剩余库存和单人限购。</span></p></div>
              <button class="admin-primary" type="submit" :disabled="actionBusy === 'campaign-create'" data-testid="create-campaign-submit">
                <Activity v-if="actionBusy === 'campaign-create'" class="spin" :size="17" /><Zap v-else :size="17" /> 创建并投放活动
              </button>
            </form>

            <div class="campaign-admin-list">
              <div class="list-card-heading"><div><strong>活动列表</strong><small>支持暂停、恢复、结束和库存预热</small></div><span>{{ activeCampaigns }} 场进行中</span></div>
              <article v-for="campaign in campaigns" :key="campaign.id" class="campaign-admin-card">
                <img :src="campaign.imageUrl || '/product-images/headphone.svg'" alt="" />
                <div class="campaign-admin-main">
                  <div><span class="campaign-status" :class="campaign.status.toLowerCase()">{{ statusText(campaign.status) }}</span><code>#{{ campaign.id }} · {{ campaign.skuCode }}</code></div>
                  <h3>{{ campaign.productName }}</h3>
                  <p>{{ campaign.colorName }} · {{ campaign.specification }}</p>
                  <div class="campaign-numbers">
                    <span><small>秒杀价</small><strong>{{ money(campaign.flashPrice) }}</strong></span>
                    <span><small>剩余库存</small><strong>{{ campaign.remainingStock }} / {{ campaign.totalStock }}</strong></span>
                    <span><small>每人限购</small><strong>{{ campaign.purchaseLimit }} 件</strong></span>
                    <span><small>活动时间</small><strong>{{ formatTime(campaign.startsAt) }} - {{ formatTime(campaign.endsAt) }}</strong></span>
                  </div>
                </div>
                <div class="campaign-admin-actions">
                  <button type="button" @click="emit('warmup', campaign.id)"><Database :size="15" /> 重建缓存</button>
                  <button v-if="campaign.status === 'ACTIVE'" type="button" @click="emit('setCampaignStatus', campaign.id, 'PAUSED')"><CircleOff :size="15" /> 暂停</button>
                  <button v-else-if="campaign.status === 'PAUSED'" type="button" @click="emit('setCampaignStatus', campaign.id, 'ACTIVE')"><CheckCircle2 :size="15" /> 恢复</button>
                  <button v-if="campaign.status !== 'ENDED'" class="danger" type="button" @click="emit('setCampaignStatus', campaign.id, 'ENDED')"><Archive :size="15" /> 结束</button>
                </div>
              </article>
            </div>
          </div>
        </section>

        <InventoryPanel
          v-else-if="activeTab === 'inventory'"
          :token="session.accessToken"
          :campaigns="campaigns"
        />

        <section v-else-if="activeTab === 'orders'" class="admin-orders">
          <div class="split-heading">
            <div>
              <p>ORDER & AFTER-SALES</p>
              <h1>订单与售后管理</h1>
              <span>查询完整订单状态，录入承运商与运单号，并处理买家退款申请。</span>
            </div>
            <span class="scope-note"><RotateCcw :size="16" /> {{ pendingReturns }} 笔退款待处理</span>
          </div>

          <div class="admin-order-toolbar">
            <label><Search :size="16" /><input v-model="orderKeyword" placeholder="搜索订单号、商品或规格" /></label>
            <select v-model="orderStatus">
              <option value="ALL">全部状态</option>
              <option value="PENDING_PAYMENT">待支付</option>
              <option value="PAID">已支付</option>
              <option value="SHIPPED">已发货</option>
              <option value="RECEIVED">已收货</option>
              <option value="REFUND_REQUESTED">退款审核中</option>
              <option value="REFUNDED">已退款</option>
              <option value="CANCELLED">已关闭</option>
            </select>
            <span>{{ filteredOrders.length }} / {{ orders.length }} 笔订单</span>
          </div>

          <div class="after-sale-layout">
            <div class="admin-order-list">
              <div class="list-card-heading">
                <div><strong>交易订单</strong><small>最近 200 笔订单</small></div>
              </div>
              <article v-for="order in filteredOrders" :key="order.orderNo" class="admin-order-row">
                <div class="admin-order-product">
                  <strong>{{ order.productName }}</strong>
                  <span>{{ order.variantLabel }} · × {{ order.quantity }}</span>
                  <code>{{ order.orderNo }}</code>
                </div>
                <div><small>订单金额</small><b>{{ money(order.amount) }}</b></div>
                <div><small>创建时间</small><span>{{ formatTime(order.createdAt) }}</span></div>
                <span class="order-status" :class="order.status.toLowerCase()">
                  {{ orderStatusText(order.status) }}
                </span>
                <div class="admin-order-delivery">
                  <small>收货信息</small>
                  <strong>{{ order.recipientName }} · {{ order.recipientPhone }}</strong>
                  <span>{{ order.shippingAddress }}</span>
                  <template v-if="order.status === 'PAID'">
                    <form class="shipment-form" @submit.prevent="submitShipment(order.orderNo)">
                      <select v-model="shippingDraft(order.orderNo).carrier" required>
                        <option>顺丰速运</option>
                        <option>京东物流</option>
                        <option>中通快递</option>
                        <option>圆通速递</option>
                        <option>邮政 EMS</option>
                      </select>
                      <input v-model.trim="shippingDraft(order.orderNo).trackingNo" required maxlength="80" placeholder="输入运单号" :data-testid="`tracking-${order.orderNo}`" />
                      <button type="submit" :disabled="actionBusy === `ship-${order.orderNo}`" :data-testid="`ship-${order.orderNo}`">
                        <Activity v-if="actionBusy === `ship-${order.orderNo}`" class="spin" :size="14" />
                        <Truck v-else :size="14" /> 确认发货
                      </button>
                    </form>
                  </template>
                  <p v-else-if="order.trackingNo" class="shipment-result">
                    <Truck :size="14" /> {{ order.carrier }} · {{ order.trackingNo }}
                    <time>发货于 {{ formatTime(order.shippedAt || order.createdAt) }}</time>
                  </p>
                  <p v-else class="shipment-result muted">等待买家支付后安排发货</p>
                </div>
              </article>
              <div v-if="!filteredOrders.length" class="admin-empty">没有符合筛选条件的订单</div>
            </div>

            <div class="return-workbench">
              <div class="list-card-heading">
                <div><strong>退款审核工作台</strong><small>申请、审核、库存恢复全程留痕</small></div>
                <span>{{ returns.length }} 笔</span>
              </div>
              <article v-for="item in returns" :key="item.id" class="return-card">
                <div class="return-card-head">
                  <div><strong>{{ item.productName }}</strong><code>{{ item.returnNo }}</code></div>
                  <span :class="`return-${item.status.toLowerCase()}`">{{ returnStatusText(item.status) }}</span>
                </div>
                <p>{{ item.variantLabel }} · × {{ item.quantity }} · {{ money(item.amount) }}</p>
                <blockquote>{{ item.reason }}</blockquote>
                <small>申请于 {{ formatTime(item.requestedAt) }} · 订单 {{ item.orderNo }}</small>
                <template v-if="item.status === 'PENDING'">
                  <textarea
                    v-model="returnNotes[item.id]"
                    rows="2"
                    maxlength="500"
                    placeholder="填写审核说明（可选）"
                  ></textarea>
                  <div class="return-actions">
                    <button
                      type="button"
                      class="approve"
                      :disabled="actionBusy === `return-decision-${item.id}`"
                      @click="emit('decideReturn', item.id, true, returnNotes[item.id] || '审核通过')"
                    ><CheckCircle2 :size="15" /> 通过退款</button>
                    <button
                      type="button"
                      class="danger"
                      :disabled="actionBusy === `return-decision-${item.id}`"
                      @click="emit('decideReturn', item.id, false, returnNotes[item.id] || '不符合退款条件')"
                    ><CircleOff :size="15" /> 驳回申请</button>
                  </div>
                </template>
                <p v-else class="admin-decision">商家说明：{{ item.adminNote || "—" }}</p>
              </article>
              <div v-if="!returns.length" class="admin-empty">暂无退款申请</div>
            </div>
          </div>
        </section>

        <section v-else-if="activeTab === 'reviews'" class="admin-reviews">
          <div class="split-heading">
            <div>
              <p>REVIEW MODERATION</p>
              <h1>商品评价管理</h1>
              <span>查看真实已购评价，控制前台展示并以商家身份回复。</span>
            </div>
            <span class="scope-note"><Star :size="16" /> {{ visibleReviews }} 条公开评价</span>
          </div>

          <div class="review-admin-list">
            <article v-for="review in reviews" :key="review.id" class="review-admin-card" :class="{ hidden: !review.visible }">
              <div class="review-product">
                <span class="review-avatar">{{ review.reviewerName.slice(0, 1).toUpperCase() }}</span>
                <div>
                  <strong>{{ review.productName }}</strong>
                  <span>{{ review.variantLabel }} · {{ review.reviewerName }}</span>
                  <code>{{ review.orderNo }}</code>
                </div>
              </div>
              <div class="review-content">
                <div class="review-stars">
                  <Star v-for="value in 5" :key="value" :size="16" :class="{ active: value <= review.rating }" fill="currentColor" />
                  <b>{{ review.rating }}.0</b>
                  <small>{{ formatTime(review.createdAt) }}</small>
                </div>
                <p>{{ review.content }}</p>
                <blockquote v-if="review.merchantReply">商家回复：{{ review.merchantReply }}</blockquote>
                <form @submit.prevent="emit('replyReview', review.id, reviewReplies[review.id] || '')">
                  <input v-model="reviewReplies[review.id]" required maxlength="500" placeholder="输入商家回复" />
                  <button type="submit"><Send :size="14" /> 回复</button>
                </form>
              </div>
              <button
                type="button"
                class="visibility-button"
                @click="emit('setReviewVisibility', review.id, !review.visible)"
              >
                {{ review.visible ? "隐藏评价" : "恢复展示" }}
              </button>
            </article>
            <div v-if="!reviews.length" class="admin-empty">还没有商品评价</div>
          </div>
        </section>

        <section v-else class="admin-categories">
          <div class="split-heading">
            <div><p>PRODUCT TAXONOMY</p><h1>商品分类管理</h1><span>分类用于前台导航和商品筛选，停用后不能继续创建该分类商品。</span></div>
          </div>
          <div class="category-admin-layout">
            <form class="admin-form-card category-create" data-testid="category-form" @submit.prevent="addCategory">
              <div class="form-card-heading"><span><FolderTree :size="21" /></span><div><strong>创建分类</strong><small>建议按前台展示顺序设置排序</small></div></div>
              <label><span>分类名称</span><input v-model="categoryForm.name" required maxlength="80" placeholder="例如：智能家居" data-testid="category-name" /></label>
              <label><span>排序值</span><input v-model.number="categoryForm.sortOrder" required min="0" type="number" /><small>数值越小越靠前</small></label>
              <button class="admin-primary" type="submit" :disabled="actionBusy === 'category-create'" data-testid="create-category-submit"><Plus :size="17" /> 创建分类</button>
            </form>
            <div class="category-list-card">
              <div class="list-card-heading"><div><strong>现有分类</strong><small>{{ categories.filter((item) => item.active).length }} 个启用</small></div><span>{{ categories.length }} 个分类</span></div>
              <div class="category-table">
                <div class="category-head"><span>排序</span><span>分类名称</span><span>商品数量</span><span>状态</span><span>操作</span></div>
                <div v-for="category in categories" :key="category.id" class="category-row">
                  <strong>{{ String(category.sortOrder).padStart(2, "0") }}</strong>
                  <span><Tag :size="17" /><b>{{ category.name }}</b></span>
                  <span>{{ products.filter((item) => item.categoryId === category.id).length }} 个商品</span>
                  <span class="publish-status" :class="{ active: category.active }">{{ category.active ? "已启用" : "已停用" }}</span>
                  <button type="button" @click="emit('setCategoryStatus', category.id, !category.active)">{{ category.active ? "停用" : "启用" }}</button>
                </div>
              </div>
            </div>
          </div>
        </section>
      </main>
    </div>
  </div>
</template>
