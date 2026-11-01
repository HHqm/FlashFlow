<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import {
  Activity,
  CheckCircle2,
  Database,
  History,
  ListChecks,
  RefreshCw,
  RotateCcw,
  Scale,
  ScanSearch,
  TriangleAlert,
  Wrench,
} from "@lucide/vue";
import {
  getStockReconciliation,
  listCompensationTasks,
  listReconciliationLogs,
  retryCompensationTask,
  runStockReconciliation,
} from "../api";
import type {
  Campaign,
  CompensationTask,
  ReconciliationLog,
  ReconciliationReport,
  StockState,
} from "../types";

const props = defineProps<{
  token: string;
  campaigns: Campaign[];
}>();

const report = ref<ReconciliationReport | null>(null);
const tasks = ref<CompensationTask[]>([]);
const logs = ref<ReconciliationLog[]>([]);
const busy = ref<"" | "load" | "run" | `retry-${number}`>("");
const error = ref("");

const campaignById = computed(
  () => new Map(props.campaigns.map((campaign) => [campaign.id, campaign])),
);
const pendingTasks = computed(() => tasks.value.filter((task) => task.status === "PENDING").length);
const failedTasks = computed(() => tasks.value.filter((task) => task.status === "FAILED").length);

const stateLabels: Record<StockState, string> = {
  CONSISTENT: "一致",
  DRIFT_OBSERVED: "观察中",
  REPAIRED: "已修复",
  PENDING_COMPENSATION: "补偿中",
  CAS_CONFLICT: "并发跳过",
  MISSING_KEY: "缓存缺失",
};

const reasonLabels: Record<CompensationTask["reason"], string> = {
  ORDER_WRITE_FAILED: "下单落库失败",
  PAYMENT_TIMEOUT: "超时关单",
  CUSTOMER_CANCELLED: "用户取消",
  REFUND_APPROVED: "退款通过",
};

async function load() {
  busy.value = "load";
  error.value = "";
  try {
    [report.value, tasks.value, logs.value] = await Promise.all([
      getStockReconciliation(props.token),
      listCompensationTasks(props.token),
      listReconciliationLogs(props.token),
    ]);
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "加载失败";
  } finally {
    busy.value = "";
  }
}

async function runNow() {
  busy.value = "run";
  error.value = "";
  try {
    report.value = await runStockReconciliation(props.token);
    logs.value = await listReconciliationLogs(props.token);
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "对账失败";
  } finally {
    busy.value = "";
  }
}

async function retry(task: CompensationTask) {
  busy.value = `retry-${task.id}`;
  error.value = "";
  try {
    const updated = await retryCompensationTask(task.id, props.token);
    tasks.value = tasks.value.map((item) => (item.id === updated.id ? updated : item));
  } catch (exception) {
    error.value = exception instanceof Error ? exception.message : "重试失败";
  } finally {
    busy.value = "";
  }
}

function campaignName(campaignId: number) {
  const campaign = campaignById.value.get(campaignId);
  return campaign ? `${campaign.productName} · ${campaign.colorName}` : `活动 #${campaignId}`;
}

function signed(value: number | null) {
  if (value === null) return "—";
  return value > 0 ? `+${value}` : `${value}`;
}

function formatTime(value: string | null) {
  return value ? new Date(value).toLocaleString("zh-CN", { hour12: false }) : "—";
}

onMounted(load);
</script>

