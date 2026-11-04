package org.spiderflow.controller;

import java.io.Reader;
import java.sql.Connection;
import java.sql.Clob;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spiderflow.core.utils.DataSourceUtils;
import org.spiderflow.model.JsonBean;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据源的库、模式、表等元数据查询与SQL执行，供数据源详情页和自动入库节点的下拉使用
 */
@RestController
@RequestMapping("/database")
public class DatabaseController {

	/**
	 * 表列表与SQL查询结果的单次返回上限，避免一次拉爆浏览器
	 */
	private static final int MAX_ROWS = 500;

	/**
	 * SQL执行超时秒数
	 */
	private static final int QUERY_TIMEOUT = 30;

	private static final Logger logger = LoggerFactory.getLogger(DatabaseController.class);

	@RequestMapping("/catalogs")
	public JsonBean<List<String>> catalogs(String datasourceId) {
		return withConnection(datasourceId, null, null, "读取数据库列表失败", (connection, meta, catalog, schema) -> toStringList(meta.getCatalogs()));
	}

	@RequestMapping("/schemas")
	public JsonBean<List<String>> schemas(String datasourceId, String catalog) {
		return withConnection(datasourceId, catalog, null, "读取模式列表失败", (connection, meta, useCatalog, schema) -> toStringList(meta.getSchemas(useCatalog, null)));
	}

	@RequestMapping("/tables")
	public JsonBean<List<Map<String, Object>>> tables(String datasourceId, String catalog, String schema, String tableName) {
		return withConnection(datasourceId, catalog, schema, "读取表列表失败", (connection, meta, useCatalog, useSchema) -> {
			List<Map<String, Object>> tables = new ArrayList<>();
			try (ResultSet rs = meta.getTables(useCatalog, useSchema, likePattern(tableName), new String[] { "TABLE", "VIEW" })) {
				while (rs.next() && tables.size() < MAX_ROWS) {
					Map<String, Object> table = new LinkedHashMap<>();
					table.put("name", rs.getString("TABLE_NAME"));
					table.put("type", rs.getString("TABLE_TYPE"));
					table.put("remarks", rs.getString("REMARKS"));
					tables.add(table);
				}
			}
			return tables;
		});
	}

	@RequestMapping("/columns")
	public JsonBean<Map<String, Object>> columns(String datasourceId, String catalog, String schema, String tableName) {
		if (StringUtils.isBlank(tableName)) {
			return new JsonBean<>(0, "表名不能为空");
		}
		return withConnection(datasourceId, catalog, schema, "读取表结构失败", (connection, meta, useCatalog, useSchema) -> {
			List<String> primaryKeys = new ArrayList<>();
			try (ResultSet rs = meta.getPrimaryKeys(useCatalog, useSchema, tableName)) {
				while (rs.next()) {
					primaryKeys.add(rs.getString("COLUMN_NAME"));
				}
			}
			List<Map<String, Object>> columns = new ArrayList<>();
			try (ResultSet rs = meta.getColumns(useCatalog, useSchema, tableName, "%")) {
				while (rs.next()) {
					Map<String, Object> column = new LinkedHashMap<>();
					String name = rs.getString("COLUMN_NAME");
					column.put("name", name);
					column.put("typeName", rs.getString("TYPE_NAME"));
					column.put("size", rs.getInt("COLUMN_SIZE"));
					column.put("decimalDigits", rs.getInt("DECIMAL_DIGITS"));
					column.put("nullable", rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable);
					column.put("defaultValue", rs.getString("COLUMN_DEF"));
					column.put("remarks", rs.getString("REMARKS"));
					column.put("primaryKey", primaryKeys.contains(name));
					columns.add(column);
				}
			}
			Map<String, Object> detail = new LinkedHashMap<>();
			detail.put("columns", columns);
			detail.put("primaryKeys", primaryKeys);
			detail.put("indexes", readIndexes(meta, useCatalog, useSchema, tableName));
			return detail;
		});
	}

	/**
	 * 执行一条SQL：查询返回列名与数据行，其余语句返回影响行数
	 */
	@RequestMapping("/execute")
	public JsonBean<Map<String, Object>> execute(String datasourceId, String catalog, String schema, String sql) {
		if (StringUtils.isBlank(sql)) {
			return new JsonBean<>(0, "SQL不能为空");
		}
		return withConnection(datasourceId, catalog, schema, "执行SQL失败", (connection, meta, useCatalog, useSchema) -> {
			Map<String, Object> result = new LinkedHashMap<>();
			try (Statement statement = connection.createStatement()) {
				statement.setQueryTimeout(QUERY_TIMEOUT);
				statement.setMaxRows(MAX_ROWS);
				if (statement.execute(sql)) {
					try (ResultSet rs = statement.getResultSet()) {
						ResultSetMetaData metaData = rs.getMetaData();
						int count = metaData.getColumnCount();
						List<String> columns = new ArrayList<>(count);
						for (int i = 1; i <= count; i++) {
							columns.add(metaData.getColumnLabel(i));
						}
						List<List<String>> rows = new ArrayList<>();
						boolean truncated = false;
						while (rs.next()) {
							if (rows.size() >= MAX_ROWS) {
								// 驱动可能不遵守 setMaxRows，这里再兜一次底
								truncated = true;
								break;
							}
							List<String> row = new ArrayList<>(count);
							for (int i = 1; i <= count; i++) {
								row.add(toText(rs.getObject(i)));
							}
							rows.add(row);
						}
						result.put("type", "query");
						result.put("columns", columns);
						result.put("rows", rows);
						result.put("truncated", truncated);
					}
				} else {
					result.put("type", "update");
					result.put("count", statement.getUpdateCount());
				}
			}
			return result;
		});
	}

