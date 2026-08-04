# 简历项目描述模板

## 项目名称

FlashFlow 高并发限量购与订单履约平台

## 技术栈

Java 21、Spring Boot 4.1、Spring Cloud Gateway、Spring Security、MySQL 8.4、Redis 8、Lua、Kafka 4.3、JPA、Flyway、Vue 3、TypeScript、Docker Compose、Prometheus、JUnit 5

## 项目描述

面向限量商品瞬时高并发场景设计的微服务电商系统，包含用户商城、运营后台、API 网关、交易服务和异步通知服务；商品域采用类目、SPU、SKU、秒杀活动四层模型，覆盖多字段检索、运营定价、上下架、库存划拨、鉴权限流、订单支付与取消、退款审批、已购评价、可靠消息、消费幂等、监控与容器化交付。

## 可直接使用的职责亮点

- 设计类目、SPU、SKU、秒杀活动四层商品模型，支持多颜色/规格独立库存、商品与 SKU 启停、活动库存划拨和个人限购，并通过角色权限隔离运营与购买链路；
- 实现覆盖商品名称、描述、类目、SKU 编码、颜色和规格的组合检索，支持价格/评分排序、可见评价聚合和管理员 SKU 独立定价，并校验原价与未结束活动价的约束；
- 设计 Redis Lua 原子预占方案，将按数量库存校验与扣减、累计限购和请求幂等合并为单次原子执行，并以 MySQL `remaining_stock >= quantity` 条件更新作为最终防超卖屏障；
- 基于 `Idempotency-Key + 用户 ID`、数据库唯一索引和 Kafka Inbox 表构建 HTTP、订单、消费三层幂等，处理客户端重试与消息重复投递；
- 使用 Transactional Outbox 将订单与领域事件纳入同一 MySQL 事务，通过 Kafka `acks=all`、幂等 Producer、指数退避和 DLT 实现至少一次可靠投递；
- 实现 `待支付→已支付→退款中→已退款` 订单状态机和独立售后申请模型，支持主动取消、15 分钟超时关单、退款审批及审计意见，并在事务提交后通过带订单号校验的 Lua 脚本补偿 Redis；
- 建立“已支付订单才能评价、每单一次”的已购评价约束，支持 1～5 星评分、前台平均分聚合、后台显隐审核和商家回复；
- 搭建 Spring Cloud Gateway 统一入口，实现 JWT 双重校验、Redis 令牌桶限流和 Trace ID 透传，使用 Actuator/Prometheus 暴露 JVM、HTTP 与业务指标；
- 采用 Maven 多模块、Flyway 版本化建表、Docker Compose 一键环境和 GitHub Actions 自动测试，保证项目可复现交付。

## 有实测数据后再添加

请将方括号替换为真实数据，并保留测试条件：

> 在 `[CPU/内存/Docker 配置]` 环境下使用 k6 以 `[并发用户数]` 压测 `[持续时间]`，入口吞吐达到 `[QPS]`，P95/P99 为 `[数值]`，错误率 `[数值]`；压测后核对 MySQL 初始库存、成功订单数与剩余库存一致，无负库存。

## 根据年限裁剪

应届/初级保留 3-4 条，重点讲清 Lua、防超卖、Outbox 和幂等；有经验岗位可增加故障模型、容量规划、索引设计、监控指标和演进到 CDC/Kubernetes 的取舍。
