package org.spiderflow.core.executor.shape;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spiderflow.context.SpiderContext;
import org.spiderflow.core.utils.DataSourceUtils;
import org.spiderflow.core.utils.ExpressionUtils;
import org.spiderflow.executor.ShapeExecutor;
import org.spiderflow.io.SpiderResponse;
import org.spiderflow.model.SpiderNode;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 自动入库执行器：取上游“输出”节点的输出内容，按内容自动建表并批量插入，无需手写 SQL。
 */
@Component
public class AutoSQLExecutor implements ShapeExecutor {

	public static final String DATASOURCE_ID = "datasourceId";

	public static final String CATALOG = "catalog";

	public static final String SCHEMA = "schema";

	public static final String TABLE_NAME = "tableName";

	public static final String BATCH_SIZE = "batchSize";

	public static final String AUTO_CREATE_TABLE = "auto-create-table";

	private static final Logger logger = LoggerFactory.getLogger(AutoSQLExecutor.class);

	/**
	 * 库名、模式名、表名与列名都要拼进 DDL 和 INSERT，只允许字母、数字、下划线和中文，从字符集上杜绝注入
	 */
	private static final Pattern IDENTIFIER = Pattern.compile("^[\\p{Alpha}\\p{IsHan}_][\\p{Alpha}\\p{IsHan}0-9_]{0,63}$");

	private static final Pattern ILLEGAL_CHARACTER = Pattern.compile("[^\\p{Alpha}\\p{IsHan}0-9_]");

	private static final int DEFAULT_BATCH_SIZE = 500;

	/**
	 * 字符串列按实际长度的两倍预留空间，超过阈值改用大文本类型
	 */
	private static final int VARCHAR_LIMIT = 5000;

	private static final int KIND_NULL = 0;

	private static final int KIND_INTEGRAL = 1;

	private static final int KIND_DECIMAL = 2;

	private static final int KIND_BOOLEAN = 4;

	private static final int KIND_TEMPORAL = 8;

	private static final int KIND_TEXT = 16;

	@Override
	public void execute(SpiderNode node, SpiderContext context, Map<String, Object> variables) {
		String datasourceId = node.getStringJsonValue(DATASOURCE_ID);
		if (StringUtils.isBlank(datasourceId)) {
			logger.warn("数据源ID为空！");
			return;
		}
		DataSource dataSource = DataSourceUtils.getDataSource(datasourceId);
		if (dataSource == null) {
			logger.warn("数据源[{}]不存在", datasourceId);
			return;
		}
		String[] identifiers = readIdentifiers(node, variables);
		if (identifiers == null) {
			return;
		}
		String tableName = identifiers[0];
		String catalog = identifiers[1];
		String schema = identifiers[2];
		List<Map<String, Object>> rows = readRows(node, variables);
		if (rows.isEmpty()) {
			logger.info("上游输出节点没有可入库的数据,跳过写入表[{}]", tableName);
			return;
		}
		List<Column> columns = normalizeRows(rows);
		boolean autoCreate = "1".equals(node.getStringJsonValue(AUTO_CREATE_TABLE, "1"));
		int batchSize = Math.max(NumberUtils.toInt(node.getStringJsonValue(BATCH_SIZE), DEFAULT_BATCH_SIZE), 1);
		try (Connection connection = dataSource.getConnection()) {
			Dialect dialect = new Dialect(connection.getMetaData());
			String previousCatalog = readCatalog(connection);
			String previousSchema = readSchema(connection);
			try {
				use(connection, catalog, schema);
				String table = dialect.qualify(catalog, schema, tableName);
				if (autoCreate && !dialect.existsTable(connection, catalog, schema, tableName)) {
					dialect.createTable(connection, table, columns);
					logger.info("自动创建表[{}],共{}列", table, columns.size());
				}
				insertRows(connection, dialect, table, columns, rows, batchSize);
				logger.info("写入表[{}]{}行", table, rows.size());
			} finally {
				restore(connection, previousCatalog, previousSchema);
			}
		} catch (SQLException e) {
			logger.error("写入表[{}]出错,异常信息:{}", tableName, e.getMessage(), e);
			ExceptionUtils.wrapAndThrow(e);
		}
	}

