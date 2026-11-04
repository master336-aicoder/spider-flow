# 变更记录（本分支 vs 上游 spider-flow 0.5.0）

本文件按主题列出本仓库相对上游 `v0.5.0`（提交 `ffab712`）的全部改动，每条都给出 `文件:行号` 依据。行号取自当前工作区，后续代码若增删行会失效，核对时以 `git diff ffab712` 为准。

配套阅读：差异概览见 [README.md](../README.md#与上游-spider-flow-050-的差异)，运行细节见 [deployment.md](deployment.md)、[operations.md](operations.md)、[user-guide.md](user-guide.md)、[api.md](api.md)、[security.md](security.md)、[architecture.md](architecture.md)。

---

## 1. 运行环境与依赖（JDK 21 适配）

三项改造是刻意设计，**不要回退**，否则在 JDK 21 上启动即失败。

| 改动 | 依据 | 原因 |
| --- | --- | --- |
| MySQL 驱动 `mysql:mysql-connector-java` → `com.mysql:mysql-connector-j:9.1.0` | [pom.xml:62-64](../pom.xml) | 上游坐标由 Spring Boot 2.0.7 的 BOM 锁定为 5.1.x，不含 MySQL 8 的 `caching_sha2_password` 完整支持 |
| 驱动类名 `com.mysql.jdbc.Driver` → `com.mysql.cj.jdbc.Driver`，连接串补 `allowPublicKeyRetrieval` 与 `serverTimezone` | [application.properties:29-32](../spider-flow-web/src/main/resources/application.properties) | Connector/J 8+ 的驱动类与 MySQL 8 时区要求 |
| 新增独立 Nashorn `org.openjdk.nashorn:nashorn-core:15.6` | [spider-flow-core/pom.xml:20-24](../spider-flow-core/pom.xml) | JDK 15 起内置 Nashorn 被移除（JEP 372） |
| 引擎类包名 `jdk.nashorn.api.scripting.ScriptObjectMirror` → `org.openjdk.nashorn.api.scripting.ScriptObjectMirror` | [ScriptManager.java:3](../spider-flow-core/src/main/java/org/spiderflow/core/script/ScriptManager.java) | 独立版 Nashorn 换了包名 |
| 启动参数固定带 `-Dfile.encoding=UTF-8 --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED` | [Dockerfile:15-19](../Dockerfile) | JEP 403 后 JDK 内部 API 默认强封装，反射访问会抛 `InaccessibleObjectException` |

## 2. 运行参数：从启动固定改为可配置 + 立即生效

上游用 `@Value` 把 `spider.*` 参数注入到字段里，进程启动后无法改。本分支引入 `SystemParamService`，读取顺序为 **`sp_sys_config` 表覆盖值 → application.properties → 代码兜底默认值**，并把所有 `@Value("${spider.*}")` 替换为该服务的调用。

| 位置 | 改动 | 依据 |
| --- | --- | --- |
| 新增服务 | 8 个参数的定义表（键/中文名/说明/兜底值/可选值） | [SystemParamService.java:63-72](../spider-flow-core/src/main/java/org/spiderflow/core/service/SystemParamService.java) |
| | 取值、列举与保存（保存值为空即删除覆盖，回落 properties） | [SystemParamService.java:108-139](../spider-flow-core/src/main/java/org/spiderflow/core/service/SystemParamService.java)、[:160-191](../spider-flow-core/src/main/java/org/spiderflow/core/service/SystemParamService.java) |
| | 校验与保存后的联动（线程池 resize、重建调度） | [SystemParamService.java:202](../spider-flow-core/src/main/java/org/spiderflow/core/service/SystemParamService.java)、[:222](../spider-flow-core/src/main/java/org/spiderflow/core/service/SystemParamService.java) |
| 新增模型/Mapper | `sp_sys_config` 实体与 Mapper | [SysConfig.java](../spider-flow-core/src/main/java/org/spiderflow/core/model/SysConfig.java)、[SysConfigMapper.java](../spider-flow-core/src/main/java/org/spiderflow/core/mapper/SysConfigMapper.java) |
| 建表 SQL | 追加 `sp_sys_config`（`config_key` 唯一索引） | [db/spiderflow.sql:79-87](../db/spiderflow.sql) |
| Spider | 线程池上限、默认并发、死循环阈值改为动态读取 | [Spider.java:49](../spider-flow-core/src/main/java/org/spiderflow/core/Spider.java)、[:62](../spider-flow-core/src/main/java/org/spiderflow/core/Spider.java)、[:92](../spider-flow-core/src/main/java/org/spiderflow/core/Spider.java)、[:106](../spider-flow-core/src/main/java/org/spiderflow/core/Spider.java)、[:275](../spider-flow-core/src/main/java/org/spiderflow/core/Spider.java) |
| RequestExecutor | 工作目录、布隆过滤器容量与误判率改为动态读取 | [RequestExecutor.java:96](../spider-flow-core/src/main/java/org/spiderflow/core/executor/shape/RequestExecutor.java)、[:297](../spider-flow-core/src/main/java/org/spiderflow/core/executor/shape/RequestExecutor.java)、[:444](../spider-flow-core/src/main/java/org/spiderflow/core/executor/shape/RequestExecutor.java)、[:453](../spider-flow-core/src/main/java/org/spiderflow/core/executor/shape/RequestExecutor.java)、[:464](../spider-flow-core/src/main/java/org/spiderflow/core/executor/shape/RequestExecutor.java) |
| SpiderJob | `spider.job.enable` 每次触发时判定，而非启动时固定 | [SpiderJob.java:45](../spider-flow-core/src/main/java/org/spiderflow/core/job/SpiderJob.java)、[:51](../spider-flow-core/src/main/java/org/spiderflow/core/job/SpiderJob.java)、[:77](../spider-flow-core/src/main/java/org/spiderflow/core/job/SpiderJob.java) |
| FlowNoticeService | 邮件主题与三封正文改为动态读取 | [FlowNoticeService.java:38](../spider-flow-core/src/main/java/org/spiderflow/core/service/FlowNoticeService.java)、[:61-77](../spider-flow-core/src/main/java/org/spiderflow/core/service/FlowNoticeService.java) |
| SpiderFlowService | 历史 XML 读写目录动态化；抽出 `refreshSchedules()`，保存参数后重建 Quartz 调度 | [SpiderFlowService.java:49](../spider-flow-core/src/main/java/org/spiderflow/core/service/SpiderFlowService.java)、[:60](../spider-flow-core/src/main/java/org/spiderflow/core/service/SpiderFlowService.java)、[:66-71](../spider-flow-core/src/main/java/org/spiderflow/core/service/SpiderFlowService.java)、[:137](../spider-flow-core/src/main/java/org/spiderflow/core/service/SpiderFlowService.java)、[:210](../spider-flow-core/src/main/java/org/spiderflow/core/service/SpiderFlowService.java)、[:221](../spider-flow-core/src/main/java/org/spiderflow/core/service/SpiderFlowService.java) |
| SpiderFlowController / SpiderRestController | 日志下载与在线读取、输出目录改为动态工作空间 | [SpiderFlowController.java:65-66](../spider-flow-web/src/main/java/org/spiderflow/controller/SpiderFlowController.java)、[:173](../spider-flow-web/src/main/java/org/spiderflow/controller/SpiderFlowController.java)、[:186](../spider-flow-web/src/main/java/org/spiderflow/controller/SpiderFlowController.java)；[SpiderRestController.java:38-39](../spider-flow-web/src/main/java/org/spiderflow/controller/SpiderRestController.java)、[:110](../spider-flow-web/src/main/java/org/spiderflow/controller/SpiderRestController.java) |
| 线程池 | 单例引用 + 水位指标 + `resize()`（扩容先 max 后 core，缩容先 core 后 max） | [SpiderFlowThreadPoolExecutor.java:38](../spider-flow-api/src/main/java/org/spiderflow/concurrent/SpiderFlowThreadPoolExecutor.java)、[:48-92](../spider-flow-api/src/main/java/org/spiderflow/concurrent/SpiderFlowThreadPoolExecutor.java) |
| properties | `spider.job.enable` 默认从 `true` 改为 `false`（本仓库默认不跑定时任务） | [application.properties:10](../spider-flow-web/src/main/resources/application.properties) |
| properties | 通知文案改写为 `\uXXXX` 转义（`Properties` 按 ISO-8859-1 读取，直接写中文会乱码） | [application.properties:58-62](../spider-flow-web/src/main/resources/application.properties) |

## 3. 新增节点：autoSQL（自动建表 + 批量入库）

| 内容 | 依据 |
| --- | --- |
| 执行器与节点属性常量（数据源/数据库/schema/表名/批次/自动建表） | [AutoSQLExecutor.java:47-60](../spider-flow-core/src/main/java/org/spiderflow/core/executor/shape/AutoSQLExecutor.java) |
| 标识符白名单：库/模式/表/列名只允许字母、数字、下划线、中文，非法即跳过 | [AutoSQLExecutor.java:64-68](../spider-flow-core/src/main/java/org/spiderflow/core/executor/shape/AutoSQLExecutor.java)、[:141-165](../spider-flow-core/src/main/java/org/spiderflow/core/executor/shape/AutoSQLExecutor.java) |
| 主流程：读上游输出 → 归一列 → 切目录 → 建表 → 批量插入 → 还原目录 | [AutoSQLExecutor.java:90-136](../spider-flow-core/src/main/java/org/spiderflow/core/executor/shape/AutoSQLExecutor.java) |
| 反向遍历找最近的 output 节点 | [AutoSQLExecutor.java:374-391](../spider-flow-core/src/main/java/org/spiderflow/core/executor/shape/AutoSQLExecutor.java)，依赖新增的 `SpiderNode.getPrevNodes()` [SpiderNode.java:152](../spider-flow-api/src/main/java/org/spiderflow/model/SpiderNode.java) |
| 列类型按样本推断，混合类型退化字符串；字符串列 `varchar(255~5000)`，超长退化大文本 | [AutoSQLExecutor.java:417-483](../spider-flow-core/src/main/java/org/spiderflow/core/executor/shape/AutoSQLExecutor.java)、[:549-554](../spider-flow-core/src/main/java/org/spiderflow/core/executor/shape/AutoSQLExecutor.java)、[:683](../spider-flow-core/src/main/java/org/spiderflow/core/executor/shape/AutoSQLExecutor.java) |
| 值统一转文本/时间戳 | [AutoSQLExecutor.java:485-505](../spider-flow-core/src/main/java/org/spiderflow/core/executor/shape/AutoSQLExecutor.java)、[:590](../spider-flow-core/src/main/java/org/spiderflow/core/executor/shape/AutoSQLExecutor.java) |
| 多方言：标识符引用、限定名、建表 DDL、MySQL 与其他库的 catalog/schema 差异 | [AutoSQLExecutor.java:610-683](../spider-flow-core/src/main/java/org/spiderflow/core/executor/shape/AutoSQLExecutor.java) |
| 节点注册键（注意是 `supportShape()`，`shape()` 走默认实现返回 null，因此不出现在 `/spider/shapes`，由前端静态组件栏提供） | [AutoSQLExecutor.java:220-223](../spider-flow-core/src/main/java/org/spiderflow/core/executor/shape/AutoSQLExecutor.java) |
| 批量插入全占位符、按批次 `executeBatch` | [AutoSQLExecutor.java:228-270](../spider-flow-core/src/main/java/org/spiderflow/core/executor/shape/AutoSQLExecutor.java) |
| 组件栏条目与说明文案 | [frontend-vue/src/libs/spidereditor/editor.js:50](../frontend-vue/src/libs/spidereditor/editor.js) |
| 节点配置表单 | [frontend-vue/src/views/spider/templates/autoSQL.vue](../frontend-vue/src/views/spider/templates/autoSQL.vue) |

## 4. 新增后端接口

| 接口 | 依据 | 说明 |
| --- | --- | --- |
| `GET /system/params`、`POST /system/params/save`、`GET /system/status` | [SystemController.java:31](../spider-flow-web/src/main/java/org/spiderflow/controller/SystemController.java)、[:36](../spider-flow-web/src/main/java/org/spiderflow/controller/SystemController.java)、[:49](../spider-flow-web/src/main/java/org/spiderflow/controller/SystemController.java) | 系统设置页与状态；保存值为空即删除覆盖，回落 properties |
| `GET /dashboard/summary`、`GET /dashboard/database` | [DashboardController.java:72](../spider-flow-web/src/main/java/org/spiderflow/controller/DashboardController.java)、[:84](../spider-flow-web/src/main/java/org/spiderflow/controller/DashboardController.java) | 工作台聚合。数据源连通性探测 3 秒超时 [DashboardController.java:48](../spider-flow-web/src/main/java/org/spiderflow/controller/DashboardController.java)、[:302](../spider-flow-web/src/main/java/org/spiderflow/controller/DashboardController.java)。**返回不含口令** |
| `GET /database/catalogs`、`/schemas`、`/tables`、`/columns`、`POST /database/execute` | [DatabaseController.java:45](../spider-flow-web/src/main/java/org/spiderflow/controller/DatabaseController.java)、[:50](../spider-flow-web/src/main/java/org/spiderflow/controller/DatabaseController.java)、[:55](../spider-flow-web/src/main/java/org/spiderflow/controller/DatabaseController.java)、[:72](../spider-flow-web/src/main/java/org/spiderflow/controller/DatabaseController.java)、[:111](../spider-flow-web/src/main/java/org/spiderflow/controller/DatabaseController.java) | 数据源详情的元数据浏览与 SQL 执行。单次最多 500 行、30 秒超时 [DatabaseController.java:36-41](../spider-flow-web/src/main/java/org/spiderflow/controller/DatabaseController.java)，索引信息 [DatabaseController.java:255](../spider-flow-web/src/main/java/org/spiderflow/controller/DatabaseController.java) |
| 状态采集服务 | [SystemStatusService.java](../spider-flow-core/src/main/java/org/spiderflow/core/service/SystemStatusService.java) | JVM/内存/线程池/磁盘指标 |
| `POST /datasource/delete` 别名 | [DataSourceController.java:54](../spider-flow-web/src/main/java/org/spiderflow/controller/DataSourceController.java) | Vue 侧沿用 `/delete`，后端补上别名 |

> `/database/execute` 是**任意 SQL 通道**，且整个服务无鉴权。上生产前必须按 [security.md §4](security.md) 收口。

## 5. 前端：layui 老界面 → Vue 单页应用

`frontend-vue/` 为新增源码目录（94 个源文件，不含依赖与产物），构建产物打进 jar 的 `static` 根目录，成为唯一入口。

| 关注点 | 依据 |
| --- | --- |
| 路由（工作台/爬虫/变量/函数/数据源/系统设置/脚本） | [routes-spiderflow.js:10-199](../frontend-vue/src/router/routes-spiderflow.js)，脚本管理为隐藏路由 [:186-199](../frontend-vue/src/router/routes-spiderflow.js) |
| 接口层：axios 封装、form-urlencoded 提交、成功回调（**未**集中校验 `code`，由各页面判定） | [util/request.js:68-111](../frontend-vue/src/util/request.js)、[util/util.js:9](../frontend-vue/src/util/util.js)、配置 [config/index.js:25-42](../frontend-vue/src/config/index.js) |
| 悬浮提示文案集中维护 | [config/helpTexts.js](../frontend-vue/src/config/helpTexts.js)、[components/help-tip.vue](../frontend-vue/src/components/help-tip.vue) |
| 画布：节点图标单列竖排（22×22 图标 + 3px 内边距 + 1px 间距 = 28px 槽位，容器自身滚动） | [detail.less:52-74](../frontend-vue/src/views/spider/detail.less)、侧栏宽度 [detail.vue:16](../frontend-vue/src/views/spider/detail.vue) |
| 拖拽放置与 32×32 拖拽预览 | [spider-editor.js:318-344](../frontend-vue/src/libs/spidereditor/spider-editor.js)、点击放置 [spider-editor.js:353](../frontend-vue/src/libs/spidereditor/spider-editor.js) |
| 自绘右键菜单（节点/连线/画布三种上下文；开始节点禁剪切与删除） | [spider-editor.js:181-296](../frontend-vue/src/libs/spidereditor/spider-editor.js) |
| 测试与调试窗（WebSocket 实时日志） | [views/spider/testModal.vue:132](../frontend-vue/src/views/spider/testModal.vue) |
| 数据源详情抽屉：库/schema/表结构/索引 + SQL 执行 | [views/datasources/database.vue](../frontend-vue/src/views/datasources/database.vue)、接口封装 [api/datasources.js:33-52](../frontend-vue/src/api/datasources.js) |
| 缓存策略：`index.html` 强制回源校验，避免前端更新后浏览器仍用旧页 | [application.properties:26-27](../spider-flow-web/src/main/resources/application.properties) |
| 静态资源放行配置删除（Vue 产物落 `static` 根目录，无需 `/static/**` 映射），文件仅剩 CORS 配置 | [ResourcesConfiguration.java:12-21](../spider-flow-web/src/main/java/org/spiderflow/configuration/ResourcesConfiguration.java) |

## 6. 构建与打包

| 改动 | 依据 |
| --- | --- |
| 属性：前端目录、npm 可执行名、跳过开关 | [spider-flow-web/pom.xml:14-17](../spider-flow-web/pom.xml) |
| `generate-resources` 阶段执行 `npm run build`，注入 `NODE_OPTIONS=--openssl-legacy-provider`（webpack4 用 md4，Node 17+ 的 OpenSSL 3 需 legacy provider）；`-Dfrontend.skip=true` 可跳过 | [spider-flow-web/pom.xml:36-62](../spider-flow-web/pom.xml) |
| `process-resources` 阶段把 `frontend-vue/dist` 拷进 `target/classes/static` | [spider-flow-web/pom.xml:63-84](../spider-flow-web/pom.xml) |
| `windows-npm` profile：本机 npm 为 `npm.exe`，Windows 自动切换 | [spider-flow-web/pom.xml:87-100](../spider-flow-web/pom.xml) |

## 7. 容器与部署

| 改动 | 依据 |
| --- | --- |
| 基础镜像 `java:8` → `eclipse-temurin:21-jre`；`MAINTAINER` → OCI 标签 | [Dockerfile:1-4](../Dockerfile) |
| `CMD sleep 30; java -jar` → `ENTRYPOINT` 数组形式，带 `-Dfile.encoding` 与两条 `--add-opens` | [Dockerfile:15-19](../Dockerfile) |
| 镜像内固定 `/data/spider` 目录，运行时用 `--spider.workspace=/data/spider` 覆盖 properties 里的 Windows 路径 | [Dockerfile:6](../Dockerfile)、[:21-24](../Dockerfile) |

## 8. 删除的文件

`spider-flow-web/src/main/resources/static/` 下 161 个老前端文件整体删除（layui、easyui、codemirror、cron 插件、jsontree、mxgraph 副本、face 图标、全部 `*.html` 与 `resources/templates/*.html`），约 14.5 万行。mxGraph 改为由 `frontend-vue/public/js/mxgraph/mxgraph.js` 提供并随前端产物一起打包。

保留说明：`db/spiderflow.sql` 未删除任何既有表，只追加 `sp_sys_config`。

## 9. 新增文档

| 文件 | 内容 |
| --- | --- |
| [README.md](../README.md) | 项目定位、与上游差异表、快速开始、文档索引、技术口径核对与勘误 |
| [docs/deployment.md](deployment.md) | 环境要求、数据库初始化、必改配置与 8 项运行参数、构建、启动（前台/systemd/Windows/Docker/Nginx）、启动校验、数据目录、升级回滚、部署故障表 |
| [docs/operations.md](operations.md) | 巡检清单、日志、磁盘清理、备份恢复、任务运维、数据源与 SQL 限制、故障排查、值班速查 |
| [docs/user-guide.md](user-guide.md) | 菜单、爬虫列表、画布编辑器、全部节点字段说明、数据源详情、系统设置、常见问题 |
| [docs/api.md](api.md) | 通用约定 + 各控制器端点表 + 限制值 |
| [docs/security.md](security.md) | 暴露面总览、加固清单、依赖漏洞、自查命令 |
| [docs/architecture.md](architecture.md) | 模块与执行流、节点注册机制、加新节点的 5 步、前端结构、元数据通道的坑 |
| [docs/changelog.md](changelog.md) | 本文件 |

## 10. 不入库的本地产物

| 目录 | 体积 | 处理 |
| --- | --- | --- |
| `frontend-vue/node_modules` | ≈365 MB | `.gitignore` 排除，`npm install` 复原 |
| `frontend-vue/dist` | ≈15 MB | `.gitignore` 排除，构建产物 |
| `probe/` | ≈7.6 MB | `.gitignore` 排除。**内含带口令的 JDBC 连接串与响应样本，禁止提交** |
| `vendor/` | ≈8.5 MB | `.gitignore` 排除。是 `spider-flow-vue`/`magic-api` 的参考克隆，含各自 `.git`，**未被构建引用**（jar 内无 magic-api 依赖） |
| `target/`、`*.log`、工作空间文件 | — | `.gitignore` 排除 |

## 11. 回滚注意点

- 回退第 1 节的任意一项都会让 JDK 21 启动失败；如需回到 JDK 8，应整仓回退到上游 `ffab712`。
- 回退第 2 节后，`sp_sys_config` 里的覆盖值不再被读取，但不影响启动；表可以直接留着。
- 恢复老 layui 界面只需 `git checkout ffab712 -- spider-flow-web/src/main/resources/static`，同时删掉前端构建插件，否则 `dist` 会覆盖 `static` 根目录。
- `spider.job.enable` 在本分支默认 `false`（[application.properties:10](../spider-flow-web/src/main/resources/application.properties)），从上游配置迁移时容易误以为定时任务失效，实为默认关闭。
