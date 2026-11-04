# 接口清单

本应用的 HTTP 面。定义位置均在 [spider-flow-web/src/main/java/org/spiderflow/controller](../spider-flow-web/src/main/java/org/spiderflow/controller)。

> **所有接口都不需要身份认证**，且 CORS 对任意来源放开（[ResourcesConfiguration.java:15-21](../spider-flow-web/src/main/java/org/spiderflow/configuration/ResourcesConfiguration.java#L15)）。请只在内网/带反代鉴权的环境使用，见 [security.md](security.md)。

---

## 1. 通用约定

| 项 | 约定 | 依据 |
| --- | --- | --- |
| 请求方式 | GET/POST 均可（大多用 `@RequestMapping` 未限定方法）；前端统一 POST `application/x-www-form-urlencoded` | 各控制器 |
| 例外 | `/system/params/save` 用 `@RequestBody`（JSON），`/rest/run/{id}` 可选 JSON body | [SystemController.java:37](../spider-flow-web/src/main/java/org/spiderflow/controller/SystemController.java#L37)、[SpiderRestController.java:103](../spider-flow-web/src/main/java/org/spiderflow/controller/SpiderRestController.java#L103) |
| 统一响应 | `JsonBean`：`{ "code": 1, "message": "执行成功", "data": ... }`，**`code === 1` 才算成功** | [JsonBean.java:5-9](../spider-flow-api/src/main/java/org/spiderflow/model/JsonBean.java#L5) |
| 裸响应 | 部分接口直接返回对象/字符串，没有 `code` 包装：`/spider/list`（MyBatis-Plus `IPage`）、`/spider/get`、`/spider/other`、`/spider/save`、`/spider/xml`、`/spider/shapes`、`/spider/pluginConfigs`、`/spider/recent5TriggerTime`、`/spider/log/download` | [SpiderFlowController.java:104-213](../spider-flow-web/src/main/java/org/spiderflow/controller/SpiderFlowController.java#L104) |
| 日期格式 | `yyyy-MM-dd HH:mm:ss`，时区 `GMT+8` | [application.properties:21-22](../spider-flow-web/src/main/resources/application.properties#L21) |
| 前端调用层 | axios 封装**没有**集中校验 `code`（`successConditions.field` 为空即直接回调），是否成功由各页面自行判断；表单编码用 `qs.stringify` | [config/index.js:25-28](../frontend-vue/src/config/index.js#L25)、[util/request.js:68-82](../frontend-vue/src/util/request.js#L68)、[util/util.js:9](../frontend-vue/src/util/util.js#L9)；显式校验的例子 [views/datasources/database.vue:305](../frontend-vue/src/views/datasources/database.vue#L305)、[views/spider/templates/autoSQL.vue:134](../frontend-vue/src/views/spider/templates/autoSQL.vue#L134) |

---

## 2. 流程 `/spider`

| 方法 | 路径 | 参数 | 返回 |
| --- | --- | --- | --- |
| 分页列表 | `/spider/list` | `page`(默认1)、`limit`(默认1)、`name`(模糊) | `IPage<SpiderFlow>`（裸） |
| 保存流程 | `/spider/save` | `SpiderFlow` 表单字段（`id`、`name`、`xml`、`threadCount`…） | `String`（裸） |
| 读取流程 | `/spider/get` | `id` | `SpiderFlow`（裸） |
| 其它流程（子流程选择） | `/spider/other` | `id`（排除自身） | `List<SpiderFlow>`（裸） |
| 历史版本 | `/spider/history` | `id`、`timestamp` | `JsonBean<?>` |
| 删除 | `/spider/remove` | `id` | void |
| 启用/停用定时 | `/spider/start`、`/spider/stop` | `id` | void |
| 立即运行 | `/spider/run` | `id` | void |
| 设置 cron | `/spider/cron` | `id`、`cron` | void |
| 最近 5 次触发时间 | `/spider/recent5TriggerTime`（仅 GET） | `cron` | `List<String>`（裸） |
| 流程 XML | `/spider/xml` | `id` | `String`（裸） |
| 执行日志分页 | `/spider/log` | `id`、`taskId`、`keywords`、`index`、`count`、`reversed`、`matchcase`、`regx` | `JsonBean<List<Line>>` |
| 日志下载 | `/spider/log/download` | `id`、`taskId` | `ResponseEntity<FileSystemResource>`，附件名 `spider.log` |
| 可用图形 | `/spider/shapes` | — | `List<Shape>`（裸，插件注册） |
| 插件配置 | `/spider/pluginConfigs` | — | `List<Plugin>`（裸） |
| 语法提示 | `/spider/grammers` | — | `JsonBean<List<Grammer>>`（编辑器补全） |

`/spider/log` 读的是 `<workspace>/<flowId>/logs/<taskId>.log`（[SpiderFlowController.java:186](../spider-flow-web/src/main/java/org/spiderflow/controller/SpiderFlowController.java#L186)）。

---

## 3. 数据源 `/datasource`

| 路径 | 参数 | 说明 |
| --- | --- | --- |
| `/datasource/list` | `page`、`limit`、`name` | 分页 |
| `/datasource/all` | — | **返回全部数据源，含明文口令**，前端下拉用；高危，见 security.md §3 |
| `/datasource/save` | 表单：`id`、`name`、`driverClassName`、`jdbcUrl`、`username`、`password` | 新增/修改 |
| `/datasource/get` | `id` | 单条 |
| `/datasource/remove`（别名 `/delete`） | `id` | 删除，[DataSourceController.java:54](../spider-flow-web/src/main/java/org/spiderflow/controller/DataSourceController.java#L54) |
| `/datasource/test` | 同 save 字段 | 测试连通性 |

---

## 4. 数据库元数据与 SQL 执行 `/database`（本分支新增）

公共参数：`datasourceId`（必填，为空返回 `code=0`）、`catalog`、`schema`（留空表示"不限定"，会按会话当前库过滤）。

| 路径 | 额外参数 | 返回 `data` |
| --- | --- | --- |
| `/database/catalogs` | — | `List<String>` 数据库列表（MySQL 即库名） |
| `/database/schemas` | `catalog` | `List<String>` 模式列表（MySQL 通常为空） |
| `/database/tables` | `catalog`、`schema`、`tableName`（模糊） | `List<{name,type,remarks}>`，最多 500 条 |
| `/database/columns` | `catalog`、`schema`、`tableName`（必填） | `{columns:[{name,typeName,size,decimalDigits,nullable,defaultValue,remarks,primaryKey}], primaryKeys:[], indexes:[]}` |
| `/database/execute` | `catalog`、`schema`、`sql` | 查询：`{type:'query',columns:[],rows:[[字符串]],truncated:bool}`；否则：`{type:'update',count:int}` |

限制：单次最多 **500 行**、超时 **30 秒**（[DatabaseController.java:36,:41](../spider-flow-web/src/main/java/org/spiderflow/controller/DatabaseController.java#L36)）。所有值以字符串返回，避免前端精度问题。

> `/database/execute` 是**任意 SQL 通道**，不只读：`insert/update/delete`/DDL 同样执行。生产环境应通过反代禁止外部访问该路径。

---

## 5. 系统设置与状态 `/system`（本分支新增）

| 路径 | 参数 | 返回 |
| --- | --- | --- |
| `/system/params` | — | `List<{key,label,description,value,defaultValue,customized,options}>` |
| `/system/params/save` | **JSON body** `{"spider.thread.max":"32", ...}`，留空字符串=恢复默认 | `JsonBean<Void>`，`message="保存成功，参数已立即生效"`；校验失败 `code=0` + 原因 |
| `/system/status` | — | `{jvm:{...}, memory:{...}, spider:{...}}`（字段见 [SystemStatusService.java:40-104](../spider-flow-core/src/main/java/org/spiderflow/core/service/SystemStatusService.java#L40)） |

只有白名单内的 key 会被保存，未知 key 静默忽略（[SystemParamService.java:163-165](../spider-flow-core/src/main/java/org/spiderflow/core/service/SystemParamService.java#L163)）。

---

## 6. 工作台 `/dashboard`（本分支新增）

| 路径 | 参数 | 返回 `data` |
| --- | --- | --- |
| `/dashboard/summary` | `days`（默认 14，越界回落 14） | `{task, trend[], flowRank[], recentTasks[], system}` |
| `/dashboard/database` | — | `{main:{version,database,host,port,charset,collation,uptimeSeconds,tableCount,sizeBytes}, tables[], datasources[]}` |

`datasources[]` 每项：`id/name/driver/type/createDate/target/connected/costMs`。`target` 是从 jdbcUrl 裁出的 `host:port/db`，**不含口令**（[DashboardController.java:336-348](../spider-flow-web/src/main/java/org/spiderflow/controller/DashboardController.java#L336)）。

---

## 7. 对外 REST `/rest`

| 路径 | 参数 | 返回 |
| --- | --- | --- |
| `/rest/runAsync/{id}` | 路径参数：流程 id | `JsonBean<Integer>` → 任务 id |
| `/rest/run/{id}` | 路径参数 + 可选 JSON body（作为流程变量传入） | `JsonBean<List<SpiderOutput>>` → 同步执行并返回输出 |
| `/rest/stop/{taskId}` | 任务 id | `JsonBean<Void>` |
| `/rest/status/{taskId}` | 任务 id | `JsonBean<Integer>`（运行状态码） |

外部集成示例：

```bash
# 异步触发
curl -s -X POST http://127.0.0.1:8088/rest/runAsync/<flowId>
# 带变量同步执行
curl -s -X POST http://127.0.0.1:8088/rest/run/<flowId> \
  -H 'Content-Type: application/json' -d '{"keyword":"行业排行"}'
```

---

## 8. 其它

| 路径 | 用途 |
| --- | --- |
| `/task/list`、`/task/stop`、`/task/remove` | 任务实例列表 / 停止 / 删除 |
| `/variable/*` | 全局变量 CRUD |
| `/function/list|save|get|remove` | 自定义函数 |
| `/flowNotice/save|find|getNoticeWay` | 流程邮件通知配置 |
| `/ws`（WebSocket） | 编辑器测试窗与在线日志实时推送，`@ServerEndpoint("/ws")`（[WebSocketEditorServer.java:21](../spider-flow-web/src/main/java/org/spiderflow/websocket/WebSocketEditorServer.java#L21)）；反向代理需转发 Upgrade 头 |

---

## 9. 前端调用层对照

Vue 侧的请求封装在 [frontend-vue/src/api](../frontend-vue/src/api)，全部走 form-urlencoded，并在拦截层判断 `data.code === 1`。新增的数据库元数据接口封装在 [datasources.js:33-51](../frontend-vue/src/api/datasources.js#L33)：`catalogRequest` / `schemaRequest` / `tableRequest` / `columnRequest` / `executeSqlRequest`。
