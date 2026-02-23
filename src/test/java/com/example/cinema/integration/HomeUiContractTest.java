package com.example.cinema.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("首頁 UI 合約測試")
class HomeUiContractTest {

    @Test
    @DisplayName("主標語應只在 hero 標題區顯示一次，不可出現在每張海報內容")
    void shouldKeepHeroTaglineSinglePlacement() throws IOException {
        String sharedJs = read("src/main/resources/static/js/modules/spa-shared.js");

        assertTrue(sharedJs.contains("<p class=\"hero-tagline\">每個顧客都可以睡得很安穩</p>"));
        assertTrue(sharedJs.contains("<p v-if=\"slide.subtitle\" class=\"hero-slide-subtitle\">{{ slide.subtitle }}</p>"));
        assertFalse(sharedJs.contains("style=\"color: #0044BB;\""));
        assertEquals(1, countOccurrences(sharedJs, "每個顧客都可以睡得很安穩"));
    }

    @Test
    @DisplayName("Hero/Carousel 應使用集中化 CSS 變數，避免樣式分散")
    void shouldUseCentralizedHeroCssVariables() throws IOException {
        String css = read("src/main/resources/static/css/styles.css");
        assertTrue(css.contains("--hero-carousel-height"));
        assertTrue(css.contains("--hero-slide-crop-scale"));
        assertTrue(css.contains("--hero-tagline-color"));
        assertTrue(css.contains("height: var(--hero-carousel-height);"));
        assertTrue(css.contains("transform: scale(var(--hero-slide-crop-scale));"));
        assertTrue(css.contains(".hero-tagline"));
    }

    @Test
    @DisplayName("首頁輪播應支援10部電影而非只顯示5部")
    void shouldSupportTenMoviesInCarousel() throws IOException {
        String appJs = read("src/main/resources/static/js/app.js");
        String appPagesJs = read("src/main/resources/static/js/modules/app-pages.js");
        String combined = appJs + "\n" + appPagesJs;
        assertTrue(combined.contains(
                "const preferredOrder = ['mv-01', 'mv-02', 'mv-03', 'mv-04', 'mv-05', 'mv-06', 'mv-07', 'mv-08', 'mv-09', 'mv-10'];"));
        assertTrue(combined.contains(".slice(0, 10);"));
    }

    @Test
    @DisplayName("登入與員工頁面應避免行內 script/event handler")
    void shouldAvoidInlineScriptsAndInlineHandlers() throws IOException {
        List<String> templates = List.of(
                "src/main/resources/templates/member-login.html",
                "src/main/resources/templates/employee-login.html",
                "src/main/resources/templates/member.html",
                "src/main/resources/templates/employee.html",
                "src/main/resources/templates/admin.html",
                "src/main/resources/templates/manager.html",
                "src/main/resources/templates/it.html",
                "src/main/resources/templates/employee-checklist.html",
                "src/main/resources/templates/admin-showtimes.html");

        for (String template : templates) {
            String html = read(template);
            assertFalse(html.contains("<script>"), "inline <script> should not exist: " + template);
            assertFalse(html.contains("onchange="), "inline onchange should not exist: " + template);
            assertTrue(html.contains("legacy-pages.js"), "legacy-pages module missing: " + template);
        }
    }

    @Test
    @DisplayName("首頁應由 vendor-loader 載入 Vue 套件，避免行內 onerror")
    void shouldUseVendorLoaderWithoutInlineOnerror() throws IOException {
        String indexHtml = read("src/main/resources/static/index.html");
        assertTrue(indexHtml.contains("/js/modules/vendor-loader.js"));
        assertFalse(indexHtml.contains("onerror="));
    }

    @Test
    @DisplayName("vendor-loader 應優先使用本機 vendor 檔案，不依賴外部 CDN")
    void shouldUseLocalWebjarsInsteadOfExternalCdn() throws IOException {
        String loaderJs = read("src/main/resources/static/js/modules/vendor-loader.js");
        assertTrue(loaderJs.contains("/js/vendor/vue.global.prod.js"));
        assertFalse(loaderJs.contains("unpkg.com"));
        assertFalse(loaderJs.contains("cdn.jsdelivr.net"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath), StandardCharsets.UTF_8);
    }

    private static int countOccurrences(String source, String target) {
        int count = 0;
        int index = 0;
        while (true) {
            index = source.indexOf(target, index);
            if (index < 0) {
                return count;
            }
            count++;
            index += target.length();
        }
    }
}
