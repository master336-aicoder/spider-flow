package org.spiderflow.controller;

import java.sql.Connection;
import java.sql.DriverManager;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import javax.annotation.PreDestroy;

import org.apache.commons.lang3.StringUtils;
import org.spiderflow.core.mapper.SpiderFlowMapper;
import org.spiderflow.core.model.DataSource;
import org.spiderflow.core.model.SpiderFlow;
import org.spiderflow.core.service.DataSourceService;
import org.spiderflow.core.service.SystemStatusService;
import org.spiderflow.model.JsonBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

/**
 * 工作台：任务、数据库、系统的状态与统计视图
 */
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

	/**
	 * 数据源连通性探测的超时保护，避免个别坏连接把工作台请求挂死
	 */
	private static final int PROBE_TIMEOUT_SECONDS = 3;

	private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private static final Pattern SAFE_TABLE_NAME = Pattern.compile("^[A-Za-z0-9_]{1,64}$");

	private final ExecutorService probePool = Executors.newCachedThreadPool(runnable -> {
		Thread thread = new Thread(runnable, "dashboard-probe");
		thread.setDaemon(true);
		return thread;
	});

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private SpiderFlowMapper spiderFlowMapper;

	@Autowired
	private SystemStatusService systemStatusService;

	@Autowired
	private DataSourceService dataSourceService;

	@RequestMapping("/summary")
	public JsonBean<Map<String, Object>> summary(@RequestParam(name = "days", defaultValue = "14") Integer days) {
		int span = days == null || days < 1 || days > 90 ? 14 : days;
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("task", taskSummary());
		result.put("trend", taskTrend(span));
		result.put("flowRank", flowRank());
		result.put("recentTasks", recentTasks());
		result.put("system", systemStatusService.collect());
		return new JsonBean<>(result);
	}

	@RequestMapping("/database")
	public JsonBean<Map<String, Object>> database() {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("main", mainDatabaseInfo());
		result.put("tables", tableStats());
		result.put("datasources", datasourceStats());
		return new JsonBean<>(result);
	}

	private Map<String, Object> taskSummary() {
		Map<String, Object> task = new LinkedHashMap<>();
		List<SpiderFlow> flows = spiderFlowMapper.selectList(new QueryWrapper<SpiderFlow>()
			.select("id", "name", "enabled", "cron", "execute_count", "last_execute_time", "next_execute_time"));
		int enabled = 0;
		int scheduled = 0;
		long executeTotal = 0;
		for (SpiderFlow flow : flows) {
			if ("1".equals(flow.getEnabled())) {
				enabled++;
				if (StringUtils.isNotEmpty(flow.getCron())) {
					scheduled++;
				}
			}
			executeTotal += flow.getExecuteCount() == null ? 0 : flow.getExecuteCount();
		}
		task.put("totalFlows", flows.size());
		task.put("enabledFlows", enabled);
		task.put("scheduledFlows", scheduled);
		task.put("totalExecuteCount", executeTotal);
		Map<String, Object> counts = jdbcTemplate.queryForMap(
			"select count(*) total, ifnull(sum(case when end_time is null then 1 else 0 end), 0) running, "
				+ "ifnull(sum(case when begin_time >= curdate() then 1 else 0 end), 0) today, "
				+ "ifnull(avg(timestampdiff(second, begin_time, end_time)), 0) avg_seconds, "
				+ "ifnull(max(timestampdiff(second, begin_time, end_time)), 0) max_seconds "
				+ "from sp_task");
		task.put("totalTasks", toLong(counts.get("total")));
		task.put("runningTasks", toLong(counts.get("running")));
		task.put("todayTasks", toLong(counts.get("today")));
		task.put("avgDurationMs", toLong(counts.get("avg_seconds")) * 1000);
		task.put("maxDurationMs", toLong(counts.get("max_seconds")) * 1000);
		return task;
	}

	private List<Map<String, Object>> taskTrend(int days) {
		String start = formatDate(addDays(new Date(), -(days - 1)));
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(
			"select date_format(begin_time, '%Y-%m-%d') day, count(*) total, "
				+ "ifnull(sum(case when end_time is null then 1 else 0 end), 0) running "
				+ "from sp_task where begin_time is not null and begin_time >= ? group by day", start);
		Map<String, Map<String, Object>> byDay = new LinkedHashMap<>();
		for (Map<String, Object> row : rows) {
			byDay.put(String.valueOf(row.get("day")), row);
		}
		List<Map<String, Object>> trend = new ArrayList<>();
		for (int i = days - 1; i >= 0; i--) {
			String day = formatDate(addDays(new Date(), -i));
			Map<String, Object> row = byDay.get(day);
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("date", day);
			item.put("total", row == null ? 0 : toLong(row.get("total")));
			item.put("running", row == null ? 0 : toLong(row.get("running")));
			trend.add(item);
		}
		return trend;
	}

	private List<Map<String, Object>> flowRank() {
		List<Map<String, Object>> rank = new ArrayList<>();
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(
			"select id, name, execute_count, enabled, cron, last_execute_time from sp_flow "
				+ "order by ifnull(execute_count, 0) desc, last_execute_time desc limit 8");
		for (Map<String, Object> row : rows) {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("id", row.get("id"));
			item.put("name", row.get("name"));
			item.put("executeCount", toLong(row.get("execute_count")));
			item.put("enabled", row.get("enabled"));
			item.put("cron", row.get("cron"));
			item.put("lastExecuteTime", formatDateTimeValue(row.get("last_execute_time")));
			rank.add(item);
		}
		return rank;
	}

	private List<Map<String, Object>> recentTasks() {
		List<Map<String, Object>> tasks = new ArrayList<>();
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(
			"select t.id, t.flow_id, f.name flow_name, t.begin_time, t.end_time, "
				+ "timestampdiff(second, t.begin_time, t.end_time) duration "
				+ "from sp_task t left join sp_flow f on f.id = t.flow_id "
				+ "order by isnull(t.begin_time), t.begin_time desc limit 10");
		for (Map<String, Object> row : rows) {
			Map<String, Object> item = new LinkedHashMap<>();
			Object end = row.get("end_time");
			item.put("id", row.get("id"));
			item.put("flowId", row.get("flow_id"));
			item.put("flowName", row.get("flow_name") == null ? "已删除流程" : row.get("flow_name"));
			item.put("beginTime", formatDateTimeValue(row.get("begin_time")));
			item.put("endTime", formatDateTimeValue(end));
			item.put("durationMs", row.get("duration") == null ? null : toLong(row.get("duration")) * 1000);
			// sp_task 只有起止时间，没有执行结果字段，只能区分进行中与已结束
			item.put("status", end == null ? "running" : "finished");
			tasks.add(item);
		}
		return tasks;
	}

	private Map<String, Object> mainDatabaseInfo() {
		Map<String, Object> info = new LinkedHashMap<>();
		Map<String, Object> row = jdbcTemplate.queryForMap(
			"select version() version, database() db_name, @@hostname hostname, @@port port, "
				+ "@@character_set_server charset, @@collation_server collation");
		info.put("version", row.get("version"));
		info.put("database", row.get("db_name"));
		info.put("host", row.get("hostname"));
		info.put("port", row.get("port"));
		info.put("charset", row.get("charset"));
		info.put("collation", row.get("collation"));
		try {
			List<Map<String, Object>> status = jdbcTemplate.queryForList("show global status like 'Uptime'");
			info.put("uptimeSeconds", status.isEmpty() ? null : toLong(status.get(0).get("Value")));
		} catch (Exception e) {
			info.put("uptimeSeconds", null);
		}
		Map<String, Object> size = jdbcTemplate.queryForMap(
			"select count(*) table_count, ifnull(sum(data_length + index_length), 0) bytes "
				+ "from information_schema.tables where table_schema = database() and table_type = 'BASE TABLE'");
		info.put("tableCount", toLong(size.get("table_count")));
		info.put("sizeBytes", toLong(size.get("bytes")));
		return info;
	}

	private List<Map<String, Object>> tableStats() {
		List<Map<String, Object>> stats = new ArrayList<>();
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(
			"select table_name name, engine, table_comment, data_length + index_length bytes "
				+ "from information_schema.tables where table_schema = database() and table_type = 'BASE TABLE' "
				+ "order by bytes desc, name asc");
		for (Map<String, Object> row : rows) {
			String name = String.valueOf(row.get("name"));
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("name", name);
			item.put("engine", row.get("engine"));
			item.put("comment", row.get("table_comment"));
			item.put("sizeBytes", toLong(row.get("bytes")));
			// information_schema 的行数是估算值，这里逐表精确统计
			item.put("rows", countRows(name));
			stats.add(item);
		}
		return stats;
	}

	private long countRows(String table) {
		if (!SAFE_TABLE_NAME.matcher(table).matches()) {
			return -1;
		}
		try {
			Long value = jdbcTemplate.queryForObject("select count(*) from `" + table + "`", Long.class);
			return value == null ? 0 : value;
		} catch (Exception e) {
			return -1;
		}
	}

	private List<Map<String, Object>> datasourceStats() {
		List<Map<String, Object>> list = new ArrayList<>();
		List<DataSource> sources = dataSourceService.list(new QueryWrapper<DataSource>()
			.select("id", "name", "driver_class_name", "jdbc_url", "username", "password", "create_date")
			.orderByDesc("create_date"));
		for (DataSource source : sources) {
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("id", source.getId());
			item.put("name", source.getName());
			item.put("driver", source.getDriverClassName());
			item.put("type", driverType(source.getDriverClassName()));
			item.put("createDate", formatDateTime(source.getCreateDate()));
			// 连接信息只回显地址，绝不返回口令
			item.put("target", jdbcTarget(source.getJdbcUrl()));
			long begin = System.currentTimeMillis();
			item.put("connected", probe(source));
			item.put("costMs", System.currentTimeMillis() - begin);
			list.add(item);
		}
		return list;
	}

	/**
	 * 带超时保护的连通性探测：探测线程被弃用也不会阻塞请求线程
	 */
	private boolean probe(DataSource source) {
		if (StringUtils.isBlank(source.getDriverClassName()) || StringUtils.isBlank(source.getJdbcUrl())) {
			return false;
		}
		Future<Boolean> future = probePool.submit(new Callable<Boolean>() {
			@Override
			public Boolean call() {
				Connection connection = null;
				try {
					Class.forName(source.getDriverClassName());
					if (StringUtils.isNotBlank(source.getUsername())) {
						connection = DriverManager.getConnection(source.getJdbcUrl(), source.getUsername(), source.getPassword());
					} else {
						connection = DriverManager.getConnection(source.getJdbcUrl());
					}
					return connection.isValid(2);
				} catch (Exception e) {
					return false;
				} finally {
					if (connection != null) {
						try {
							connection.close();
						} catch (Exception e) {
						}
					}
				}
			}
		});
		try {
			return Boolean.TRUE.equals(future.get(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS));
		} catch (Exception e) {
			future.cancel(true);
			return false;
		}
	}

	private String driverType(String driverClassName) {
		if (StringUtils.isBlank(driverClassName)) {
			return "未知";
		}
		String lower = driverClassName.toLowerCase();
		if (lower.contains("mysql")) {
			return "MySQL";
		}
		if (lower.contains("oracle")) {
			return "Oracle";
		}
		if (lower.contains("sqlserver")) {
			return "SQLServer";
		}
		if (lower.contains("postgresql")) {
			return "PostgreSQL";
		}
		if (lower.contains("sqlite")) {
			return "SQLite";
		}
		if (lower.contains("clickhouse")) {
			return "ClickHouse";
		}
		int index = lower.lastIndexOf('.');
		return index > 0 ? driverClassName.substring(index + 1) : driverClassName;
	}

	private String jdbcTarget(String jdbcUrl) {
		if (StringUtils.isBlank(jdbcUrl)) {
			return "";
		}
		// jdbc:mysql://host:port/db?params -> host:port/db
		int scheme = jdbcUrl.indexOf("//");
		String rest = scheme < 0 ? jdbcUrl : jdbcUrl.substring(scheme + 2);
		int query = rest.indexOf('?');
		if (query >= 0) {
			rest = rest.substring(0, query);
		}
		return rest;
	}

	private long toLong(Object value) {
		if (value == null) {
			return 0;
		}
		if (value instanceof Number) {
			return ((Number) value).longValue();
		}
		try {
			return Long.parseLong(String.valueOf(value));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private String formatDate(Date date) {
		return new SimpleDateFormat("yyyy-MM-dd").format(date);
	}

	private String formatDateTime(Date date) {
		return date == null ? null : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
	}

	/**
	 * JDBC 返回的时间类型随驱动而异（Date / LocalDateTime / String），统一转成展示文本
	 */
	private String formatDateTimeValue(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof Date) {
			return formatDateTime((Date) value);
		}
		if (value instanceof LocalDateTime) {
			return DATETIME_FORMATTER.format((LocalDateTime) value);
		}
		return String.valueOf(value);
	}

	private Date addDays(Date date, int days) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		calendar.add(Calendar.DAY_OF_MONTH, days);
		return calendar.getTime();
	}

	@PreDestroy
	public void shutdown() {
		probePool.shutdownNow();
	}

}
