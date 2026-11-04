package org.spiderflow.core.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spiderflow.concurrent.SpiderFlowThreadPoolExecutor;
import org.spiderflow.core.model.SysConfig;
import org.spiderflow.core.mapper.SysConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统运行参数：application.properties 提供默认值，sp_sys_config 表保存覆盖值，修改后立即生效
 */
@Service
public class SystemParamService {

	private static final Logger logger = LoggerFactory.getLogger(SystemParamService.class);

	public static class ParamDefinition {
		private final String key;
		private final String label;
		private final String description;
		private final String fallback;
		private final String[] options;

		ParamDefinition(String key, String label, String description, String fallback, String... options) {
			this.key = key;
			this.label = label;
			this.description = description;
			this.fallback = fallback;
			this.options = options;
		}

		public String getKey() {
			return key;
		}

		public String getLabel() {
			return label;
		}

		public String getDescription() {
			return description;
		}

		public String getFallback() {
			return fallback;
		}

		public String[] getOptions() {
			return options;
		}
	}

	private static final List<ParamDefinition> DEFINITIONS = Collections.unmodifiableList(Arrays.asList(
		new ParamDefinition("spider.job.enable", "定时任务总开关", "关闭后 Quartz 触发不再执行任何爬虫任务，手动运行不受影响", "true", "true", "false"),
		new ParamDefinition("spider.workspace", "工作目录", "爬虫日志、历史 xml、布隆过滤器等文件的根目录，修改后新任务使用新目录", ""),
		new ParamDefinition("spider.thread.max", "最大线程数", "爬虫线程池上限，保存后立即调整现有线程池", "64"),
		new ParamDefinition("spider.thread.default", "默认线程数", "流程节点未单独设置线程数时使用的并发数", "8"),
		new ParamDefinition("spider.detect.dead-cycle", "死循环检测阈值", "测试运行时节点执行次数超过该阈值则判定为死循环并终止", "5000"),
		new ParamDefinition("spider.bloomfilter.capacity", "布隆过滤器容量", "URL 去重布隆过滤器的预期元素数量，仅对之后新建的过滤器生效", "5000000"),
		new ParamDefinition("spider.bloomfilter.error-rate", "布隆过滤器误判率", "URL 去重布隆过滤器的误判概率，仅对之后新建的过滤器生效", "0.00001"),
		new ParamDefinition("spider.notice.subject", "邮件通知主题", "流程通知邮件的主题前缀", "spider-flow流程通知")
	));

	private final Map<String, String> overrides = new ConcurrentHashMap<>();

	@Autowired
	private SysConfigMapper sysConfigMapper;

