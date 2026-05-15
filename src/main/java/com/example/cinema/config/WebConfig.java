package com.example.cinema.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // Forward the root path to the SPA entry point. Do **not** forward
        // '/login' or '/post-login' here so that the dedicated login pages for
        // members and employees can be served by their respective controllers.
        // See AuthPageController for member and employee login mappings.
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 設定靜態資源路徑
        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/");

        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/");

        registry.addResourceHandler("/images/**")
                .addResourceLocations("classpath:/static/images/");

        // Vue Router History Mode 支援：所有未匹配路由都返回 index.html
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        // Do not serve SPA index.html under these server-side areas.
                        // It can cause confusing behavior (and CSP issues) when users hit /member/** or /employee/**.
                        if (resourcePath != null) {
                            String p = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
                            if (p.startsWith("member/") || p.startsWith("employee/") || p.startsWith("api/")) {
                                return null;
                            }
                            Resource requestedResource = location.createRelative(resourcePath);
                            if (requestedResource.exists() && requestedResource.isReadable()) {
                                return requestedResource;
                            }
                            // Do not fallback SPA entry for protected deep links.
                            // Protected routes are handled by SpaRouteGuardController to enforce session checks.
                            // Unknown paths should keep normal 404 behavior instead of always returning index.html.
                            if (isSpaClientRoute(p)) {
                                return new ClassPathResource("/static/index.html");
                            }
                            return null;
                        }
                        return null;
                    }

                    private boolean isSpaClientRoute(String path) {
                        // Keep explicit extension point for future public SPA routes.
                        // Current policy: no anonymous deep-link fallback from resource handler.
                        return false;
                    }
                });
    }
}
