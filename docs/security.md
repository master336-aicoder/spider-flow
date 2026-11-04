# 安全说明与加固清单

结论先说：**spider-flow（含本分支）是一个假定"内网可信环境"的运维工具，没有登录、没有角色、没有审计**。本分支新增了「任意 SQL 执行」和「数据源元数据浏览」两个功能，攻击面比上游更大，因此**禁止在未加固的情况下暴露到公网**。

本文列出暴露面、风险成因、加固方案与依赖已知漏洞。所有代码位置均可点开核对。

---

## 1. 暴露面总览

| 风险 | 说明 | 代码位置 | 缓解现状 |
| --- | --- | --- | --- |
| **无身份认证** | 任何能访问 8088 的人都能读写全部配置、运行任务 | 全应用无 Spring Security/过滤器 | ❌ 需靠网络隔离 + 反向代理鉴权 |
| **CORS 全放开** | `allowedOrigins("*")` + `allowCredentials(true)`，浏览器里的任意网站都能调用管理接口（配合社工/内网访问即等价于 CSRF） | [ResourcesConfiguration.java:15-21](../spider-flow-web/src/main/java/org/spiderflow/configuration/ResourcesConfiguration.java#L15) | ❌ 未加固，建议按 §4.2 收口 |
| **任意 SQL 通道** | `/database/execute` 在所选数据源上执行任意语句，含 DDL/DML，不止读 | [DatabaseController.java:111](../spider-flow-web/src/main/java/org/spiderflow/controller/DatabaseController.java#L111) | ⚠️ 仅有限流：500 行 / 30 秒 |
| **数据源口令泄露** | `/datasource/all` 返回完整实体，含 `password` 明文 | [DataSourceController.java:33-36](../spider-flow-web/src/main/java/org/spiderflow/controller/DataSourceController.java#L33) | ❌ `/datasource/get` 已置空口令（[:50](../spider-flow-web/src/main/java/org/spiderflow/controller/DataSourceController.java#L50)），`/all` 没有 |
| **口令明文入库** | `sp_datasource.password varchar(32)` 明文存储 | [db/spiderflow.sql:32](../db/spiderflow.sql#L32) | ❌ 上游设计，备份文件按敏感数据对待 |
| **可触发任务** | `/spider/run`、`/rest/run/{id}`、`/rest/runAsync/{id}` 无鉴权即可跑爬虫（对外发起请求、写库） | [SpiderRestController.java:52-103](../spider-flow-web/src/main/java/org/spiderflow/controller/SpiderRestController.java#L52) | ❌ |
| **可改运行参数** | `/system/params/save` 能关定时任务、改工作目录与线程池 | [SystemController.java:36](../spider-flow-web/src/main/java/org/spiderflow/controller/SystemController.java#L36) | ⚠️ key 白名单 + 数值校验，不可注入任意 properties |
| **SSRF** | request 节点 URL 完全由流程定义决定，被控账号可用它探测内网端口 | [RequestExecutor.java](../spider-flow-core/src/main/java/org/spiderflow/core/executor/shape/RequestExecutor.java) | ❌ 无 URL 白名单 |
| **任意文件写出** | 输出节点「输出到 CSV 文件」写服务端本地路径 | [helpTexts.js:69](../frontend-vue/src/config/helpTexts.js#L69) | ❌ 由流程定义决定路径 |
| **JS 脚本执行** | 自定义函数/脚本节点在服务端执行 JS（Nashorn） | [ScriptManager.java](../spider-flow-core/src/main/java/org/spiderflow/core/script/ScriptManager.java) | ❌ 等价于远程代码执行入口，必须视为管理员功能 |

本分支已做的正向措施：

- autoSQL 的动态标识符（表名/库名/模式名/列名）走白名单正则，非法值**告警跳过**而不是拼进 SQL（[AutoSQLExecutor.java:66](../spider-flow-core/src/main/java/org/spiderflow/core/executor/shape/AutoSQLExecutor.java#L66)、[:141](../spider-flow-core/src/main/java/org/spiderflow/core/executor/shape/AutoSQLExecutor.java#L141)）。
- 工作台数据源列表**不返回口令**，只回显 `host:port/db`（[DashboardController.java:336-348](../spider-flow-web/src/main/java/org/spiderflow/controller/DashboardController.java#L336)）。
- `/database/*` 与 autoSQL 用完连接都会还原 catalog/schema，避免污染连接池导致后续任务写错库（[DatabaseController.java:174-184](../spider-flow-web/src/main/java/org/spiderflow/controller/DatabaseController.java#L174)）。
- 数据库详情抽屉与 autoSQL 页面都有醒目提示，说明 SQL 直接作用在数据源上。

---

## 2. 配置中的凭据

[application.properties:30-32](../spider-flow-web/src/main/resources/application.properties#L30) 当前是本机开发库口令（`root` / `root`）。

- 这是**开发用默认口令**，随源码分发到公开仓库等于泄露"内部约定"。生产部署必须用 `--spring.config.location` 或环境变量从外部注入，不要提交。
- 本仓库的 `.gitignore` 排除了 `probe/`（含本机联调产生的日志与响应样本），避免把带口令的文件误提交。
- 提交/推送前建议执行一次自查：

```bash
git grep -nI "password\|passwd\|secret" -- ':!docs' ':!*.md' | head
```

---

## 3. SQL 通道的使用规范

`/database/execute` 与页面「数据库详情 → SQL 执行」：

1. **只给运维/开发内网使用**，并在反代上按 §4.2 屏蔽外部路径。
2. 生产环境建议为元数据浏览单独注册一个**只读数据源**（仅 `SELECT` 权限、仅目标库），把写权限数据源留给明确的流程。
3. 单次上限 500 行 / 30 秒只防"拖库拖表"，**不防破坏**：`delete`/`drop` 会照常执行。
4. 该接口目前不写审计日志。开启 MySQL 侧审计或用反代 access log 记录 `POST /database/execute` 的请求体摘要（注意不要把 SQL 里的口令记进日志）。

---

## 4. 加固清单

### 4.1 网络层（最低要求）

- [ ] 服务只监听内网地址：`server.address=127.0.0.1`（同机反代）或防火墙限制来源 IP。
- [ ] 不开放 8088 到公网；对外只走 HTTPS 反向代理。
- [ ] 备份/镜像仓库中的 `sp_datasource` 数据按敏感数据加密存放。

### 4.2 反向代理鉴权与路径收口

`/datasource/all`、`/database/*`、`/system/*`、`/rest/*`、`/spider/run` 是最危险的几组，建议默认拒绝、按需放行：

```nginx
server {
  listen 443 ssl;
  server_name spider.internal.example.com;

  auth_basic "spider-flow";
  auth_basic_user_file /etc/nginx/.htpasswd;

  # 危险接口：默认对任何来源关闭（包括已登录用户，仅运维网段放行）
  location ~ ^/(database|datasource/all|system/params/save|rest)(/|$) {
      allow 10.0.0.0/8;
      deny  all;
      proxy_pass http://127.0.0.1:8088;
      proxy_set_header Host $host;
  }

  # 前端与常规页面接口
  location / {
      proxy_pass http://127.0.0.1:8088;
      proxy_set_header Host $host;
      proxy_set_header X-Real-IP $remote_addr;
  }

  location /ws {
      proxy_pass http://127.0.0.1:8088;
      proxy_http_version 1.1;
      proxy_set_header Upgrade $http_upgrade;
      proxy_set_header Connection "upgrade";
      proxy_read_timeout 3600s;
  }
}
```

- [ ] 把 CORS 从 `*` 收紧到实际调用方域名，并去掉 `allowCredentials(true)`（[ResourcesConfiguration.java:17-20](../spider-flow-web/src/main/java/org/spiderflow/configuration/ResourcesConfiguration.java#L17)）。
- [ ] Nginx 记录访问日志并保留 ≥90 天，作为最低限度的审计。

### 4.3 账号与流程侧

- [ ] 主库连接账号只给 spiderflow 库的必要权限，**不要给 root**。
- [ ] 业务数据源按最小权限拆分：抓取写入用低权限账号，人用的"数据库详情"配只读账号。
- [ ] 自定义函数/脚本节点视同管理员能力，流程 XML 的修改权限即代码执行权限。
- [ ] `spider.job.enable` 默认保持 `false`（本分支已如此），部署验证完成后再开。

### 4.4 主机侧

- [ ] 以专用非 root 用户运行；`spider.workspace` 目录权限 `750`。
- [ ] 出网限制：只允许访问目标站点（降低被抓取内容利用的风险）。
- [ ] JVM 参数保持 `-Dfile.encoding=UTF-8`，避免日志与参数解析出现编码类问题（属可用性而非机密性，但会掩盖故障现场）。

---

## 5. 依赖已知漏洞（按本仓库锁定版本）

| 依赖 | 锁定版本 | 风险口径 | 建议 |
| --- | --- | --- | --- |
| `com.alibaba:fastjson` | 1.2.58（[pom.xml:21](../pom.xml#L21)） | 低于修复 CVE-2022-25845（autoType 绕过反序列化 RCE）所需的 1.2.83；1.x 系列另有 2026 年披露的 RCE（CVE-2026-16723，官方描述影响 1.2.68–1.2.83，**1.2.58 是否受同一漏洞影响未见官方说明，属未确认项**） | 升级到 1.2.83 并开启 `safeMode`，或迁移 fastjson2；短期靠"不接受不可信 JSON 输入"降低暴露 |
| `spring-boot-starter-parent` | 2.0.7.RELEASE（[pom.xml:16](../pom.xml#L16)） | 2.0 线开源支持早已结束，不再有安全补丁 | 评估升级到 2.7.x（仍在维护尾期）或 3.x（需 JDK17+、命名空间迁移，工作量大） |
| `com.alibaba:druid` | 1.1.16（[pom.xml:22](../pom.xml#L22)） | 未配置 `stat-view-servlet`，因此 `/druid/*` 监控页默认不存在；若启用需加口令 | 保持默认关闭；启用前先加认证 |
| `org.jsoup:jsoup` | 1.11.3（[pom.xml:29](../pom.xml#L29)） | 早于后续多处解析加固版本 | 若只解析可信来源可暂缓，否则升级 |
| `com.google.guava` | 28.2-jre（[pom.xml:28](../pom.xml#L28)） | 存在已披露的临时文件权限等问题 | 视主机多用户环境决定升级 |
| `mysql-connector-j` | 9.1.0（[pom.xml:62-64](../pom.xml#L62)） | 本分支为 JDK21/MySQL8 适配特意提升的版本，非风险项 | 保持 |
| `org.openjdk.nashorn:nashorn-core` | 15.6（[spider-flow-core/pom.xml:20-24](../spider-flow-core/pom.xml#L20)） | 引擎本身已停止演进；风险来自"服务端执行用户可写的 JS"这一能力 | 限制自定义函数的编辑权限 |

> 本表**未做 SCA 扫描**，是基于版本号的公开信息整理；正式评估请跑 `mvn org.owasp:dependency-check-maven:check` 或等价工具，并把结果附在这里。

---

## 6. 自查：确认你的部署是否裸奔

```bash
# 1) 未授权能否拿到数据源口令（返回内容里有 "password" 字段即中招）
curl -s http://<host>:8088/datasource/all | grep -o '"password"'
# 2) 未授权能否执行 SQL
curl -s -X POST http://<host>:8088/database/execute -d 'datasourceId=<id>&sql=select 1'
# 3) 未授权能否触发任务
curl -s -X POST http://<host>:8088/rest/runAsync/<flowId>
```

以上任何一条从**非运维网段**成功返回，都应按 §4 立即整改。

---

## 7. 未复核项（如实声明）

- 未做渗透测试，也没有跑 SCA 扫描器；§5 是版本级公开信息归纳。
- 未验证"关闭 CORS 通配 + 反代鉴权"组合在前端页面上的兼容性（例如 `/ws` 与表单请求的 Origin 校验），实施时请回归一遍编辑器保存/测试/日志推送。
- `/datasource/all` 的口令泄露是**上游既有行为**，本分支未修改其返回字段（改动会影响前端下拉与编辑回显，需连带设计），只在工作台侧避免了回显。

---

## 8. 出处

| 口径 | 出处 |
| --- | --- |
| fastjson < 1.2.83 存在 autoType 绕过反序列化漏洞（CVE-2022-25845） | [GitHub advisory 转述](https://github.com/alibaba/bulbasaur/pull/21) |
| fastjson 1.2.68–1.2.83 RCE（CVE-2026-16723），1.x 无官方补丁 | [NVD](https://nvd.nist.gov/vuln/detail/CVE-2026-16723)、[CSA 研究说明](https://labs.cloudsecurityalliance.org/research/csa-research-note-fastjson-1x-zero-patch-rce-cve-2026-16723/) |
| Spring Boot 2.0 线开源支持已结束 | [endoflife.date/spring-boot](https://endoflife.date/spring-boot) |
| Nashorn 在 JDK 11 废弃、JDK 15 移除，独立版本面向 Java 15+ | [JEP 335](https://openjdk.org/jeps/335)、[openjdk/nashorn](https://github.com/openjdk/nashorn) |
