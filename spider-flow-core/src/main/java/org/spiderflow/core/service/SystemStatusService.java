package org.spiderflow.core.service;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.matchers.GroupMatcher;
import org.spiderflow.concurrent.SpiderFlowThreadPoolExecutor;
import org.spiderflow.core.mapper.SpiderFlowMapper;
import org.spiderflow.core.model.SpiderFlow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

/**
 * 运行时状态采集：JVM、内存、线程池与调度器，供系统设置页和工作台复用
 */
@Service
public class SystemStatusService {

	@Autowired
	private SystemParamService systemParamService;

	@Autowired
	private SpiderFlowMapper spiderFlowMapper;

	@Autowired
	private Scheduler scheduler;

	public Map<String, Object> collect() {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("jvm", jvmInfo());
		result.put("memory", memoryInfo());
		result.put("spider", spiderInfo());
		return result;
	}

	public Map<String, Object> jvmInfo() {
		RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
		OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
		ThreadMXBean threads = ManagementFactory.getThreadMXBean();
		Map<String, Object> jvm = new LinkedHashMap<>();
		jvm.put("javaVersion", System.getProperty("java.version"));
		jvm.put("jvmName", runtime.getVmName());
		jvm.put("osName", os.getName() + " " + os.getVersion());
		jvm.put("osArch", os.getArch());
		jvm.put("availableProcessors", os.getAvailableProcessors());
		jvm.put("uptime", runtime.getUptime());
		jvm.put("uptimeText", formatUptime(runtime.getUptime()));
		jvm.put("threadCount", threads.getThreadCount());
		jvm.put("startTime", runtime.getStartTime());
		return jvm;
	}

	public Map<String, Object> memoryInfo() {
		MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
		MemoryUsage heap = memory.getHeapMemoryUsage();
		MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("used", heap.getUsed());
		result.put("committed", heap.getCommitted());
		result.put("max", heap.getMax());
		result.put("nonUsed", nonHeap.getUsed());
		return result;
	}

	public Map<String, Object> spiderInfo() {
		Map<String, Object> spider = new LinkedHashMap<>();
		spider.put("jobEnable", String.valueOf(systemParamService.getBoolean("spider.job.enable", true)));
		spider.put("workspace", systemParamService.getString("spider.workspace"));
		spider.put("threadMax", systemParamService.getInteger("spider.thread.max", 64));
		spider.put("threadDefault", systemParamService.getInteger("spider.thread.default", 8));
		SpiderFlowThreadPoolExecutor pool = SpiderFlowThreadPoolExecutor.getInstance();
		spider.put("poolMaxThreads", pool == null ? -1 : pool.getMaxThreads());
		spider.put("poolActiveThreads", pool == null ? -1 : pool.getActiveThreads());
		spider.put("poolThreads", pool == null ? -1 : pool.getPoolThreads());
		spider.put("poolQueueSize", pool == null ? -1 : pool.getQueueSize());
		try {
			List<SpiderFlow> flows = spiderFlowMapper.selectList(new QueryWrapper<SpiderFlow>().select("id", "name", "enabled", "cron"));
			int scheduled = 0;
			for (SpiderFlow flow : flows) {
				if ("1".equals(flow.getEnabled()) && StringUtils.isNotEmpty(flow.getCron())) {
					scheduled++;
				}
			}
			spider.put("totalSpiders", flows.size());
			spider.put("runningJobs", scheduler.getCurrentlyExecutingJobs().size());
			spider.put("scheduledJobs", scheduled);
			spider.put("quartzJobCount", scheduler.getJobKeys(GroupMatcher.anyGroup()).size());
		} catch (SchedulerException e) {
			spider.put("runningJobs", -1);
		}
		return spider;
	}

	public String formatUptime(long millis) {
		long seconds = millis / 1000;
		long days = seconds / 86400;
		long hours = seconds % 86400 / 3600;
		long minutes = seconds % 3600 / 60;
		StringBuilder sb = new StringBuilder();
		if (days > 0) {
			sb.append(days).append(" 天 ");
		}
		if (hours > 0) {
			sb.append(hours).append(" 小时 ");
		}
		sb.append(minutes).append(" 分钟");
		return sb.toString();
	}

}