	/**
	 * 读取表名、数据库、模式三个标识符，支持 ${} 表达式；不合法时返回 null
	 */
	private String[] readIdentifiers(SpiderNode node, Map<String, Object> variables) {
		String[] keys = { TABLE_NAME, CATALOG, SCHEMA };
		String[] values = new String[keys.length];
		for (int i = 0; i < keys.length; i++) {
			String value = toText(ExpressionUtils.execute(node.getStringJsonValue(keys[i]), variables)).trim();
			if (StringUtils.isEmpty(value)) {
				// 数据库和模式留空表示用数据源连接里的默认值，表名不能为空
				if (i == 0) {
					logger.warn("表名为空！");
					return null;
				}
				continue;
			}
			if (!IDENTIFIER.matcher(value).matches()) {
				logger.warn("{}[{}]不合法，只允许字母、数字、下划线和中文，且不能以数字开头", keys[i], value);
				return null;
			}
			values[i] = value;
		}
		return values;
	}

	/**
	 * 尽量把会话切到指定库与模式，驱动不支持时忽略，DDL/INSERT 里仍会带上限定名
	 */
	private void use(Connection connection, String catalog, String schema) {
		if (catalog != null) {
			try {
				connection.setCatalog(catalog);
			} catch (SQLException e) {
				logger.debug("切换数据库[{}]失败:{}", catalog, e.getMessage());
			}
		}
		if (schema != null) {
			try {
				connection.setSchema(schema);
			} catch (SQLException e) {
				logger.debug("切换模式[{}]失败:{}", schema, e.getMessage());
			}
		}
	}

	/**
	 * 连接来自 Druid 池，会话切过库或模式就得还原，否则会污染后续取到这条连接的流程
	 */
	private void restore(Connection connection, String catalog, String schema) {
		try {
			connection.setCatalog(catalog);
		} catch (SQLException e) {
			logger.debug("还原数据库[{}]失败:{}", catalog, e.getMessage());
		}
		try {
			connection.setSchema(schema);
		} catch (SQLException e) {
			logger.debug("还原模式[{}]失败:{}", schema, e.getMessage());
		}
	}

	/**
	 * Druid 1.1 的连接代理未实现 getSchema，取不到就按“没有可还原的会话状态”处理
	 */
	private String readCatalog(Connection connection) {
		try {
			return connection.getCatalog();
		} catch (SQLException e) {
			logger.debug("读取当前数据库失败:{}", e.getMessage());
			return null;
		}
	}

	private String readSchema(Connection connection) {
		try {
			return connection.getSchema();
		} catch (SQLException e) {
			logger.debug("读取当前模式失败:{}", e.getMessage());
			return null;
		}
	}

	@Override
	public String supportShape() {
		return "autoSQL";
	}

	/**
	 * 批量插入，table 已由 Dialect 拼好限定名，SQL 里只拼标识符，所有值都走占位符
	 */
	private void insertRows(Connection connection, Dialect dialect, String table, List<Column> columns, List<Map<String, Object>> rows, int batchSize) throws SQLException {
		StringBuilder fields = new StringBuilder();
		StringBuilder placeholders = new StringBuilder();
		for (int i = 0; i < columns.size(); i++) {
			if (i > 0) {
				fields.append(",");
				placeholders.append(",");
			}
			fields.append(dialect.wrap(columns.get(i).name));
			placeholders.append("?");
		}
		String sql = "insert into " + table + " (" + fields + ") values (" + placeholders + ")";
		logger.debug("执行sql：{}", sql);
		int pending = 0;
		try (PreparedStatement ps = connection.prepareStatement(sql)) {
			for (Map<String, Object> row : rows) {
				for (int i = 0; i < columns.size(); i++) {
					Column column = columns.get(i);
					Object value = column.bindValue(row.get(column.name));
					if (value == null) {
						ps.setNull(i + 1, column.sqlType());
					} else {
						ps.setObject(i + 1, value);
					}
				}
				ps.addBatch();
				if (++pending >= batchSize) {
					ps.executeBatch();
					pending = 0;
				}
			}
			if (pending > 0) {
				ps.executeBatch();
			}
		}
	}

