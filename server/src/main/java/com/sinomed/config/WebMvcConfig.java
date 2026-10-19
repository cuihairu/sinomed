package com.sinomed.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * 广告媒体静态托管：/media/** → sinomed.ads.upload-dir。
 * 生产环境由 Nginx 直接托管同目录（见 docs/server/deploy.md），
 * 本地开发没有 Nginx 时由 Spring 提供同等映射。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${sinomed.ads.upload-dir:data/ads}")
    private String uploadDir;

    @Value("${sinomed.ads.url-prefix:/media}")
    private String urlPrefix;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String absolute = Paths.get(uploadDir).toAbsolutePath().normalize().toUri().toString();
        if (!absolute.endsWith("/")) {
            absolute += "/";
        }
        registry.addResourceHandler(urlPrefix + "/**")
                .addResourceLocations(absolute);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 公众号粉丝 H5 绑定静态页（设计稿 §5.6）：设计稿定的 /h5/wx/bind 指到 classpath 静态文件
        // （Spring 7 嵌套目录不自动补 index.html，显式 forward；SecurityConfig 放行 /h5/**）
        registry.addViewController("/h5/wx/bind").setViewName("forward:/h5/wx/bind/index.html");
    }
}
