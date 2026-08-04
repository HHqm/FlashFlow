<script setup lang="ts">
import { computed, reactive } from "vue";
import {
  Activity,
  ArrowRight,
  Database,
  Eye,
  EyeOff,
  KeyRound,
  LockKeyhole,
  Radio,
  ShieldCheck,
  Zap,
} from "@lucide/vue";
import type { ServiceHealth } from "../types";

const props = defineProps<{
  busy: boolean;
  error: string;
  services: ServiceHealth[];
}>();

const emit = defineEmits<{
  login: [credentials: { username: string; password: string }];
}>();

const form = reactive({
  username: "demo",
  password: "Demo@123456",
});
const showPassword = reactive({ value: false });

const allHealthy = computed(
  () => props.services.length > 0 && props.services.every((service) => service.status === "UP"),
);

function useAccount(type: "demo" | "admin") {
  if (type === "admin") {
    form.username = "admin";
    form.password = "Admin@123456";
  } else {
    form.username = "demo";
    form.password = "Demo@123456";
  }
}

function submit() {
  emit("login", { username: form.username.trim(), password: form.password });
}
</script>

<template>
  <main class="login-page">
    <section class="login-story" aria-labelledby="login-title">
      <div class="brand brand-light">
        <span class="brand-mark"><Zap :size="21" :stroke-width="2.4" /></span>
        <span>FlashFlow</span>
      </div>

      <div class="story-copy">
        <p class="eyebrow eyebrow-light">HIGH CONCURRENCY COMMERCE</p>
        <h1 id="login-title">把一次秒杀，<br />做成一套工程能力。</h1>
        <p class="story-lead">
          从 Redis 原子扣减到 Kafka 异步履约，在一个可操作的业务界面里展示高并发交易链路。
        </p>

        <div class="capability-list" aria-label="平台核心能力">
          <div class="capability-item">
            <Database :size="20" />
            <div>
              <strong>Redis + Lua</strong>
              <span>库存预扣、限流与分布式锁</span>
            </div>
          </div>
          <div class="capability-item">
            <Radio :size="20" />
            <div>
              <strong>Kafka Outbox</strong>
              <span>事务消息、幂等消费与死信处理</span>
            </div>
          </div>
          <div class="capability-item">
            <ShieldCheck :size="20" />
            <div>
              <strong>JWT + 幂等</strong>
              <span>网关鉴权与重复请求防护</span>
            </div>
          </div>
        </div>
      </div>

      <div class="story-footer">
        <span class="live-dot" :class="{ muted: !allHealthy }"></span>
        {{ allHealthy ? "核心服务全部在线" : "正在连接本地服务" }}
        <span class="story-divider"></span>
        Java 21 · Spring Boot · MySQL
      </div>
    </section>

    <section class="login-panel" aria-label="登录">
      <div class="login-box">
        <div class="mobile-brand brand">
          <span class="brand-mark"><Zap :size="20" /></span>
          <span>FlashFlow</span>
        </div>

        <div class="login-heading">
          <span class="login-icon"><KeyRound :size="22" /></span>
          <p class="eyebrow">COMMERCE PLATFORM</p>
          <h2>登录 FlashFlow</h2>
          <p>用户进入秒杀商城，管理员进入独立运营后台。</p>
        </div>

        <div class="account-switch" aria-label="选择演示账号">
          <button
            type="button"
            :class="{ active: form.username === 'demo' }"
            data-testid="demo-account"
            @click="useAccount('demo')"
          >
            普通用户
            <span>浏览、抢购与支付</span>
          </button>
          <button
            type="button"
            :class="{ active: form.username === 'admin' }"
            data-testid="admin-account"
            @click="useAccount('admin')"
          >
            管理员
            <span>商品、SKU 与活动运营</span>
          </button>
        </div>

        <form @submit.prevent="submit">
          <label class="field">
            <span>用户名</span>
            <span class="input-wrap">
              <LockKeyhole :size="17" />
              <input
                v-model="form.username"
                name="username"
                autocomplete="username"
                aria-label="用户名"
                data-testid="username"
                required
              />
            </span>
          </label>

          <label class="field">
            <span>密码</span>
            <span class="input-wrap">
              <ShieldCheck :size="17" />
              <input
                v-model="form.password"
                name="password"
                :type="showPassword.value ? 'text' : 'password'"
                autocomplete="current-password"
                aria-label="密码"
                data-testid="password"
                required
              />
              <button
                class="password-toggle"
                type="button"
                :aria-label="showPassword.value ? '隐藏密码' : '显示密码'"
                @click="showPassword.value = !showPassword.value"
              >
                <EyeOff v-if="showPassword.value" :size="17" />
                <Eye v-else :size="17" />
              </button>
            </span>
          </label>

          <p v-if="error" class="form-error" role="alert">{{ error }}</p>

          <button
            class="primary-button login-button"
            type="submit"
            :disabled="busy"
            data-testid="login-submit"
          >
            <Activity v-if="busy" class="spin" :size="18" />
            <span>{{ busy ? "正在验证服务..." : "登录并进入" }}</span>
            <ArrowRight v-if="!busy" :size="18" />
          </button>
        </form>

        <p class="login-note">
          本地演示环境 · 登录信息仅用于当前浏览器会话
        </p>
      </div>
    </section>
  </main>
</template>
