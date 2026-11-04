# 部署手册

面向把本仓库跑起来的人。所有结论均来自本仓库代码或本机实测，标注了 `文件:行号`；第三方版本口径见文末《出处与勘误》。

- 日常巡检、备份、故障排查请看 [operations.md](operations.md)
- 风险与加固清单请看 [security.md](security.md)

---

## 1. 环境要求

| 组件 | 要求 | 校验命令 | 备注 |
| --- | --- | --- | --- |
| JDK | 21（本分支唯一验证过的版本） | `java -version` | 低于 16 无需 `--add-opens`，但 Nashorn 依赖要求 11+；本分支按 21 出包 |
| Maven | 3.6+ | `mvn -v` | 需能访问中央仓库或内网镜像；离线构建加 `-o` |
| Node.js | 17+ 需 legacy provider，本分支用 24.x 验证 | `node -v` | 构建插件已自动注入 `NODE_OPTIONS`，见 §4.2 |
| MySQL | 8.0（Connector/J 9.1.0 的官方口径为 8.0 及以上） | `select version()` | 字符集 `utf8mb4` |
| 磁盘 | 建议 ≥10 GB 空闲 | — | `spider.workspace` 下的任务日志与历史 XML 只增不减，见 §7.2 |

### 端口

| 端口 | 用途 | 定义位置 |
| --- | --- | --- |
| 8088 | 应用 HTTP + WebSocket(`/ws`) | [application.properties:1](../spider-flow-web/src/main/resources/application.properties#L1) |
| 3000 | 仅前端本地开发 `npm run dev`，代理到 8088 | [vue.config.js](../frontend-vue/vue.config.js) `devServer.port` |
| 3306 | MySQL | 数据源配置 |

---

## 2. 数据库初始化

```bash
mysql -uroot -p -e "create database spiderflow default charset utf8mb4 collate utf8mb4_general_ci"
mysql -uroot -p spiderflow < db/spiderflow.sql
```

脚本内容（[db/spiderflow.sql](../db/spiderflow.sql)）：

| 表 | 作用 |
| --- | --- |
| `sp_flow` | 流程定义（mxGraph XML）+ cron + 启用标记 + 执行统计 |
| `sp_datasource` | 数据源（驱动、jdbc url、用户名、口令） |
| `sp_variable` | 全局变量 |
| `sp_task` | 任务运行记录（只有起止时间，没有成功/失败字段） |
| `sp_function` | 自定义函数 |
| `sp_flow_notice` | 流程通知配置 |
| `sp_sys_config` | **本分支新增**：系统参数覆盖值（`config_key` 唯一） |

`sp_sys_config` 建表语句已写进脚本，同时服务启动时还会 `CREATE TABLE IF NOT EXISTS` 兜底一次，因此从老库升级不需要手工 DDL（[SystemParamService.java:87-94](../spider-flow-core/src/main/java/org/spiderflow/core/service/SystemParamService.java#L87)）。

Quartz 使用内存 JobStore，没有 `QRTZ_*` 表；调度状态在启动时由 `sp_flow` 里的 cron 重建（[SpiderFlowService.java](../spider-flow-core/src/main/java/org/spiderflow/core/service/SpiderFlowService.java)）。

---

## 3. 应用配置

配置文件：[spider-flow-web/src/main/resources/application.properties](../spider-flow-web/src/main/resources/application.properties)。 jar 内配置可在启动时用外部文件覆盖：

```bash
java -jar spider-flow.jar --spring.config.location=file:/etc/spider-flow/application.properties
```

### 3.1 必改项

| 键 | 默认值（本仓库） | 说明 |
| --- | --- | --- |
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/spiderflow?...&serverTimezone=GMT%2B8` | 生产环境必须换成实际地址 |
| `spring.datasource.username` / `password` | `root` / `root`（**开发机口令，不可用于生产**） | 见 security.md §3 |
| `spring.datasource.driver-class-name` | `com.mysql.cj.jdbc.Driver` | Connector/J 9.x 的类名，旧的 `com.mysql.jdbc.Driver` 已不可用 |
| `spider.workspace` | `D:/data/spider` | 任务日志/历史 XML/布隆过滤器根目录，Linux 改成如 `/data/spider` |
| `spider.job.enable` | `false` | 定时任务总开关，本分支默认关闭（避免刚部署就批量触发） |
| `spring.mail.*` | 占位 `xxxx@qq.com` | 只用邮件通知才需要填 |

### 3.2 本分支新增/调整的配置

| 键 | 值 | 为什么 |
| --- | --- | --- |
| `spring.resources.cache.cachecontrol.no-cache` | `true` | `index.html` 每次回源校验，否则前端更新后浏览器一直用旧页面（js/css 带 hash，命中 304 即可） |
| `spider.notice.*` | `\uXXXX` 转义 | `java.util.Properties` 按 ISO-8859-1 读取，直接写中文会得到乱码 |

### 3.3 运行期可调参数（不重启）

系统设置页可修改并**立即生效**的 8 项参数，白名单定义在 [SystemParamService.java:63-72](../spider-flow-core/src/main/java/org/spiderflow/core/service/SystemParamService.java#L63)：

| 键 | 中文名 | 兜底默认 | 生效时机 | 校验 |
| --- | --- | --- | --- | --- |
| `spider.job.enable` | 定时任务总开关 | `true`（properties 里为 `false`） | 保存后立即重建调度（[SystemController.java:40-42](../spider-flow-web/src/main/java/org/spiderflow/controller/SystemController.java#L40)） | 仅 `true`/`false` |
| `spider.workspace` | 工作目录 | properties 值 | 新任务使用新目录；**应用日志路径不随之改变**（logback 在启动时解析） | — |
| `spider.thread.max` | 最大线程数 | `64` | 立即 resize 现有线程池（[SystemParamService.java:229-231](../spider-flow-core/src/main/java/org/spiderflow/core/service/SystemParamService.java#L229)） | 正整数 |
| `spider.thread.default` | 默认线程数 | `8` | 之后启动的任务 | 正整数 |
| `spider.detect.dead-cycle` | 死循环检测阈值 | `5000` | 之后启动的任务 | 正整数 |
| `spider.bloomfilter.capacity` | 布隆过滤器容量 | `5000000` | 仅对**新建**过滤器生效；已存在的 `<workspace>/<flowId>/url.bf` 需删除才重建 | 正整数 |
| `spider.bloomfilter.error-rate` | 布隆过滤器误判率 | `0.00001` | 同上 | 0~1 小数 |
| `spider.notice.subject` | 邮件通知主题 | `spider-flow流程通知` | 下一条通知 | — |

留空保存 = 删除覆盖行，回到 properties 值（[SystemParamService.java:179-181](../spider-flow-core/src/main/java/org/spiderflow/core/service/SystemParamService.java#L179)）。

---

## 4. 构建

### 4.1 一键构建（推荐）

```bash
mvn -DskipTests package
```

这条命令会依次：`generate-resources` 阶段在 `frontend-vue/` 执行 `npm run build`（[spider-flow-web/pom.xml:36-62](../spider-flow-web/pom.xml#L36)）→ `process-resources` 阶段把 `frontend-vue/dist` 复制进 `target/classes/static`（[:63-84](../spider-flow-web/pom.xml#L63)）→ 打成可执行 jar。

产物：`spider-flow-web/target/spider-flow.jar`（`finalName`/`mainClass` 见 [:31-32](../spider-flow-web/pom.xml#L31)）。

### 4.2 构建注意点

| 现象 | 原因与处理 |
| --- | --- |
| `npm run build` 报 `ERR_OSSL_EVP_UNSUPPORTED` | Node 17+ 的 OpenSSL 3 不再默认提供 md4；exec 插件已注入 `NODE_OPTIONS=--openssl-legacy-provider`（[:51-54](../spider-flow-web/pom.xml#L51)），手工构建前端时需自行带上 |
| Windows 上 exec 插件找不到 npm | 本机 npm 只有 `npm.exe`（无 `npm.cmd`），`windows-npm` profile 已按 OS 激活并改为 `npm.exe`（[:89-97](../spider-flow-web/pom.xml#L89)） |
| jar 里还是旧前端 | `target/classes/static` 会残留上次构建的带 hash 文件，`mvn clean` 不删它就得手工清：`rm -rf spider-flow-web/target/classes/static` 后重新打包 |
| 只想重打后端 | `mvn -DskipTests -Dfrontend.skip=true package`（开关见 [:17,:48](../spider-flow-web/pom.xml#L17)） |
| 前端单独出包 | `cd frontend-vue && NODE_OPTIONS=--openssl-legacy-provider npm run build`，产物在 `frontend-vue/dist` |

### 4.3 首次构建前

```bash
cd frontend-vue && npm install
```

`node_modules/`、`dist/` 不入库（见仓库根 `.gitignore`）。

---

## 5. 启动

### 5.1 前台/开发

```bash
java -Dfile.encoding=UTF-8 \
     --add-opens java.base/java.lang=ALL-UNNAMED \
     --add-opens java.base/java.util=ALL-UNNAMED \
     -jar spider-flow-web/target/spider-flow.jar
```

`--add-opens` 不是可选装饰：JDK 16 起 JDK 内部 API 默认强封装，本应用的反射路径会触碰 `java.base` 内部；缺省 JVM 参数时会出现 `InaccessibleObjectException`。`-Dfile.encoding=UTF-8` 用于避免中文日志与页面文案在 Windows 控制台（GBK）下乱码。

### 5.2 Linux systemd

`/etc/systemd/system/spider-flow.service`：

```ini
[Unit]
Description=spider-flow
After=network.target mysqld.service

[Service]
User=spiderflow
WorkingDirectory=/opt/spider-flow
ExecStart=/usr/bin/java -Dfile.encoding=UTF-8 \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-opens java.base/java.util=ALL-UNNAMED \
  -jar /opt/spider-flow/spider-flow.jar \
  --spring.config.location=file:/etc/spider-flow/application.properties
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
systemctl daemon-reload && systemctl enable --now spider-flow
```

### 5.3 Windows

作为服务运行可用任意 Java 服务包装器（如 WinSW/NSSM），参数与 §5.1 完全一致。直接双击运行请写 `.bat`：

```bat
java -Dfile.encoding=UTF-8 --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED -jar spider-flow.jar
```

### 5.4 Docker

仓库根 [Dockerfile](../Dockerfile) 已改为 JDK 21 基础镜像（上游的 `FROM java:8` 已废弃且与本分支不兼容）：

```bash
mvn -DskipTests package
docker build -t spider-flow:0.5.0-jdk21 .
docker run -d --name spider-flow -p 8088:8088 \
  -v /data/spider:/data/spider \
  spider-flow:0.5.0-jdk21 \
  --spider.workspace=/data/spider \
  --spring.datasource.url=jdbc:mysql://host:3306/spiderflow?...
```

compose 片段：

```yaml
services:
  spider-flow:
    image: spider-flow:0.5.0-jdk21
    ports: ["8088:8088"]
    volumes: ["/data/spider:/data/spider"]
    command: ["--spider.workspace=/data/spider"]
    environment:
      - TZ=Asia/Shanghai
```

容器启动命令已带 `--add-opens`；配置通过挂载或命令行参数覆盖，不要把生产口令写进镜像。

### 5.5 Nginx 反向代理

调试窗走 WebSocket `/ws`，必须显式升级：

```nginx
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
```

---

## 6. 启动校验

```bash
# 1) 页面可达（返回 200 即为前端已就位）
curl -s -o /dev/null -w "%{http_code}\n" http://127.0.0.1:8088/
# 2) 系统状态接口（含 JVM/内存/线程池/调度）
curl -s http://127.0.0.1:8088/system/status
# 3) 参数清单（确认覆盖值与默认值）
curl -s http://127.0.0.1:8088/system/params
```

`/system/status` 返回字段见 [SystemStatusService.java:40-104](../spider-flow-core/src/main/java/org/spiderflow/core/service/SystemStatusService.java#L40)：`jvm.*`（javaVersion/uptime/threadCount…）、`memory.*`（heap used/committed/max）、`spider.*`（jobEnable/workspace/threadMax/poolActiveThreads/poolQueueSize/runningJobs/scheduledJobs/quartzJobCount）。

应用日志：`<workspace>/logs/spider-flow.log`（[logback-spring.xml:13-14](../spider-flow-web/src/main/resources/logback-spring.xml#L13)）。启动失败先看这个文件与控制台。

---

## 7. 数据目录

`spider.workspace` 下的结构（实测 + 代码依据）：

```
<workspace>/
├── logs/spider-flow.log              # 应用日志（logback FileAppender，无滚动）
└── <flowId>/
    ├── logs/<taskId>.log             # 单次任务日志（页面日志查看器与 /spider/log 读它）
    ├── xmls/<timestamp>.xml          # 每次保存的历史流程 XML
    └── url.bf                        # URL 去重布隆过滤器（持久化，跨任务复用）
```

依据：任务日志路径 [SpiderFlowController.java:173,:186](../spider-flow-web/src/main/java/org/spiderflow/controller/SpiderFlowController.java#L173)；布隆过滤器读写 [RequestExecutor.java:443-444,:464-469](../spider-flow-core/src/main/java/org/spiderflow/core/executor/shape/RequestExecutor.java#L443)。

### 7.1 迁移主机时

`<workspace>` 整目录 + `mysqldump` 一起搬；只搬数据库会丢历史 XML 与去重状态（去重状态丢了会导致重复抓取）。

### 7.2 容量增长（重要）

- `logs/`、`xmls/`、`spider-flow.log` **都没有自动清理或滚动策略**。
- 上线后请配置外部清理：例如按天切割 `spider-flow.log`（logback 换成 `RollingFileAppender`）、`find <workspace> -name '*.log' -mtime +30 -delete` 加入 crontab。
- 具体阈值与脚本见 [operations.md §4 磁盘](operations.md#4-磁盘与清理)。

---

## 8. 升级与回滚

### 8.1 升级本分支

```bash
# 备份
mysqldump -uroot -p --databases spiderflow > spiderflow.$(date +%F).sql
cp -a /data/spider /data/spider.bak.$(date +%F)
# 换包
systemctl stop spider-flow
cp spider-flow-web/target/spider-flow.jar /opt/spider-flow/spider-flow.jar.new
mv /opt/spider-flow/spider-flow.jar{,.old} && mv /opt/spider-flow/spider-flow.jar{.new,}
systemctl start spider-flow
```

配置项新增时用 `diff` 比对 `application.properties`，本分支新增的键见 §3.2。

### 8.2 回滚

换回 `spider-flow.jar.old` 并重启即可。`sp_sys_config` 是新增表，回滚到不识别它的版本不会有影响（旧版本不读它，参数回到 properties 值）。

---

## 9. 常见部署故障

| 现象 | 判断依据 | 处理 |
| --- | --- | --- |
| 启动即退出，日志 `Failed to configure a DataSource` | 数据库不通/账号错 | 检查 url、`allowPublicKeyRetrieval=true`（MySQL 8 的 `caching_sha2_password` 需要它） |
| 页面 404 或白屏 | `curl /` 返回 404 | jar 里没有 `static/index.html`：前端未构建或被 `target/classes/static` 残留覆盖，见 §4.2 |
| 改了配置不生效 | `/system/params` 显示 `customized=true` | 页面覆盖值优先于 properties，需在系统设置页清空该项 |
| 中文参数保存后变乱码 | 页面回显乱码 | 确认 JVM `-Dfile.encoding=UTF-8`，且 properties 中中文用 `\uXXXX` |
| 调试窗连不上 | 浏览器控制台 WebSocket 失败 | 反代未转发 `/ws` 升级头，见 §5.5 |
| 定时任务不跑 | `/system/status` 里 `jobEnable=false` | 本分支默认关闭；在系统设置页打开并确认流程有 cron 且 `enabled=1` |

更多运行期排查（任务卡住、线程池排队、SQL 节点报错）见 [operations.md](operations.md)。

---

## 10. 出处与勘误

| 本文口径 | 正确说法 | 出处 |
| --- | --- | --- |
| "JDK 8 也能按本手册启动" | 不行。JDK 16 起 JDK 内部元素默认强封装（`--illegal-access=deny`），JDK 17（JEP 403）后成为不可绕过的默认；本手册的 `--add-opens` 是针对该变化的处理 | [JEP 403](https://openjdk.org/jeps/403) |
| "Nashorn 是 JDK 自带能力" | JDK 11 标记废弃（JEP 335）、JDK 15 移除（JEP 372）；独立 Nashorn 主要面向 Java 15+，包名 `org.openjdk.nashorn.api.scripting` | [openjdk/nashorn README](https://github.com/openjdk/nashorn)、[JEP 335](https://openjdk.org/jeps/335) |
| "Connector/J 换坐标即可连任意 MySQL" | Connector/J 9.x 面向 MySQL Server 8.0 及以上，实现 JDBC 4.2 | [MySQL Connector/J 文档](https://dev.mysql.com/doc/relnotes/connector-j/en/) |
| "`--openssl-legacy-provider` 是可选加速参数" | 它是 Node 17+（OpenSSL 3）下运行 webpack4（md4 摘要）的必要 workaround，OpenSSL 3 把 md4 移入默认关闭的 legacy provider | [Node.js ERR_OSSL_EVP_UNSUPPORTED 讨论](https://stackoverflow.com/questions/75959563/node-js-err-ossl-evp-unsupported-error-when-running-npm-run-start) |
| 表格中 `capacity=5000000`、`error-rate=0.00001` 等默认值 | 来自代码兜底常量，与 properties 中的示例值（1000000 / 0.0001）不同：properties 缺省时以 properties 为准，未配置时以代码兜底为准 | [RequestExecutor.java:453](../spider-flow-core/src/main/java/org/spiderflow/core/executor/shape/RequestExecutor.java#L453)、[application.properties:14-16](../spider-flow-web/src/main/resources/application.properties#L14) |
| "磁盘 ≥10 GB 空闲" | 经验值，非官方口径；实际取决于任务日志量 | — |