	private interface MetaAction<T> {

		T run(Connection connection, DatabaseMetaData meta, String catalog, String schema) throws Exception;
	}

	/**
	 * 统一取连接、切换库与模式、包装异常，并把本次过滤用的库/模式回传给调用方
	 */
	private <T> JsonBean<T> withConnection(String datasourceId, String catalog, String schema, String message, MetaAction<T> action) {
		if (StringUtils.isBlank(datasourceId)) {
			return new JsonBean<>(0, "数据源ID不能为空");
		}
		DataSource dataSource = DataSourceUtils.getDataSource(datasourceId);
		if (dataSource == null) {
			return new JsonBean<>(0, "数据源不存在");
		}
		try (Connection connection = dataSource.getConnection()) {
			String previousCatalog = readCatalog(connection);
			String previousSchema = readSchema(connection);
			try {
				useCatalog(connection, catalog);
				useSchema(connection, schema);
				// DatabaseMetaData 里 null 是“不限定”，会把所有库的同名表混在一起，未指定时按会话当前的库/模式过滤
				String filterCatalog = StringUtils.isNotBlank(catalog) ? catalog.trim() : previousCatalog;
				String filterSchema = StringUtils.isNotBlank(schema) ? schema.trim() : previousSchema;
				return new JsonBean<>(action.run(connection, connection.getMetaData(), nullIfBlank(filterCatalog), nullIfBlank(filterSchema)));
			} finally {
				restore(connection, previousCatalog, previousSchema);
			}
		} catch (Exception e) {
			logger.error("{},数据源:{}", message, datasourceId, e);
			return new JsonBean<>(0, message + "：" + e.getMessage());
		}
	}

	/**
	 * 驱动不支持切换时忽略，元数据接口仍按参数显式过滤
	 */
	private void useCatalog(Connection connection, String catalog) {
		if (StringUtils.isNotBlank(catalog)) {
			try {
				connection.setCatalog(catalog.trim());
			} catch (SQLException e) {
				logger.debug("切换数据库[{}]失败:{}", catalog, e.getMessage());
			}
		}
	}

	private void useSchema(Connection connection, String schema) {
		if (StringUtils.isNotBlank(schema)) {
			try {
				connection.setSchema(schema.trim());
			} catch (SQLException e) {
				logger.debug("切换模式[{}]失败:{}", schema, e.getMessage());
			}
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

	/**
	 * 连接是池化的，会话切过库或模式就得还原，否则会污染后续取到这条连接的流程
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

	private List<Map<String, Object>> readIndexes(DatabaseMetaData meta, String catalog, String schema, String tableName) {
		List<Map<String, Object>> indexes = new ArrayList<>();
		try (ResultSet rs = meta.getIndexInfo(catalog, schema, tableName, false, false)) {
			while (rs.next() && indexes.size() < MAX_ROWS) {
				Map<String, Object> index = new LinkedHashMap<>();
				index.put("name", rs.getString("INDEX_NAME"));
				index.put("unique", !rs.getBoolean("NON_UNIQUE"));
				index.put("column", rs.getString("COLUMN_NAME"));
				index.put("ascending", "A".equals(rs.getString("ASC_OR_DESC")));
				indexes.add(index);
			}
		} catch (SQLException e) {
			// 视图或个别驱动不支持索引查询，索引只是附加信息
			logger.debug("读取表[{}]索引失败:{}", tableName, e.getMessage());
		}
		return indexes;
	}

	/**
	 * 表单会传空串，DatabaseMetaData 把空串当成精确匹配的库名，必须转成 null 才表示“不限定”
	 */
	private String nullIfBlank(String value) {
		return StringUtils.isBlank(value) ? null : value.trim();
	}

	/**
	 * 关键字为空匹配全部，否则按包含匹配
	 */
	private String likePattern(String tableName) {
		if (StringUtils.isBlank(tableName)) {
			return "%";
		}
		return "%" + tableName.trim() + "%";
	}

	private List<String> toStringList(ResultSet rs) throws Exception {
		List<String> result = new ArrayList<>();
		try (ResultSet set = rs) {
			while (set.next()) {
				result.add(set.getString(1));
			}
		}
		return result;
	}

	/**
	 * 结果值统一转成字符串，二进制和大文本只给摘要，避免JSON体积失控
	 */
	private static String toText(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof byte[]) {
			return "<二进制 " + ((byte[]) value).length + " 字节>";
		}
		if (value instanceof Clob) {
			try {
				long length = ((Clob) value).length();
				String text = ((Clob) value).getSubString(1, (int) Math.min(length, 2000));
				return length > 2000 ? text + "..." : text;
			} catch (SQLException e) {
				return "<大文本>";
			}
		}
		if (value instanceof Reader) {
			return "<字符流>";
		}
		return String.valueOf(value);
	}

}
