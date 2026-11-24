<p align="center">
    <img src="https://www.spiderflow.org/images/logo.svg" width="600">
</p>
<p align="center">
    <a target="_blank" href="https://openjdk.org/projects/jdk/21/"><img src="https://img.shields.io/badge/JDK-21%20(verified)-green.svg" /></a>
    <img src="https://img.shields.io/badge/Spring%20Boot-2.0.7-blue.svg" />
    <img src="https://img.shields.io/badge/Vue-2.x%20%2B%20ant--design--vue-brightgreen.svg" />
    <img src="https://img.shields.io/badge/MySQL-8.0-orange.svg" />
    <a target="_blank" href="LICENSE"><img src="https://img.shields.io/:license-MIT-blue.svg"></a>
</p>

[快速开始](#快速开始) | [与上游的差异](#与上游-spider-flow-050-的差异) | [文档索引](#文档索引) | [特性](#特性) | [目录结构](#目录结构) | [安全提示](#安全提示务必阅读) | [上游站点](https://www.spiderflow.org) | [免责声明](#免责声明)

## 介绍

`spider-flow` 以流程图的方式定义爬虫，是一个高度灵活可配置的爬虫平台。

**本仓库是上游 [spider-flow 0.5.0](https://gitee.com/ssssssss-team/spider-flow) 的适配分支**，在上游基础上完成了三件事：

1. **JDK 21 运行适配**——上游声明 JDK 1.8+，本分支在 JDK 21 上实际跑通并修掉运行期不兼容点。
2. **前端整体替换为 Vue 源码**——删除随 jar 打包的 layui 老前端，改为 `frontend-vue/`（Vue 2 + ant-design-vue）源码，并由 Maven 驱动构建、产物打进 jar。
3. **功能增强**——工作台、系统设置（参数即时生效）、autoSQL 自动入库节点、数据源"数据库详情"（元数据浏览 + SQL 执行）、画布右键菜单与拖拽布局、在线日志与调试窗等。

改动逐条清单见 [docs/changelog.md](docs/changelog.md)。

## 与上游 spider-flow 0.5.0 的差异

| 主题 | 上游 | 本分支 | 依据 |
| --- | --- | --- | --- |
| 运行 JDK | 声明 1.8+ | 在 JDK 21 上验证；启动需 `--add-opens` | `docs/deployment.md` 启动命令 |
| MySQL 驱动 | `mysql:mysql-connector-java` | `com.mysql:mysql-connector-j:9.1.0`，驱动类 `com.mysql.cj.jdbc.Driver` | [pom.xml:62-64](pom.xml#L62)、`application.properties:29` |
| JS 脚本引擎 | JDK 内置 Nashorn | 独立依赖 `org.openjdk.nashorn:nashorn-core:15.6`，包名改为 `org.openjdk.nashorn.*` | [spider-flow-core/pom.xml:20-24](spider-flow-core/pom.xml#L20)、`ScriptManager.java:3` |
| 前端 | jar 内 layui 静态页 | `frontend-vue/` Vue 源码，Maven 构建后由 maven-resources 复制进 jar `static/` | [spider-flow-web/pom.xml:14-84](spider-flow-web/pom.xml#L14) |
| 定时任务 | 默认开启 | `spider.job.enable=false`，可在系统设置页即时开关 | [application.properties:10](spider-flow-web/src/main/resources/application.properties#L10)、[SystemParamService.java:64](spider-flow-core/src/main/java/org/spiderflow/core/service/SystemParamService.java#L64) |
| 运行参数 | 只能改配置文件后重启 | 8 项参数在页面修改后立即生效，覆盖值存 `sp_sys_config` 表 | [SystemParamService.java:63-72](spider-flow-core/src/main/java/org/spiderflow/core/service/SystemParamService.java#L63) |
| 入库节点 | 手写 SQL 的"执行SQL" | 新增"自动入库"（autoSQL）节点，取上游输出自动建表批量插入 | [AutoSQLExecutor.java:47](spider-flow-core/src/main/java/org/spiderflow/core/executor/shape/AutoSQLExecutor.java#L47) |
| 鉴权 | 无 | **仍然没有**，本分支未引入登录 | `docs/security.md` |

## 快速开始

> 完整口径（含 Windows/Linux、Docker、Nginx、参数表、备份与故障排查）见 **[docs/deployment.md](docs/deployment.md)** 与 **[docs/operations.md](docs/operations.md)**。

### 环境要求

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 21（本分支验证版本） | 启动需 `--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED` |
| Maven | 3.6.x | 需能解析 `spider-flow-*` 三个模块 |
| Node.js | 17 及以上均可，本分支用 24.x | webpack4 使用 md4 摘要，Node 17+ 必须带 `NODE_OPTIONS=--openssl-legacy-provider`（构建插件已内置） |
| MySQL | 8.0 | 需 `utf8mb4`；驱动为 Connector/J 9.1.0 |

### 1. 初始化数据库

```bash
mysql -uroot -p -e "create database spiderflow default charset utf8mb4"
mysql -uroot -p spiderflow < db/spiderflow.sql
```

`sp_sys_config`（系统参数覆盖值表）在 `db/spiderflow.sql` 中已包含，服务启动时也会 `CREATE TABLE IF NOT EXISTS` 兜底（[SystemParamService.java:87-94](spider-flow-core/src/main/java/org/spiderflow/core/service/SystemParamService.java#L87)）。

### 2. 修改连接配置

编辑 [spider-flow-web/src/main/resources/application.properties](spider-flow-web/src/main/resources/application.properties)，至少改 `spring.datasource.url / username / password`、`spider.workspace`。

### 3. 构建（前端 + 后端一体）

```bash
mvn -DskipTests package
```

只改后端、想跳过前端构建时加 `-Dfrontend.skip=true`。产物：`spider-flow-web/target/spider-flow.jar`。

### 4. 启动

```bash
java -Dfile.encoding=UTF-8 \
     --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.util=ALL-UNNAMED \
     -jar spider-flow-web/target/spider-flow.jar
```

浏览器访问 `http://127.0.0.1:8088/`（默认无登录，直接进入工作台）。

## 特性

### 上游原有

- [x] 支持 Xpath/JsonPath/css 选择器/正则提取/混搭提取
- [x] 支持 JSON/XML/二进制格式
- [x] 支持多数据源、SQL select/selectInt/selectOne/insert/update/delete
- [x] 支持爬取 JS 动态渲染(或 ajax)的页面
- [x] 支持代理
- [x] 支持自动保存至数据库/文件
- [x] 常用字符串、日期、文件、加解密等函数
- [x] 支持插件扩展(自定义执行器，自定义方法)
- [x] 任务监控、任务日志
- [x] 支持 HTTP 接口
- [x] 支持 Cookie 自动管理
- [x] 支持自定义函数

### 本分支新增

- [x] **工作台**：任务/数据库/系统三块状态与统计视图（`/dashboard/summary`、`/dashboard/database`）
- [x] **系统设置**：8 项运行参数页面修改即时生效 + 系统状态（JVM/内存/爬虫线程池/Quartz）
- [x] **自动入库（autoSQL）节点**：取上游"输出"节点内容自动建表并批量插入，可选数据库与 schema，表名支持 `${}` 变量
- [x] **数据源·数据库详情**：按数据库/schema/表逐级浏览表结构、索引，并内置 SQL 执行面板（默认 500 行 / 30 秒上限）
- [x] **画布体验**：左侧组件单列紧凑排列、节点与连线右键菜单（复制/剪切/删除/连线等）、组件拖动放置、悬浮提示
- [x] **调试窗**：编辑器内测试运行，日志经 WebSocket(`/ws`)实时推送
- [x] **菜单可收起**、前端为唯一入口（根路径 `/` 即 Vue）

## 文档索引

| 文档 | 内容 | 适用读者 |
| --- | --- | --- |
| [docs/deployment.md](docs/deployment.md) | 环境、构建、启动、Docker/Nginx/systemd、参数与端口清单 | 部署实施 |
| [docs/operations.md](docs/operations.md) | 日常巡检、日志与文件目录、备份恢复、故障排查、升级回滚 | 运维值班 |
| [docs/user-guide.md](docs/user-guide.md) | 各页面与画布 11 种节点的字段说明、建流程到出数据的完整示例 | 使用者 |
| [docs/architecture.md](docs/architecture.md) | 模块划分、执行器注册、上下文与线程模型、前端结构 | 二次开发 |
| [docs/api.md](docs/api.md) | 页面接口、`/rest` 对外接口、`/ws` WebSocket 清单 | 联调 |
| [docs/security.md](docs/security.md) | 无鉴权风险提示、SQL 通道、口令存储、加固清单、依赖已知漏洞 | 安全评估 |
| [docs/changelog.md](docs/changelog.md) | 相对上游的逐文件改动（含行号依据） | 代码评审 |

## 目录结构

```
spider-flow/
├── pom.xml                  # 聚合工程，Spring Boot 2.0.7 作为 parent，统一版本管理
├── db/spiderflow.sql        # 初始化脚本（含 sp_sys_config）
├── frontend-vue/            # Vue 2 前端源码（Maven 构建时自动 npm run build）
│   ├── src/{api,components,config,libs,router,views}
│   └── public/js/mxgraph/   # 画布依赖的 mxGraph
├── spider-flow-api/         # 对外接口层：模型、上下文、线程池
├── spider-flow-core/        # 核心：执行器、调度、脚本、服务、Mapper
├── spider-flow-web/         # 启动模块：控制器、配置、静态资源、logback
└── Dockerfile               # 容器镜像（eclipse-temurin 21）
```

模块依赖方向为 `spider-flow-web → spider-flow-core → spider-flow-api`（[pom.xml:170-174](pom.xml#L170)）。

## 插件

上游提供的插件（本分支未内置，需自行按插件文档引入，插件坐标见 `pom.xml` 的 `dependencyManagement`）：

- [x] [Selenium插件](https://gitee.com/ssssssss-team/spider-flow-selenium)
- [x] [Redis插件](https://gitee.com/ssssssss-team/spider-flow-redis)
- [x] [OSS插件](https://gitee.com/ssssssss-team/spider-flow-oss)
- [x] [Mongodb插件](https://gitee.com/ssssssss-team/spider-flow-mongodb)
- [x] [IP代理池插件](https://gitee.com/ssssssss-team/spider-flow-proxypool)
- [x] [OCR识别插件](https://gitee.com/ssssssss-team/spider-flow-ocr)
- [x] [电子邮箱插件](https://gitee.com/ssssssss-team/spider-flow-mailbox)

> 注意：本分支已从根 `pom.xml` 的 `<modules>` 中移除插件模块（[pom.xml:170-174](pom.xml#L170)），如需插件请引入对应独立仓库产物。

## 其它开源项目

- [spider-flow-vue，spider-flow的前端](https://gitee.com/ssssssss-team/spider-flow-vue)（本分支 `frontend-vue/` 的起点）
- [magic-api，一个以XML为基础自动映射为HTTP接口的框架](https://gitee.com/ssssssss-team/magic-api)
- [magic-api-spring-boot-starter](https://gitee.com/ssssssss-team/magic-api-spring-boot-starter)

> 本机 `vendor/` 目录下存在上述两个仓库的源码克隆，但**当前构建没有任何模块依赖它们**（根 jar 内无 magic-api 相关包），且已在 `.gitignore` 中排除，不随本仓库分发。

## 安全提示（务必阅读）

本分支**没有实现登录与权限体系**，默认端口 8088 直接暴露即可被任意访问，且：

- `/spider/run`、`/rest/run/{id}` 等接口可直接触发任务；
- `/database/execute`（数据源·数据库详情）可在所选数据源上执行任意 SQL；
- `/datasource/all` 等接口返回数据源明文口令。

**禁止将未加固的实例暴露到公网。** 上线前必须按 [docs/security.md](docs/security.md) 的清单完成网络隔离与反向代理鉴权。

## 免责声明

请勿将`spider-flow`应用到任何可能会违反法律规定和道德约束的工作中,请友善使用`spider-flow`，遵守蜘蛛协议，不要将`spider-flow`用于任何非法用途。如您选择使用`spider-flow`即代表您遵守此协议，作者不承担任何由于您违反此协议带来任何的法律风险和损失，一切后果由您承担。

## 技术口径核对与勘误

文档中的第三方技术口径逐条对照了原始出处，本仓库分支相关部分如下；未列出的条目为实测（本机运行/构建）结论，出处为代码行号。

| 原说法（含上游 README/常见经验说法） | 本分支采用的正确说法 | 出处 |
| --- | --- | --- |
| 上游 README 徽章 "JDK-1.8+" | 本分支只在 JDK 21 上验证；JDK 16 起 JDK 内部 API 默认强封装，需显式 `--add-opens`，不能沿用 1.8 的启动方式 | [JEP 403](https://openjdk.org/jeps/403)、[JEP 335/372](https://openjdk.org/jeps/335) |
| "JDK 内置 Nashorn 可直接用" | Nashorn 在 JDK 11 被标记废弃（JEP 335），JDK 15 移除（JEP 372），须用独立 `nashorn-core` 且包名为 `org.openjdk.nashorn.*` | [openjdk/nashorn](https://github.com/openjdk/nashorn)、[GraalVM Nashorn 迁移指南](https://www.graalvm.org/jdk24/reference-manual/js/NashornMigrationGuide/) |
| "换驱动坐标即可，与 MySQL 版本无关" | Connector/J 9.x 官方口径为配合 MySQL Server 8.0 及以上使用，实现 JDBC 4.2 | [MySQL Connector/J Release Notes](https://dev.mysql.com/doc/relnotes/connector-j/en/) |
| "Node 版本无所谓，`npm run build` 直接跑" | Node 17 起改用 OpenSSL 3，md4 等算法进入 legacy provider，webpack4 必须加 `--openssl-legacy-provider` | [Node.js ERR_OSSL_EVP_UNSUPPORTED 讨论](https://stackoverflow.com/questions/75959563/node-js-err-ossl-evp-unsupported-error-when-running-npm-run-start) |
| "fastjson 只要不是最新版就安全" | 1.x 系列存在长期自动反序列化风险；`pom.xml:21` 锁定 1.2.58，低于修复 CVE-2022-25845 的 1.2.83，且 1.2.68–1.2.83 已披露无补丁的 RCE（CVE-2026-16723） | [GitHub advisory 转述](https://github.com/alibaba/bulbasaur/pull/21)、[NVD CVE-2026-16723](https://nvd.nist.gov/vuln/detail/CVE-2026-16723) |
| "Spring Boot 2.0.7 仍受官方支持" | 2.0 线开源支持早已结束，本分支不升级框架、只做运行适配，安全补丁需自行评估 | [endoflife.date/spring-boot](https://endoflife.date/spring-boot) |

文档中未标注官方出处的容量、耗时、行数上限等数字均为本仓库代码常量或本机经验值，已在正文注明。

---

# 关于作者

> 源码猿（master336）—— 一个把「踩过的坑」写成「别人脚下的路」的技术作者。

## 一句话介绍

**源码猿（master336）**，现就职于**某公司**，担任**高级技术架构师 / 产品售前专家**（腾讯云开发者社区认证）。长期深耕 Java 后端与分布式架构，近年将主要精力投向 **AI Agent、大模型私有化部署与工程化落地**，坚持用「能跑通、可复现、讲原理」的标准写每一篇文章。

## 身份与平台

| 平台 | 主页 | 身份 / 荣誉 |
| --- | --- | --- |
| CSDN 博客 | [master336.blog.csdn.net](https://master336.blog.csdn.net) | 人工智能领域**新星创作者**，原力等级 **298**，累计获赞 **461** 次、评论 **86** 条 |
| 腾讯云开发者社区 | [cloud.tencent.com/developer/user/1558800](https://cloud.tencent.com/developer/user/1558800) | 认证作者 · 某公司｜高级技术架构师、产品售前专家 |

## 技术视野

作者的写作版图横跨「从底层到应用、从传统工程到 AI 原生」的完整链路：

- **后端与架构**：Java、Spring / SpringBoot / SpringCloud、微服务架构、Maven
- **数据与存储**：MySQL、Redis、PostgreSQL、Greenplum、GaussDB、ETL 与大数据处理
- **运维与基础设施**：Linux、Nginx、操作系统、网络协议（TCP/UDP、HTTP/HTTPS、OSI 七层模型）、RabbitMQ
- **前端与客户端**：JavaScript、CSS、Vue.js、HTML、Android、微信小程序
- **AI 与智能化**：机器学习、NLP、图像处理、数据分析、AIGC
- **AI 工程化（近期主线）**：AI Agent 架构与多智能体协同、Ollama / ComfyUI 私有大模型部署、MCP 协议、Skill 资产化、大模型评测与上下文召回

## 写作风格

不同于「Hello World 式」的速食教程，作者的文章有三个鲜明特点：

1. **讲透原理** —— 不满足于告诉你「怎么做」，更在意解释「为什么这么做」；
2. **给出可落地的工程** —— 完整目录结构、核心代码、硬件选型、避坑清单，拿来就能改；
3. **有数据、有出处** —— 明确区分哪些是实测、哪些是预测、哪些不可横向比较，拒绝拍脑袋结论。

近期在腾讯云开发者社区连续产出《用 CodeArts 把一个自托管云相册真正做完》《Ollama 私有大模型部署与生态配置完全指南》《从零构建 AI Agent：一份写给工程师的深度开发指南》《一次讲透 HTTP 与 HTTPS》等长文，几乎保持日更节奏。

## 联系方式

- CSDN：[https://master336.blog.csdn.net](https://master336.blog.csdn.net)
- 腾讯云开发者社区：[https://cloud.tencent.com/developer/user/1558800](https://cloud.tencent.com/developer/user/1558800)

---

## ☕ 支持作者

看到这里，说明你已经陪我读到了文章的最后。

如果这些文字曾经帮你**少踩一个坑、少熬一个夜、少走一次弯路**，那它就是有价值的。写作这件事，靠的不只是兴趣，还有一台够用的机器、几个付费的实验环境、以及愿意花在查证与实测上的时间。

**你的每一份支持，都会变成下一篇文章里的一个真实实验、一次更深入的拆解。**

> 不用有压力 —— 一杯咖啡的钱，就足够让我开心很久。
> 当然，一个点赞、一条评论、一次转发，同样是最好的鼓励。

### 打赏渠道

<div align="center">

| 微信支付 | 支付宝 |
| :---: | :---: |
| ![微信支付二维码](images/wx.png) | ![支付宝二维码](images/zfb.jpg) |
| *推荐使用微信支付* | *支持信用卡 / 花呗付款* |

</div>

### 不打烊的支持方式

如果不方便打赏，下面这些同样珍贵：

- 👍 **点赞** —— 让更多人看到这篇内容
- ⭐ **收藏** —— 下次要用时能第一时间找到
- 💬 **评论** —— 你的问题常常是下一篇选题的起点
- 🔗 **转发** —— 把有用的东西分享给需要的朋友

感谢每一位读到这里的人。我们下篇文章见。

---

*Made with ❤️ by 源码猿（master336）*
