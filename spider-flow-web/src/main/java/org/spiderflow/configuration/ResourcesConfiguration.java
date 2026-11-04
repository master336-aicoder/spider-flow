package org.spiderflow.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域配置：前端（frontend-vue）与接口同源部署，此处保留供外部调用方使用
 * @author Administrator
 *
 */
@Configuration
public class ResourcesConfiguration implements WebMvcConfigurer{

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**")
				.allowedOrigins("*")
				.allowedMethods("GET","POST","OPTIONS")
				.allowCredentials(true);
	}
}