<template>
  <section class="admin-inventory">
    <div class="split-heading">
      <div>
        <p>STOCK RECONCILIATION</p>
        <h1>库存对账与补偿</h1>
        <span>MySQL 是库存事实来源。Redis 补偿失败会落库重试，残留漂移由对账任务在静默窗口后用 Lua CAS 修复。</span>
      </div>
      <div class="inventory-actions">
        <button type="button" :disabled="busy !== ''" @click="load">
          <RefreshCw :size="15" :class="{ spin: busy === 'load' }" /> 只读检查
        </button>
        <button class="admin-primary" type="button" :disabled="busy !== ''" data-testid="run-reconciliation" @click="runNow">
          <Activity v-if="busy === 'run'" class="spin" :size="15" /><Wrench v-else :size="15" /> 立即对账
        </button>
      </div>
    </div>

    <p v-if="error" class="inventory-error"><TriangleAlert :size="15" /> {{ error }}</p>

    <div class="inventory-metrics">
      <article><ScanSearch :size="18" /><small>进行中活动</small><strong>{{ report?.campaigns ?? "—" }}</strong></article>
      <article class="ok"><CheckCircle2 :size="18" /><small>Redis 与 MySQL 一致</small><strong>{{ report?.consistent ?? "—" }}</strong></article>
      <article :class="{ warn: (report?.drifting ?? 0) > 0 }"><Scale :size="18" /><small>存在漂移</small><strong>{{ report?.drifting ?? "—" }}</strong></article>
      <article :class="{ warn: pendingTasks > 0 }"><RotateCcw :size="18" /><small>待重试补偿</small><strong>{{ pendingTasks }}</strong></article>
      <article :class="{ danger: failedTasks > 0 }"><TriangleAlert :size="18" /><small>耗尽重试</small><strong>{{ failedTasks }}</strong></article>
    </div>

    <div class="inventory-card">
      <div class="list-card-heading">
        <div>
          <strong>活动库存对比</strong>
          <small>静默窗口 {{ report?.quietWindowSeconds ?? "—" }} 秒 · 检查于 {{ formatTime(report?.checkedAt ?? null) }}</small>
        </div>
        <span><Database :size="13" /> 漂移 = Redis − MySQL</span>
      </div>
      <div class="inventory-table">
        <div class="inventory-head stock-grid"><span>活动</span><span>MySQL</span><span>Redis</span><span>漂移</span><span>补偿中</span><span>状态</span></div>
        <div v-for="item in report?.items ?? []" :key="item.campaignId" class="inventory-row stock-grid">
          <span><strong>{{ campaignName(item.campaignId) }}</strong><code>#{{ item.campaignId }} · 总量 {{ item.totalStock }}</code></span>
          <span>{{ item.databaseStock }}</span>
          <span>{{ item.redisStock ?? "缺失" }}</span>
          <span :class="{ negative: (item.drift ?? 0) < 0, positive: (item.drift ?? 0) > 0 }">{{ signed(item.drift) }}</span>
          <span>{{ item.pendingCompensations }}</span>
          <span><b class="stock-state" :class="item.state.toLowerCase()">{{ stateLabels[item.state] }}</b></span>
        </div>
        <p v-if="report && report.items.length === 0" class="inventory-empty">当前没有进行中的活动</p>
      </div>
    </div>

    <div class="admin-two-columns inventory-columns">
      <div class="inventory-card">
        <div class="list-card-heading">
          <div><strong>Redis 补偿任务</strong><small>与库存回补同事务落库，提交后立即执行，失败指数退避重试</small></div>
          <span><ListChecks :size="13" /> 最近 {{ tasks.length }} 条</span>
        </div>
        <div class="inventory-table">
          <div class="inventory-head task-grid"><span>订单</span><span>原因</span><span>状态</span><span>重试</span><span></span></div>
          <div v-for="task in tasks" :key="task.id" class="inventory-row task-grid">
            <span><strong>{{ task.orderNo }}</strong><code>{{ campaignName(task.campaignId) }} · {{ task.quantity }} 件</code></span>
            <span>{{ reasonLabels[task.reason] }}</span>
            <span>
              <b class="task-status" :class="task.status.toLowerCase()">{{ task.status === "DONE" ? (task.outcome === "NOOP" ? "已完成（幂等跳过）" : "已回补") : task.status === "PENDING" ? "待重试" : "失败" }}</b>
              <small v-if="task.lastError" :title="task.lastError">{{ task.lastError }}</small>
            </span>
            <span>{{ task.retryCount }}</span>
            <span>
              <button v-if="task.status === 'FAILED'" type="button" :disabled="busy !== ''" @click="retry(task)">
                <Activity v-if="busy === `retry-${task.id}`" class="spin" :size="13" /><RotateCcw v-else :size="13" /> 重试
              </button>
            </span>
          </div>
          <p v-if="tasks.length === 0" class="inventory-empty">暂无补偿任务</p>
        </div>
      </div>

      <div class="inventory-card">
        <div class="list-card-heading">
          <div><strong>修复审计日志</strong><small>每次 CAS 修复都会记录修复前后的 Redis 值</small></div>
          <span><History :size="13" /> 最近 {{ logs.length }} 条</span>
        </div>
        <div class="inventory-table">
          <div class="inventory-head log-grid"><span>活动</span><span>Redis 修复</span><span>漂移</span><span>来源</span><span>时间</span></div>
          <div v-for="log in logs" :key="log.id" class="inventory-row log-grid">
            <span><strong>{{ campaignName(log.campaignId) }}</strong></span>
            <span>{{ log.redisStockBefore }} → {{ log.redisStockAfter }}</span>
            <span :class="{ negative: log.drift < 0, positive: log.drift > 0 }">{{ signed(log.drift) }}</span>
            <span>{{ log.triggerSource === "MANUAL" ? "手动" : "定时" }}</span>
            <span>{{ formatTime(log.createdAt) }}</span>
          </div>
          <p v-if="logs.length === 0" class="inventory-empty">尚无修复记录，说明补偿链路没有遗漏</p>
        </div>
      </div>
    </div>
  </section>
</template>
