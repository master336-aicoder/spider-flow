# 架构与模块说明

给要改代码的人。读完这篇应该能回答：一个流程从点「运行」到落库经过了哪些类，以及加一种新节点要动几个文件。

| 层 | 位置 | 职责 |
| --- | --- | --- |
| 接口/模型 | [spider-flow-api](../spider-flow-api) | `SpiderNode`、`JsonBean`、`ShapeExecutor` 接口、上下文与线程池 |
| 核心 | [spider-flow-core](../spider-flow-core) | 流程引擎 `Spider`、执行器、Quartz 任务、脚本、服务与 Mapper |
| Web | [spider-flow-web](../spider-flow-web) | 控制器、配置、WebSocket、logback、前端产物打包 |

依赖方向：`spider-flow-web → spider-flow-core → spider-flow-api`（[pom.xml:170-174](../pom.xml#L170)）。

---

## 1. 启动

[SpiderApplication.java](../spider-flow-web/src/main/java/org/spiderflow/SpiderApplication.java) 是唯一的 `main`。启动期与运行参数有关的三件事：

1. `SystemParamService.init()`：建 `sp_sys_config` 表 → 把覆盖值读进内存 `overrides` → 立刻按覆盖值 resize 线程池（[SystemParamService.java:86-106](../spider-flow-core/src/main/java/org/spiderflow/core/service/SystemParamService.java#L86)）。
2. `SpiderFlowService.initJobs()`：读 `sp_flow` 里启用的流程注册 Quartz Job；本分支把它抽成可重复调用的 `refreshSchedules()`，先 `remove` 再按开关决定 `addJob`（[SpiderFlowService.java:66-78](../spider-flow-core/src/main/java/org/spiderflow/core/service/SpiderFlowService.java#L66)）。
3. `SpiderFlowThreadPoolExecutor` 构造时把自己记成静态 `instance`，让运行期能改上限（[SpiderFlowThreadPoolExecutor.java:38,48](../spider-flow-api/src/main/java/org/spiderflow/concurrent/SpiderFlowThreadPoolExecutor.java#L38)）。

参数读取统一走 `SystemParamService.getString/getInteger/...`：**DB 覆盖值 → properties → 代码兜底**。原先散落在各处的 `@Value` 字段已全部删除，改为运行期取值（[Spider.java:48-49](../spider-flow-core/src/main/java/org/spiderflow/core/Spider.java#L48)、[RequestExecutor.java:95-96](../spider-flow-core/src/main/java/org/spiderflow/core/executor/shape/RequestExecutor.java#L95)、[SpiderJob.java:44-45](../spider-flow-core/src/main/java/org/spiderflow/core/job/SpiderJob.java#L44)、[FlowNoticeService.java:37-38](../spider-flow-core/src/main/java/org/spiderflow/core/service/FlowNoticeService.java#L37)）。

---

## 2. 流程模型：mxGraph XML + JsonProperty

流程存在 `sp_flow.xml` 字段里，就是一段 mxGraph XML；每个节点的业务配置挂在 `<JsonProperty as="data">` 下的 JSON：

```xml
<mxCell id="5" value="抓取首页" style="request" parent="1" vertex="1">
  <mxGeometry x="180" y="80" width="24" height="24" as="geometry"/>
  <JsonProperty as="data">
    {"url":"...","method":"GET","shape":"request"}
  </JsonProperty>
</mxCell>
```

- `style`/`data.shape` 决定用哪个执行器。
- 后端解析成 `SpiderNode`（[SpiderNode.java](../spider-flow-api/src/main/java/org/spiderflow/model/SpiderNode.java)，本分支新增 `getPrevNodes()` 供 autoSQL 回溯上游输出）。
- 前端序列化/反序列化在 [spider-editor.js `registerJsonCodec`](../frontend-vue/src/libs/spidereditor/spider-editor.js)。

---

## 3. 执行：Spider + 线程池 + 上下文

```
SpiderJob / /spider/run / /rest/run
      └─ Spider.execute()
           ├─ 根节点属性：线程数(默认 spider.thread.default)、提交策略
           ├─ executorInstance.createSubThreadPoolExecutor(...)   # 每个任务一个子池
           ├─ SpiderContext 承载变量、resp、线程池、任务 id
           └─ 逐节点：ExecutorsUtils.get(shape) → ShapeExecutor.execute(node, context, vars)
```

- 平台级线程池 `SpiderFlowThreadPoolExecutor`，上限来自 `spider.thread.max`（[Spider.java:54-62](../spider-flow-core/src/main/java/org/spiderflow/core/Spider.java#L54)）。
- 并发度 = 流程线程数 + 1（[Spider.java:120](../spider-flow-core/src/main/java/org/spiderflow/core/Spider.java#L120)）。
- 连线条件、循环、死循环检测都在 `Spider` 内部（表达式经 `ExpressionUtils.execute`，[Spider.java:221,:321](../spider-flow-core/src/main/java/org/spiderflow/core/Spider.java#L221)）。
- 任务日志靠线程上下文路由：`SpiderContextHolder.get()` 拿到 `SpiderJobContext` 时，logback 的行写进 `<taskId>.log`（[SpiderFlowFileAppender.java:17-23](../spider-flow-web/src/main/java/org/spiderflow/logback/SpiderFlowFileAppender.java#L17)）。

### 执行器注册

`ExecutorsUtils` 把所有 `ShapeExecutor` Bean 按 `supportShape()` 收成 Map（[ExecutorsUtils.java:34-41](../spider-flow-core/src/main/java/org/spiderflow/core/utils/ExecutorsUtils.java#L34)），`/spider/shapes` 则由 `shape()` 汇总（[:48-50](../spider-flow-core/src/main/java/org/spiderflow/core/utils/ExecutorsUtils.java#L48)）。插件只要注册一个 `ShapeExecutor` Bean 就能出现在画布上，这是上游的扩展点，本分支沿用。

内置执行器：`Start` / `Request` / `Variable` / `Loop` / `ForkJoin` / `Comment` / `Output` / `ExecuteSql` / `Script` / `Function` / `Process` / **`AutoSQL`（本分支新增，`supportShape()="autoSQL"`，[AutoSQLExecutor.java:220-223](../spider-flow-core/src/main/java/org/spiderflow/core/executor/shape/AutoSQLExecutor.java#L220)）**。

---

## 4. autoSQL 的数据流

```
上游 output 节点的输出（Map / List<Map> / 标量）
   → 归一化成 rows: List<Map<String,Object>>
   → 列集合与类型推断（混合类型退化为字符串；字符串上限 VARCHAR(5000)）
   → Dialect.qualify(catalog, schema, tableName) 拼限定表名
   → 表不存在且勾选自动建表 → CREATE TABLE（标识符全部经 IDENTIFIER 白名单）
   → 批量 insert，值一律走 PreparedStatement 占位符（AutoSQLExecutor.java:228-239）
   → finally 还原连接的 catalog/schema（:118-130）
```

设计上刻意保守：任何不合法的动态标识符**跳过节点并 warn**，而不是尝试转义后执行（[:141](../spider-flow-core/src/main/java/org/spiderflow/core/executor/shape/AutoSQLExecutor.java#L141)）。方言能力（是否支持 catalog 出现在 DDL、是否分 schema）集中在内部类 `Dialect`（[:627 附近](../spider-flow-core/src/main/java/org/spiderflow/core/executor/shape/AutoSQLExecutor.java#L627)）。

---

## 5. 数据库元数据通道

[DatabaseController.java](../spider-flow-web/src/main/java/org/spiderflow/controller/DatabaseController.java) 直接走 `DataSourceUtils.getDataSource(id)` 拿 Druid 池的连接，用 `DatabaseMetaData` 读 catalog/schema/表/列/索引，`/execute` 用 `Statement` 执行单条 SQL。

统一入口 `withConnection(...)` 负责：取连接 → 记录原 catalog/schema → 切换 → 执行 → **finally 还原**（[:165-190](../spider-flow-core/../spider-flow-web/src/main/java/org/spiderflow/controller/DatabaseController.java#L165)）。这里有两个真实踩过的坑，改动时不要退回：

1. Druid 1.1.16 的 `getSchema()` 抛 `SQLFeatureNotSupportedException`，必须包一层 try（`readSchema`，[:227 附近](../spider-flow-web/src/main/java/org/spiderflow/controller/DatabaseController.java#L227)）。
2. `DatabaseMetaData` 的 catalog/schema 传 `null` 表示"不限定"，会把所有库的同名表混在一起；所以未指定时要用**会话当前**的 catalog/schema 过滤（[:174-184](../spider-flow-web/src/main/java/org/spiderflow/controller/DatabaseController.java#L174)）。

前端封装在 [database.vue](../frontend-vue/src/views/datasources/database.vue)（抽屉）+ [datasources.js:33-51](../frontend-vue/src/api/datasources.js#L33)。

---

## 6. 脚本与自定义函数

- `ScriptManager` 用 Nashorn；JDK 15+ 移除了内置引擎，本分支依赖独立 `nashorn-core:15.6`，导入包名改为 `org.openjdk.nashorn.api.scripting.*`（[ScriptManager.java:3](../spider-flow-core/src/main/java/org/spiderflow/core/script/ScriptManager.java#L3)）。
- 自定义函数存在 `sp_function`，在流程中以 `${函数名(args)}` 调用；编辑器里是 Monaco + `/spider/grammers` 提供的补全词法。

---

## 7. 前端结构

```
frontend-vue/src/
├── api/            # 每个后端模块一个文件，统一 form-urlencoded + code===1 判定
├── components/     # main 布局、code-editor(Monaco)、help-tip 悬浮提示等
├── config/         # helpTexts.js：所有字段/按钮的提示文案，改文案只改这里
├── libs/spidereditor/
│   ├── editor.js   # 画布 11 种组件的定义（title/desc/image/hidden/defaultAdd）
│   └── spider-editor.js  # mxGraph 装配：样式、拖放、右键菜单、JSON 编解码、快捷键
├── router/         # routes-spiderflow.js：菜单与路由
├── views/
│   ├── dashboard/  spider/{detail,list,templates,components}
│   ├── datasources/{list.vue, database.vue}
│   ├── system/settings.vue   variables/  functions/
└── public/js/mxgraph/       # 第三方 mxGraph（未打包，直接 script 引入）
```

要点：

- 路由是 **hash 模式**，站点根路径 `/` 即 Vue 应用；构建产物由 Maven 复制进 jar 的 `static/`（[deployment.md §4](deployment.md)）。
- 画布节点数据用自定义 `JsonProperty` + `mxObjectCodec` 编解码，字段名与后端 `ShapeExecutor` 常量一一对应。
- 组件栏拖放走 `mxUtils.makeDraggable`（[spider-editor.js:345](../frontend-vue/src/libs/spidereditor/spider-editor.js#L345)）→ `mxDragSource`；注意 mxGraph 在本构建里走 **pointer 事件**，`graphContainsEvent` 是从 `evt.target` 向上找容器（[mxgraph.js:13402](../frontend-vue/public/js/mxgraph/mxgraph.js#L13402)）——自动化测试要派发真实元素上的 pointermove 才能命中画布。
- 组件图标 28×28、单列、侧栏 56px（[detail.less:52-74](../frontend-vue/src/views/spider/detail.less#L52)），但**画布节点仍是 32×32**（[spider-editor.js:333](../frontend-vue/src/libs/spidereditor/spider-editor.js#L333)），两者是独立尺寸。

---

## 8. 加一种新节点要动什么

| 步骤 | 文件 | 说明 |
| --- | --- | --- |
| 1 | `spider-flow-core/.../executor/shape/XxxExecutor.java` | `@Component implements ShapeExecutor`，`supportShape()` 返回形状名，`execute()` 干活，`shape()` 提供画布元数据 |
| 2 | [editor.js](../frontend-vue/src/libs/spidereditor/editor.js) | 增加一项 `{name, title, desc, image}`，名字与 `supportShape()` 一致 |
| 3 | `views/spider/templates/xxx.vue` | 属性面板；字段读写用 `getCellValue/setCellValue`，键名与后端常量一致 |
| 4 | [helpTexts.js](../frontend-vue/src/config/helpTexts.js) | 补字段悬浮提示 |
| 5 | 重新构建 | 见 [deployment.md §4](deployment.md) |

参考实现就是 autoSQL：[AutoSQLExecutor.java](../spider-flow-core/src/main/java/org/spiderflow/core/executor/shape/AutoSQLExecutor.java) + [editor.js:50-53](../frontend-vue/src/libs/spidereditor/editor.js#L50) + [autoSQL.vue](../frontend-vue/src/views/spider/templates/autoSQL.vue)。

---

## 9. 数据库表

| 表 | 关键字段 | 写入方 |
| --- | --- | --- |
| `sp_flow` | `xml`(流程定义)、`cron`、`enabled`、`execute_count`、`last/next_execute_time` | 编辑器保存、调度执行统计 |
| `sp_task` | `flow_id`、`begin_time`、`end_time` | 任务开始/结束；**没有成功失败字段** |
| `sp_datasource` | `driver_class_name`、`jdbc_url`、`username`、`password`(明文) | 数据源管理 |
| `sp_variable` | 全局变量 | 全局变量页 |
| `sp_function` | 自定义函数 | 函数页 |
| `sp_flow_notice` | 通知方式与三种触发开关 | 列表页「通知」 |
| `sp_sys_config` | `config_key`(唯一)、`config_value` | 系统设置页（本分支新增） |

脚本：[db/spiderflow.sql](../db/spiderflow.sql)。
