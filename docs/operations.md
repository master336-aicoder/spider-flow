# 运维手册

面向日常值守：看什么、怎么查、出问题按什么顺序排。命令均以 Linux 为例，Windows 下换 `netstat -ano | findstr 8088` / 任务计划程序。

安装与参数口径见 [deployment.md](deployment.md)，风险项见 [security.md](security.md)。

---

## 1. 巡检清单

| # | 检查项 | 命令 / 入口 | 健康判据 |
| --- | --- | --- | --- |
| 1 | 进程与端口 | `ss -lntp \| grep 8088` | 监听中 |
| 2 | 首页可用 | `curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8088/` | `200`（应用未提供 actuator 健康端点，只能这样探活） |
| 3 | JVM 运行时长/线程 | `curl -s :8088/system/status \| jq .jvm` | `uptime` 与预期重启时间一致；`threadCount` 无持续增长 |
| 4 | 堆内存水位 | `curl -s :8088/system/status \| jq .memory` | `used/max` 长期 < 80% |
| 5 | 爬虫线程池 | `curl -s :8088/system/status \| jq .spider` | `poolQueueSize` 不持续增大；`poolActiveThreads ≤ poolMaxThreads` |
| 6 | 调度状态 | 同上 `jobEnable` / `runningJobs` / `scheduledJobs` / `quartzJobCount` | 打开开关后 `quartzJobCount` 应与"启用且有 cron"的流程数一致 |
| 7 | 任务趋势 | 页面「工作台」或 `curl -s ':8088/dashboard/summary?days=7'` | 当日任务数、平均耗时符合历史 |
| 8 | 库与表体积 | `curl -s :8088/dashboard/database \| jq .main` | `sizeBytes`、`tableCount` 无异常跳增 |
| 9 | 数据源连通性 | 同接口 `.datasources[]`（`connected`/`costMs`） | 全为 `true`，`costMs` 个位数毫秒 |
| 10 | 磁盘余量 | `df -h <spider.workspace>` | 见 §4 清理策略 |
| 11 | 备份新鲜度 | `ls -l` 备份目录 | ≤24h（见 §5） |