	@Autowired
	private Environment environment;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@PostConstruct
	public void init() {
		jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS `sp_sys_config` ("
			+ "`id` VARCHAR(50) NOT NULL,"
			+ "`config_key` VARCHAR(100) NOT NULL,"
			+ "`config_value` VARCHAR(500) DEFAULT NULL,"
			+ "`update_date` DATETIME DEFAULT NULL,"
			+ "PRIMARY KEY (`id`),"
			+ "UNIQUE KEY `uk_config_key` (`config_key`)"
			+ ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
		for (SysConfig config : sysConfigMapper.selectList(new QueryWrapper<SysConfig>())) {
			if (config.getConfigValue() != null && !"".equals(config.getConfigValue())) {
				overrides.put(config.getConfigKey(), config.getConfigValue());
			}
		}
		// 立即应用线程池上限，保证重启后覆盖值同样生效
		try {
			applyThreadMax();
		} catch (Exception e) {
			logger.warn("应用线程池参数失败：{}", e.getMessage());
		}
	}

	public String getString(String key) {
		String value = overrides.get(key);
		if (value != null) {
			return value;
		}
		return environment.getProperty(key);
	}

	public String getString(String key, String defaultValue) {
		String value = getString(key);
		return value == null || value.isEmpty() ? defaultValue : value;
	}

	public int getInteger(String key, int defaultValue) {
		return NumberUtils.toInt(getString(key), defaultValue);
	}

	public double getDouble(String key, double defaultValue) {
		return NumberUtils.toDouble(getString(key), defaultValue);
	}

	public boolean getBoolean(String key, boolean defaultValue) {
		String value = getString(key);
		return value == null || value.isEmpty() ? defaultValue : Boolean.parseBoolean(value);
	}

	public String getDefaultValue(ParamDefinition definition) {
		String value = environment.getProperty(definition.getKey());
		return value == null || value.isEmpty() ? definition.getFallback() : value;
	}

	public List<Map<String, Object>> listParams() {
		List<Map<String, Object>> result = new ArrayList<>();
		for (ParamDefinition definition : DEFINITIONS) {
			Map<String, Object> item = new LinkedHashMap<>();
			String defaultValue = getDefaultValue(definition);
			String value = overrides.containsKey(definition.getKey()) ? overrides.get(definition.getKey()) : defaultValue;
			item.put("key", definition.getKey());
			item.put("label", definition.getLabel());
			item.put("description", definition.getDescription());
			item.put("value", value);
			item.put("defaultValue", defaultValue);
			item.put("customized", overrides.containsKey(definition.getKey()));
			item.put("options", definition.getOptions().length == 0 ? null : definition.getOptions());
			result.add(item);
		}
		return result;
	}

	/**
	 * 保存参数，value 为空表示恢复为配置文件默认值
	 */
	public void save(Map<String, String> values) {
		for (Map.Entry<String, String> entry : values.entrySet()) {
			String key = entry.getKey();
			if (!containsKey(key)) {
				continue;
			}
			String value = entry.getValue() == null ? "" : entry.getValue().trim();
			validate(key, value);
			SysConfig exist = sysConfigMapper.selectOne(new QueryWrapper<SysConfig>().eq("config_key", key));
			if (exist == null) {
				if (!value.isEmpty()) {
					exist = new SysConfig();
					exist.setConfigKey(key);
					exist.setConfigValue(value);
					exist.setUpdateDate(new Date());
					sysConfigMapper.insert(exist);
					overrides.put(key, value);
				}
			} else {
				if (value.isEmpty()) {
					sysConfigMapper.deleteById(exist.getId());
					overrides.remove(key);
				} else {
					exist.setConfigValue(value);
					exist.setUpdateDate(new Date());
					sysConfigMapper.updateById(exist);
					overrides.put(key, value);
				}
			}
			applySideEffects(key);
		}
	}

	private boolean containsKey(String key) {
		for (ParamDefinition definition : DEFINITIONS) {
			if (definition.getKey().equals(key)) {
				return true;
			}
		}
		return false;
	}

	private void validate(String key, String value) {
		if (value.isEmpty()) {
			return;
		}
		if (key.endsWith(".thread.max") || key.endsWith(".thread.default") || key.endsWith(".detect.dead-cycle") || key.endsWith(".bloomfilter.capacity")) {
			if (NumberUtils.toInt(value, -1) <= 0) {
				throw new IllegalArgumentException(key + " 必须为正整数");
			}
		}
		if (key.endsWith(".error-rate")) {
			double rate = NumberUtils.toDouble(value, -1);
			if (rate <= 0 || rate >= 1) {
				throw new IllegalArgumentException(key + " 必须是 0-1 之间的小数");
			}
		}
		if (key.endsWith(".job.enable") && !"true".equals(value) && !"false".equals(value)) {
			throw new IllegalArgumentException(key + " 只能是 true 或 false");
		}
	}

	private void applySideEffects(String key) {
		if ("spider.thread.max".equals(key)) {
			applyThreadMax();
		}
	}

	private void applyThreadMax() {
		SpiderFlowThreadPoolExecutor executorInstance = SpiderFlowThreadPoolExecutor.getInstance();
		if (executorInstance != null) {
			executorInstance.resize(getInteger("spider.thread.max", 64));
		}
	}

}
