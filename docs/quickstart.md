# FlashFlow 本地使用教程

这份教程用于从 GitHub 克隆项目，在 Windows + Docker Desktop 环境中启动 FlashFlow，并走通用户商城和管理员后台。

## 1. 环境要求

- Windows 10/11
- Docker Desktop（启用 Linux containers）
- Docker Compose v2.20+
- 预留约 12 GB Docker 磁盘空间

Java 21 和 Node.js 不是启动容器的必需条件；只有本地修改后需要运行 Maven 测试或前端构建时才需要安装。

## 2. 获取代码

```powershell
git clone https://github.com/HHqm/FlashFlow.git
cd FlashFlow
```

## 3. 配置本地环境

首次使用时复制配置模板：

```powershell
Copy-Item .env.example .env
```

本地演示可以使用模板默认值。生产环境必须替换数据库密码、Redis 密码和 JWT 密钥，并关闭演示数据初始化。

## 4. 启动服务

启动核心服务：

```powershell
docker compose up -d --build
```

如果需要 Kafka 管理台和 Prometheus：

```powershell
docker compose --profile tools up -d --build
```

查看容器状态：

```powershell
docker compose ps
```

看到 `frontend`、`gateway-service`、`trade-service`、`notification-service`、`mysql`、`redis` 和 `kafka` 处于 `Up` 即可访问系统。

## 5. 访问地址与账号

| 地址 | 说明 |
| --- | --- |
| http://localhost:3000 | 用户商城与管理员后台 |
| http://localhost:8080 | API Gateway |
| http://localhost:8090 | Kafka UI，需启动 tools profile |
| http://localhost:9090 | Prometheus，需启动 tools profile |

| 账号 | 密码 | 用途 |
| --- | --- | --- |
| `demo` | `Demo@123456` | 浏览、搜索、地址、秒杀、支付、收货、退款、评价 |
| `admin` | `Admin@123456` | 商品、SKU、价格、活动、发货、售后和评价运营 |

管理员账号只拥有运营权限，不能创建订单或支付。

## 6. 推荐演示流程

### 用户端

1. 使用 `demo` 登录。
2. 在搜索框输入商品名称、颜色或规格，例如 `Watch`、`Pro`。
3. 在“收货地址”中新增、编辑或选择默认地址。
4. 在秒杀卡片中调整数量，点击“立即抢购”并确认本单地址。
5. 在“我的订单”中点击支付；待支付订单可以主动取消。
6. 管理员发货后，用户可查看承运商和运单号并确认收货。
7. 已支付、已发货或已收货订单可以申请退款；确认收货后可以提交评价。

### 管理端

1. 退出用户账号，使用 `admin` 登录。
2. 在“商品与 SKU”中创建商品、增加颜色/规格 SKU、修改售价和库存、上下架。
3. 在“秒杀活动”中配置活动价、活动库存、限购数量和活动时间。
4. 在“订单与售后”中为已支付订单录入承运商和运单号，并审批或驳回退款申请。
5. 在“商品评价”中查看已购评价、隐藏/恢复评价并回复买家。

## 7. API 流程示例（PowerShell）

```powershell
$login = Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/v1/auth/login" `
  -ContentType "application/json" `
  -Body (@{ username = "demo"; password = "Demo@123456" } | ConvertTo-Json)
$token = $login.data.accessToken
$headers = @{ Authorization = "Bearer $token"; "Idempotency-Key" = [guid]::NewGuid().ToString("N") }

$campaigns = Invoke-RestMethod "http://localhost:8080/api/v1/campaigns?keyword=Watch"
$campaignId = $campaigns.data[0].id
$addresses = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/addresses" `
  -Headers @{ Authorization = "Bearer $token" }
$addressId = $addresses.data[0].id
$order = Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/v1/campaigns/$campaignId/orders" `
  -Headers $headers -ContentType "application/json" `
  -Body (@{ quantity = 1; addressId = $addressId } | ConvertTo-Json)
$orderNo = $order.data.orderNo

Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/v1/orders/$orderNo/pay" `
  -Headers @{ Authorization = "Bearer $token" }
```

更多接口和请求体见 [OpenAPI 契约](openapi.yaml) 与 [IntelliJ HTTP 请求集](api.http)。

## 8. 停止与清理

停止容器但保留数据库数据：

```powershell
docker compose down
```

删除本项目容器和数据卷（会清空本地演示数据，请确认后再执行）：

```powershell
docker compose down -v
```

## 9. 常见问题

- 页面提示服务未启动：执行 `docker compose ps`，等待 MySQL、Redis、Kafka 健康检查通过后刷新页面。
- 端口被占用：修改 `compose.yml` 左侧宿主机端口，容器内部端口不要改。
- 想重新生成演示数据：执行 `docker compose down -v` 后重新 `docker compose up -d --build`。
- 想查看异常：执行 `docker compose logs -f gateway-service trade-service notification-service`。