字段定义位置：[SystemStatusService.java:40-104](../spider-flow-core/src/main/java/org/spiderflow/core/service/SystemStatusService.java#L40)、[DashboardController.java:72-91](../spider-flow-web/src/main/java/org/spiderflow/controller/DashboardController.java#L72)。

> 注意：以上接口**都不需要登录**——本应用没有鉴权。把它们接到内网监控，不要暴露到公网。

---

## 2. 指标采集

没有 Micrometer/actuator，最小采集脚本（cron + 文本指标）：

```bash
#!/bin/bash
BASE=http://127.0.0.1:8088
j() { curl -s --max-time 5 "$1" | jq -r "$2"; }
echo "sf_up $(j $BASE/system/status '.jvm.uptime' | grep -qE '[0-9]' && echo 1 || echo 0)"
echo "sf_heap_used $(j $BASE/system/status '.memory.used')"
echo "sf_pool_queue $(j $BASE/system/status '.spider.poolQueueSize')"
echo "sf_running_jobs $(j $BASE/system/status '.spider.runningJobs')"
echo "sf_job_enable $(j $BASE/system/status '.spider.jobEnable')"
```

接入 Prometheus 时可用 `textfile` collector 或 nginx log 侧的接口可用性替代。

---

## 3. 日志

### 3.1 三类日志

| 日志 | 路径 / 位置 | 写入方 | 说明 |
| --- | --- | --- | --- |
| 应用日志 | `<workspace>/logs/spider-flow.log` + 控制台 | logback `File`/`Stdout` | 任务线程内的日志**不进这里**，见下一行 |
| 单次任务日志 | `<workspace>/<flowId>/logs/<taskId>.log` | 自定义 appender 按 `SpiderContextHolder` 路由到任务输出流 | 页面「执行日志」与 `/spider/log`、`/spider/log/download` 读的就是它 |
| 实时推送 | WebSocket `/ws` | `SpiderFlowWebSocketAppender` | 调试窗与在线日志用它 |

依据：[logback-spring.xml:13-22](../spider-flow-web/src/main/resources/logback-spring.xml#L13)、路由逻辑 [SpiderFlowFileAppender.java:17-23](../spider-flow-web/src/main/java/org/spiderflow/logback/SpiderFlowFileAppender.java#L17)、任务日志路径 [SpiderFlowController.java:173](../spider-flow-web/src/main/java/org/spiderflow/controller/SpiderFlowController.java#L173)、`@ServerEndpoint("/ws")` [WebSocketEditorServer.java:21](../spider-flow-web/src/main/java/org/spiderflow/websocket/WebSocketEditorServer.java#L21)。

### 3.2 调日志级别

`logging.level.root` 由 logback 在启动时读取（[logback-spring.xml:3](../spider-flow-web/src/main/resources/logback-spring.xml#L3)），**改了要重启**，系统设置页不含这一项。调试单个模块用：

```
logging.level.org.spiderflow=DEBUG
```

### 3.3 Windows 控制台乱码

日志文件是 UTF-8，控制台是 GBK。看文件时统一：

```bash
iconv -f UTF-8 -t GBK spider-flow.log | grep 关键字   # 仅 Windows 终端需要
```

不要用 `iconv -f GBK -t UTF-8` 转 JSON 接口响应——HTTP 响应本身是 UTF-8，转了会得到 `鎵ц…` 一类乱码。

---

## 4. 磁盘与清理

`spider.workspace` 下的 `logs/`、`xmls/`、`url.bf` 与 `spider-flow.log` **均无自动滚动或过期清理**（appender 是 `FileAppender`，不是 `RollingFileAppender`）。上线后必须挂外部清理：

```bash
# crontab -e（示例：应用日志按天切割保留 14 天，任务日志保留 30 天）
0 3 * * * find /data/spider -name '*.log' -type f -mtime +30 -delete
0 3 * * * find /data/spider -path '*/xmls/*' -type f -mtime +90 -delete
```

应用日志切割：把 `logback-spring.xml` 的 `File` appender 换成 `ch.qos.logback.core.rolling.RollingFileAppender` + `TimeBasedRollingPolicy`，但**必须保留 `org.spiderflow.logback.SpiderFlowFileAppender` 的按线程路由能力**（它继承 `FileAppender` 并覆写 `subAppend`）。做法是继承链换成 `RollingFileAppender` 后重测"任务日志分文件"是否仍生效——本分支未做该改造，属未验证项。

`url.bf` 是 URL 去重状态，删了会导致该流程重复抓取已抓过的 URL；清理前确认业务可接受。

数据库侧增长集中在 `sp_task`（每次运行一行）与 `sp_flow.xml`（流程定义）。归档示例：

```sql
delete from sp_task where begin_time < date_sub(curdate(), interval 180 day);
```

`sp_task` 无外键约束到 `sp_flow`，历史任务在流程删除后仍保留（工作台以"已删除流程"显示，见 [DashboardController.java:180](../spider-flow-web/src/main/java/org/spiderflow/controller/DashboardController.java#L180)）。

---

## 5. 备份与恢复

### 5.1 备份

```bash
mysqldump -uroot -p --single-transaction --routines --databases spiderflow \
  | gzip > /backup/spiderflow.$(date +%F).sql.gz
tar czf /backup/spider-ws.$(date +%F).tar.gz -C /data spider
```

数据库里含**所有数据源口令**（`sp_datasource.password`），备份文件按敏感数据保管，不要放进公共对象存储。

### 5.2 恢复

```bash
gunzip -c spiderflow.2026-11-04.sql.gz | mysql -uroot -p
systemctl stop spider-flow
rm -rf /data/spider && tar xzf spider-ws.2026-11-04.tar.gz -C /data
systemctl start spider-flow
curl -s :8088/system/status | jq '.spider.workspace'   # 确认目录一致
```

恢复后必查：① 工作台数据源连通性是否全绿；② 有 cron 的流程是否需要重新触发（内存 JobStore，重启即按 `sp_flow.cron` 重建，无遗留 misfire 表）。

---

## 6. 任务运维

| 操作 | 页面入口 | 接口 |
| --- | --- | --- |
| 立即运行 | 爬虫列表「运行」 | `/spider/run`、`/rest/run/{id}`（同步）、`/rest/runAsync/{id}` |
| 停止运行中任务 | 列表/任务详情「停止」 | `/spider/stop`、`/task/stop`、`/rest/stop/{taskId}` |
| 查任务状态 | 任务详情 | `/rest/status/{taskId}`、`/task/list` |
| 查执行日志 | 「执行日志」页 | `/spider/log`（分页 + 关键字 + 正则 + 倒序）、`/spider/log/download` |
| 开关全部定时任务 | 系统设置 | `/system/params/save`（保存后立刻重建调度） |
| 单流程定时 | 爬虫编辑页 cron | `/spider/cron`、`/spider/recent5TriggerTime` |

接口全清单见 [api.md](api.md)。

**批量停任务**没有接口，只能逐个 `/task/stop`；应急止血更彻底的做法是系统设置里把 `spider.job.enable` 设为 `false`（对已在跑的任务无效，只挡住后续触发）。

**死循环**：测试运行时节点执行次数超过 `spider.detect.dead-cycle`（默认 5000）判定死循环并终止；长跑任务请调低该值再重跑。

**线程池**：`spider.thread.max` 即时 resize（扩容先改 max 再改 core，缩容反之，见 [SpiderFlowThreadPoolExecutor.java:83-93](../spider-flow-api/src/main/java/org/spiderflow/concurrent/SpiderFlowThreadPoolExecutor.java#L83)）。判断是否该扩容看 `poolQueueSize` 是否持续 > 0，而不是看 `poolActiveThreads` 打满。

---

## 7. 数据源与 SQL 通道

「数据源管理 → 数据库」抽屉与 autoSQL 节点共用后端 `/database/*`（[DatabaseController.java](../spider-flow-web/src/main/java/org/spiderflow/controller/DatabaseController.java)）：

| 限制 | 值 | 位置 |
| --- | --- | --- |
| 单次返回行数 | 500（`truncated=true` 表示被截断） | `MAX_ROWS`，[DatabaseController.java:36](../spider-flow-web/src/main/java/org/spiderflow/controller/DatabaseController.java#L36) |
| SQL 超时 | 30 秒 | `QUERY_TIMEOUT`，[:41](../spider-flow-web/src/main/java/org/spiderflow/controller/DatabaseController.java#L41) |
| 工作台数据源探测超时 | 3 秒 | [DashboardController.java:48](../spider-flow-web/src/main/java/org/spiderflow/controller/DashboardController.java#L48) |
| autoSQL 批量插入 | 每批 500 行 | [AutoSQLExecutor.java:70](../spider-flow-core/src/main/java/org/spiderflow/core/executor/shape/AutoSQLExecutor.java#L70) |
| autoSQL 字符串列 | `VARCHAR(5000)` 上限 | [:75](../spider-flow-core/src/main/java/org/spiderflow/core/executor/shape/AutoSQLExecutor.java#L75) |

运维要点：

- 元数据/执行接口会借用 Druid 连接池的连接并**切换 catalog/schema**，返回前显式还原（[DatabaseController.java:174-184](../spider-flow-web/src/main/java/org/spiderflow/controller/DatabaseController.java#L174)、autoSQL 同样处理 [:118-130](../spider-flow-core/src/main/java/org/spiderflow/core/executor/shape/AutoSQLExecutor.java#L118)）。若日志出现"表建到了别的库"，先确认是否有人在旧版本（未还原）实例上执行过 `use`，再核对 `sp_datasource.jdbc_url` 默认库。
- Druid 1.1.16 的 `DruidPooledConnection.getSchema()` 会抛 `SQLFeatureNotSupportedException`，代码里已用 `readSchema()` 兜住；自研脚本直接调 `getSchema()` 时同样要注意。
- MySQL 把"数据库"报成 catalog，`getSchemas()` 为空——数据库详情抽屉里 schema 下拉显示"该数据源没有可选项"是正常现象，不是故障。
- autoSQL 的表名/库名/列名走白名单（只允许字母、数字、下划线、中文），非法值会被 `logger.warn` 跳过该节点而不是拼进 SQL。日志里搜 `不合法` 即可定位。

---

## 8. 故障排查

### 8.1 流程完全不执行

1. `/system/status` 看 `jobEnable`：`false` → 系统设置打开。
2. `sp_flow.enabled='1'` 且 `cron` 非空：`select id,name,enabled,cron from sp_flow where enabled='1';`
3. `quartzJobCount` 是否等于上一步条数；不等 → 保存一次流程或重启（触发 `refreshSchedules()`）。
4. 日志搜 `Quartz`/`SchedulerException`。

### 8.2 任务卡住不动

1. `<workspace>/<flowId>/logs/<taskId>.log` 最后几行停在哪个节点（页面日志查看器同数据）。
2. 卡在网络请求：调该节点 timeout/代理；卡在 SQL：看目标库锁等待。
3. `poolActiveThreads` 已满且 `poolQueueSize` 增长 → 并发不足或慢任务堆积，先降 `spider.thread.default` 观察是否单任务吃满。

### 8.3 内存持续上涨

1. `/system/status` `memory.used/max` 与 `threadCount` 一起看。
2. 线程数随时间只增不减 → 检查是否有流程泄漏（`runningJobs` 长期 > 0 但无 cron 触发）。
3. 布隆过滤器在内存中复用（`url.bf` 载入），容量参数越大占用越高；必要时删除对应 `url.bf` 并调低 `spider.bloomfilter.capacity`。

### 8.4 前端白屏 / 版本没更新

```bash
curl -s http://127.0.0.1:8088/ | grep -o 'js/app\.[a-z0-9]*\.js'   # 比对 jar 内 hash
unzip -l spider-flow.jar | grep 'static/index.html'
```

- `index.html` 是 no-cache 的（[application.properties:27](../spider-flow-web/src/main/resources/application.properties#L27)），仍是旧页面基本说明 jar 里就是旧包 → 见 deployment.md §4.2 的 `target/classes/static` 残留。
- 前端资源 404：确认 `dist` 在打包前已生成。

### 8.5 调试窗/在线日志没有输出

1. 浏览器 Network 里 `/ws` 是否 101；不是 101 → 反代没转发升级头。
2. `WebSocket` appender 在 root 上（logback-spring.xml:27），若被人移除则只有文件日志。

### 8.6 中文乱码

| 位置 | 原因 | 处理 |
| --- | --- | --- |
| 爬虫名称、参数保存后 | JDBC 未走 UTF-8 | url 带 `characterEncoding=UTF8`，库表 `utf8mb4` |
| 通知邮件主题/正文 | properties 按 ISO-8859-1 读取 | 用 `\uXXXX`（本仓库已改） |
| 控制台 | GBK 终端 | 见 §3.3 |

---

## 9. 值班速查

| 我要… | 用这个 |
| --- | --- |
| 看整体状态 | 页面「工作台」/ `GET /dashboard/summary` |
| 看库表体积 | `GET /dashboard/database` |
| 改并发/开关定时 | 页面「系统设置」/ `POST /system/params/save` |
| 查某次任务日志 | `GET /spider/log?id=&taskId=&index=&count=` |
| 下载任务日志 | `GET /spider/log/download?id=&taskId=` |
| 临时执行一条 SQL | 页面「数据源管理 → 数据库」SQL 页签 / `POST /database/execute`（**高危，见 security.md**） |
| 看进程内状态 | `GET /system/status` |

---

## 10. 未验证项（如实声明）

- 把 logback 换成滚动 appender 的改造**未在本分支实施与验证**（§4）。
- 监控采集脚本为示例，未在真实 Prometheus 环境跑通。
- 高并发（>64 线程）下的容量表现未压测；`spider.thread.max` 调整建议逐档观察 `poolQueueSize`。
- 本手册未在浏览器中肉眼复核页面文案，接口/路径均按代码与构建产物核对。
