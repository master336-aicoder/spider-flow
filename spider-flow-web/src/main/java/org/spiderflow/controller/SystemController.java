package org.spiderflow.controller;

import java.util.List;
import java.util.Map;

import org.spiderflow.core.service.SpiderFlowService;
import org.spiderflow.core.service.SystemParamService;
import org.spiderflow.core.service.SystemStatusService;
import org.spiderflow.model.JsonBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统设置：运行参数动态修改 + 系统状态
 */
@RestController
@RequestMapping("/system")
public class SystemController {

	@Autowired
	private SystemParamService systemParamService;

	@Autowired
	private SystemStatusService systemStatusService;

	@Autowired
	private SpiderFlowService spiderFlowService;

	@RequestMapping("/params")
	public JsonBean<List<Map<String, Object>>> params() {
		return new JsonBean<>(systemParamService.listParams());
	}

	@RequestMapping("/params/save")
	public JsonBean<Void> saveParams(@RequestBody Map<String, String> values) {
		try {
			systemParamService.save(values);
			if (values.containsKey("spider.job.enable")) {
				spiderFlowService.refreshSchedules();
			}
			return new JsonBean<>(1, "保存成功，参数已立即生效");
		} catch (IllegalArgumentException e) {
			return new JsonBean<>(0, e.getMessage());
		}
	}

	@RequestMapping("/status")
	public JsonBean<Map<String, Object>> status() {
		return new JsonBean<>(systemStatusService.collect());
	}

}