	/**
	 * 向上找到最近的输出节点，把它的输出内容转换成行
	 * <ul>
	 * <li>Map 或集合中的 Map：每个 Map 一行，键作为列名</li>
	 * <li>标量或标量集合：作为一列，与其余列按下标对齐，长度不足时重复最后一项（与执行SQL节点的批量规则一致）</li>
	 * </ul>
	 */
	private List<Map<String, Object>> readRows(SpiderNode node, Map<String, Object> variables) {
		SpiderNode outputNode = findOutputNode(node);
		if (outputNode == null) {
			logger.warn("未找到上游的输出节点");
			return Collections.emptyList();
		}
		List<Map<String, Object>> rows = new ArrayList<>();
		Map<String, List<Object>> scalarColumns = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : readOutputs(outputNode, variables).entrySet()) {
			Object value = entry.getValue();
			if (value == null) {
				continue;
			}
			if (value instanceof Map) {
				rows.add(toRow((Map<?, ?>) value));
				continue;
			}
			List<Object> values = new ArrayList<>();
			if (value instanceof Collection || value.getClass().isArray()) {
				for (Object item : toCollection(value)) {
					if (item instanceof Map) {
						rows.add(toRow((Map<?, ?>) item));
					} else {
						values.add(item);
					}
				}
			} else {
				values.add(value);
			}
			if (!values.isEmpty()) {
				scalarColumns.put(entry.getKey(), values);
			}
		}
		if (rows.isEmpty()) {
			int size = 0;
			for (List<Object> values : scalarColumns.values()) {
				size = Math.max(size, values.size());
			}
			for (int i = 0; i < size; i++) {
				rows.add(new LinkedHashMap<String, Object>());
			}
		}
		for (Map.Entry<String, List<Object>> column : scalarColumns.entrySet()) {
			List<Object> values = column.getValue();
			String name = column.getKey();
			for (int i = 0; i < rows.size(); i++) {
				Map<String, Object> row = rows.get(i);
				// 输出项与集合里每个元素的键重名时，保留每行自己的值，公共值只补空
				if (row.containsKey(name)) {
					continue;
				}
				// 值不够时用最后一项补齐，避免整列为空
				row.put(name, values.get(Math.min(i, values.size() - 1)));
			}
		}
		return rows;
	}

	/**
	 * 输出节点的内容：勾选“输出全部参数”时取流程变量，否则逐个求值配置的输出项
	 */
	private Map<String, Object> readOutputs(SpiderNode outputNode, Map<String, Object> variables) {
		Map<String, Object> outputs = new LinkedHashMap<>();
		if ("1".equals(outputNode.getStringJsonValue(OutputExecutor.OUTPUT_ALL))) {
			for (Map.Entry<String, Object> entry : variables.entrySet()) {
				String name = entry.getKey();
				if ("ex".equals(name)) {
					continue;
				}
				Object value = entry.getValue();
				if (value instanceof SpiderResponse) {
					outputs.put(name + ".html", ((SpiderResponse) value).getHtml());
				} else {
					outputs.put(name, value);
				}
			}
			return outputs;
		}
		List<Map<String, String>> items;
		try {
			items = outputNode.getListJsonValue(OutputExecutor.OUTPUT_NAME, OutputExecutor.OUTPUT_VALUE);
		} catch (ArrayIndexOutOfBoundsException e) {
			logger.warn("输出节点的输出项与输出值数量不一致");
			return Collections.emptyMap();
		}
		for (Map<String, String> item : items) {
			String name = StringUtils.trimToNull(item.get(OutputExecutor.OUTPUT_NAME));
			if (name == null) {
				continue;
			}
			try {
				outputs.put(name, ExpressionUtils.execute(item.get(OutputExecutor.OUTPUT_VALUE), variables));
			} catch (Exception e) {
				logger.error("获取输出项{}出错,异常信息:{}", name, e.getMessage());
			}
		}
		return outputs;
	}

	/**
	 * 逐层向上查找最近的输出节点，自动入库只消费离它最近的那一个
	 */
	private SpiderNode findOutputNode(SpiderNode node) {
		Set<SpiderNode> visited = new HashSet<>();
		List<SpiderNode> layer = new ArrayList<>(node.getPrevNodes());
		while (!layer.isEmpty()) {
			List<SpiderNode> next = new ArrayList<>();
			for (SpiderNode item : layer) {
				if (item == node || !visited.add(item)) {
					continue;
				}
				if ("output".equals(item.getStringJsonValue("shape"))) {
					return item;
				}
				next.addAll(item.getPrevNodes());
			}
			layer = next;
		}
		return null;
	}

	private Map<String, Object> toRow(Map<?, ?> map) {
		Map<String, Object> row = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			if (entry.getKey() != null) {
				row.put(toText(entry.getKey()), entry.getValue());
			}
		}
		return row;
	}

	private Collection<?> toCollection(Object value) {
		if (value instanceof Collection) {
			return (Collection<?>) value;
		}
		List<Object> values = new ArrayList<>();
		for (int i = 0, length = Array.getLength(value); i < length; i++) {
			values.add(Array.get(value, i));
		}
		return values;
	}

	/**
	 * 统一各行的列名（清洗成合法标识符）并推断每列的类型，返回参与建表和插入的列
	 */
	private List<Column> normalizeRows(List<Map<String, Object>> rows) {
		Map<String, String> aliases = new LinkedHashMap<>();
		Map<String, Column> columns = new LinkedHashMap<>();
		for (Map<String, Object> row : rows) {
			Map<String, Object> normalized = new LinkedHashMap<>();
			Iterator<Map.Entry<String, Object>> iterator = row.entrySet().iterator();
			while (iterator.hasNext()) {
				Map.Entry<String, Object> entry = iterator.next();
				String name = aliases.get(entry.getKey());
				if (name == null) {
					name = safeColumnName(entry.getKey(), columns.keySet());
					aliases.put(entry.getKey(), name);
				}
				if (name == null) {
					continue;
				}
				Column column = columns.get(name);
				if (column == null) {
					column = new Column(name);
					columns.put(name, column);
				}
				column.addValue(entry.getValue());
				normalized.put(name, entry.getValue());
			}
			row.clear();
			row.putAll(normalized);
		}
		List<Column> result = new ArrayList<>(columns.values());
		for (Map<String, Object> row : rows) {
			for (Column column : result) {
				if (!row.containsKey(column.name)) {
					row.put(column.name, null);
				}
			}
		}
		return result;
	}

	/**
	 * 把输出项名称清洗成合法列名，无法清洗时返回 null 表示丢弃该列
	 */
	private String safeColumnName(String raw, Set<String> used) {
		String name = raw.trim();
		if (!IDENTIFIER.matcher(name).matches()) {
			name = ILLEGAL_CHARACTER.matcher(name).replaceAll("_").replaceAll("_{2,}", "_").replaceAll("^_|_$", "");
			if (StringUtils.isNotEmpty(name) && !Character.isLetter(name.charAt(0)) && name.charAt(0) != '_') {
				name = "c_" + name;
			}
			name = StringUtils.substring(name, 0, 64);
			if (!IDENTIFIER.matcher(name).matches()) {
				logger.warn("忽略无法转换为列名的输出项:{}", raw);
				return null;
			}
		}
		if (!used.contains(name)) {
			return name;
		}
		// 不同输出项清洗后重名时加序号区分
		for (int i = 2; i < 100; i++) {
			String candidate = StringUtils.substring(name, 0, 60) + "_" + i;
			if (!used.contains(candidate)) {
				return candidate;
			}
		}
		logger.warn("忽略无法转换为列名的输出项:{}", raw);
		return null;
	}

	private static String toText(Object value) {
		if (value == null) {
			return "";
		}
		if (value instanceof SpiderResponse) {
			return ((SpiderResponse) value).getHtml();
		}
		if (value instanceof Date) {
			return DateFormatUtils.format((Date) value, "yyyy-MM-dd HH:mm:ss");
		}
		if (value instanceof LocalDateTime) {
			return ((LocalDateTime) value).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
		}
		if (value instanceof LocalDate) {
			return ((LocalDate) value).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
		}
		return String.valueOf(value);
	}

	/**
	 * 一列的类型信息：按全部取值归类，只有一种类型时按该类型建列，混合类型退化成字符串列
	 */
	private static class Column {

		private final String name;

		private int kinds = KIND_NULL;

		private int length;

		Column(String name) {
			this.name = name;
		}

		void addValue(Object value) {
			if (value == null) {
				return;
			}
			if (value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte) {
				kinds |= KIND_INTEGRAL;
			} else if (value instanceof Float || value instanceof Double || value instanceof BigDecimal || value instanceof BigInteger) {
				kinds |= KIND_DECIMAL;
			} else if (value instanceof Boolean) {
				kinds |= KIND_BOOLEAN;
			} else if (value instanceof Date || value instanceof LocalDateTime || value instanceof LocalDate) {
				kinds |= KIND_TEMPORAL;
			} else {
				kinds |= KIND_TEXT;
				length = Math.max(length, toText(value).length() * 2);
			}
		}

		String type() {
			switch (kinds) {
			case KIND_INTEGRAL:
				return "bigint";
			case KIND_DECIMAL:
			case KIND_INTEGRAL | KIND_DECIMAL:
				return "decimal";
			case KIND_BOOLEAN:
				return "boolean";
			case KIND_TEMPORAL:
				return "datetime";
			default:
				return length > VARCHAR_LIMIT ? "text" : "varchar";
			}
		}

		int varcharLength() {
			return Math.min(Math.max(length, 255), VARCHAR_LIMIT);
		}

		int sqlType() {
			switch (type()) {
			case "bigint":
				return Types.BIGINT;
			case "decimal":
				return Types.DECIMAL;
			case "boolean":
				return Types.BOOLEAN;
			case "datetime":
				return Types.TIMESTAMP;
			default:
				return Types.VARCHAR;
			}
		}

		Object bindValue(Object value) {
			if (value == null) {
				return null;
			}
			switch (type()) {
			case "bigint":
				return ((Number) value).longValue();
			case "decimal":
				return value instanceof BigDecimal ? value : new BigDecimal(value.toString());
			case "boolean":
				return value;
			case "datetime":
				return toTimestamp(value);
			default:
				return toText(value);
			}
		}

		private Object toTimestamp(Object value) {
			if (value instanceof Timestamp) {
				return value;
			}
			if (value instanceof Date) {
				return new Timestamp(((Date) value).getTime());
			}
			if (value instanceof LocalDateTime) {
				return Timestamp.valueOf((LocalDateTime) value);
			}
			if (value instanceof LocalDate) {
				return Timestamp.valueOf(((LocalDate) value).atStartOfDay());
			}
			return value;
		}
	}

	/**
	 * 建表语句按连接的实际数据库方言生成，标识符用该库的引用符包裹
	 */
	private static class Dialect {

		private final String product;

		private final String quote;

		/**
		 * 建表语句里能不能带库名/模式名，按驱动自述来判断，不能带的靠 setCatalog/setSchema 兜底
		 */
		private final boolean catalogInDDL;

		private final boolean schemaInDDL;

		Dialect(DatabaseMetaData metaData) throws SQLException {
			this.product = StringUtils.lowerCase(StringUtils.trimToEmpty(metaData.getDatabaseProductName()));
			String value = metaData.getIdentifierQuoteString();
			this.quote = StringUtils.isBlank(value) ? "" : value;
			this.catalogInDDL = metaData.supportsCatalogsInTableDefinitions();
			this.schemaInDDL = metaData.supportsSchemasInTableDefinitions();
		}

		String wrap(String identifier) {
			return quote + identifier + quote;
		}

		/**
		 * 拼出建表与插入用的表名，MySQL 走 `库`.`表`，Oracle/PG/SQLServer 走 `模式`.`表`
		 */
		String qualify(String catalog, String schema, String tableName) {
			StringBuilder name = new StringBuilder();
			if (catalog != null && catalogInDDL) {
				name.append(wrap(catalog)).append('.');
			}
			if (schema != null && schemaInDDL) {
				name.append(wrap(schema)).append('.');
			}
			return name.append(wrap(tableName)).toString();
		}

		boolean existsTable(Connection connection, String catalog, String schema, String tableName) throws SQLException {
			DatabaseMetaData metaData = connection.getMetaData();
			// Oracle、SQLServer 的元数据里表名通常是大写，MySQL 部分系统大小写敏感，两种写法都查一次
			try (ResultSet rs = metaData.getTables(catalog, schema, tableName, new String[] { "TABLE" })) {
				if (rs.next()) {
					return true;
				}
			}
			try (ResultSet rs = metaData.getTables(catalog, schema, tableName.toUpperCase(), new String[] { "TABLE" })) {
				return rs.next();
			}
		}

		void createTable(Connection connection, String table, List<Column> columns) throws SQLException {
			StringBuilder ddl = new StringBuilder("create table ").append(table).append(" (");
			for (Column column : columns) {
				ddl.append(wrap(column.name)).append(' ').append(columnType(column)).append(',');
			}
			ddl.setLength(ddl.length() - 1);
			ddl.append(")");
			if (isMySQL()) {
				// 表名和列名可能含中文，必须显式指定 utf8mb4
				ddl.append(" engine=InnoDB default charset=utf8mb4");
			}
			logger.debug("建表语句：{}", ddl);
			try (Statement statement = connection.createStatement()) {
				statement.executeUpdate(ddl.toString());
			}
		}

		private boolean isMySQL() {
			return product.contains("mysql") || product.contains("mariadb");
		}

		private String columnType(Column column) {
			String type = column.type();
			if (product.contains("oracle")) {
				switch (type) {
				case "bigint":
					return "number(19)";
				case "decimal":
					return "number(20,6)";
				case "boolean":
					return "number(1)";
				case "datetime":
					return "timestamp";
				case "text":
					return "clob";
				default:
					return "varchar2(" + Math.min(column.varcharLength(), 4000) + " char)";
				}
			}
			if (product.contains("sql server")) {
				switch (type) {
				case "bigint":
					return "bigint";
				case "decimal":
					return "decimal(20,6)";
				case "boolean":
					return "bit";
				case "datetime":
					return "datetime2";
				case "text":
					return "nvarchar(max)";
				default:
					return "nvarchar(" + Math.min(column.varcharLength(), 4000) + ")";
				}
			}
			if (product.contains("postgre")) {
				switch (type) {
				case "bigint":
					return "bigint";
				case "decimal":
					return "numeric(20,6)";
				case "boolean":
					return "boolean";
				case "datetime":
					return "timestamp";
				case "text":
					return "text";
				default:
					return "varchar(" + column.varcharLength() + ")";
				}
			}
			// 其余按 MySQL 语法处理
			switch (type) {
			case "bigint":
				return "bigint";
			case "decimal":
				return "decimal(20,6)";
			case "boolean":
				return "tinyint(1)";
			case "datetime":
				return "datetime";
			case "text":
				return "text";
			default:
				return "varchar(" + column.varcharLength() + ")";
			}
		}
	}
}
