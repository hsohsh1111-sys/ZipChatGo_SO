package com.onrender.zipchatgo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Thymeleaf 페이지 이동을 담당하는 Controller.
 * templates 폴더의 HTML과 브라우저 URL을 연결한다.
 *
 * /support/* 는 support.SupportPageController 로 이관하여 여기서는 제거함(중복 매핑 방지).
 */
@Controller
public class PageController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/member/login")
    public String login(Model model) {
        model.addAttribute("defaultTab", "login");
        return "member/auth";
    }

    @GetMapping("/login")
    public String loginAlias(Model model) {
        model.addAttribute("defaultTab", "login");
        return "member/auth";
    }

    // 계정 설정(탈퇴) - 비로그인/게스트 처리는 페이지 스크립트가 /api/auth/me 로 판단
    @GetMapping("/account")
    public String account() {
        return "member/account";
    }

    @GetMapping("/member/signup")
    public String signup(Model model) {
        model.addAttribute("defaultTab", "signup");
        return "member/auth";
    }

    @GetMapping("/join")
    public String joinAlias(Model model) {
        model.addAttribute("defaultTab", "signup");
        return "member/auth";
    }

    // /property/map 은 map.MapPageController 로 이관됨(중복 정의 방지)

    @GetMapping("/property/register")
    public String propertyRegister() {
        return "property/register";
    }

    @GetMapping("/properties/register")
    public String propertiesRegister() {
        return "property/register";
    }

    @GetMapping("/market/trend")
    public String marketTrend() {
        return "market/trend";
    }

    @GetMapping("/market/sale")
    public String marketSale() {
        return "market/sale";
    }

    @GetMapping("/market/jeonse")
    public String marketJeonse() {
        return "market/jeonse";
    }

    @GetMapping("/market/volume")
    public String marketVolume() {
        return "market/volume";
    }

    @GetMapping("/market/region")
    public String marketRegion() {
        return "market/region";
    }

    @GetMapping("/market/region-flow")
    public String marketRegionFlow() {
        return "market/region-flow";
    }

    @GetMapping("/market/rate")
    public String marketRate() {
        return "market/rate";
    }

    @GetMapping("/market/school")
    public String marketSchool() {
        return "market/school";
    }

    @GetMapping("/market/traffic")
    public String marketTraffic() {
        return "market/traffic";
    }

    @GetMapping("/market/cost")
    public String marketCost() {
        return "market/cost";
    }

    @GetMapping("/market/ai-report")
    public String marketAiReport() {
        return "market/ai-report";
    }

    // =========================
    // 고객지원
    // =========================

    @GetMapping("/support/contact")
    public String contact() {
        return "support/contact";
    }

    @GetMapping("/support/guide")
    public String guide() {
        return "support/guide";
    }

    @GetMapping("/support/live")
    public String live() {
        return "support/live";
    }

    @GetMapping("/support/modelhouse")
    public String modelhouse() {
        return "support/modelhouse";
    }

    @GetMapping("/support/notice")
    public String notice() {
        return "support/notice";
    }

    @GetMapping("/support/team")
    public String team() {
        return "support/team";
    }

    @GetMapping("/support/service")
    public String supportService() {
        return "support/service";
    }

    // /about 경로 별칭
    @GetMapping("/about/service")
    public String aboutService() {
        return "support/service";
    }

    @GetMapping("/about/team")
    public String aboutTeam() {
        return "support/team";
    }

    // /content 경로 별칭
    @GetMapping("/content/modelhouse")
    public String contentModelhouse() {
        return "support/modelhouse";
    }

    // AdminInterceptor(/admin/**)가 접근 제어를 담당
    @GetMapping("/admin/properties")
    public String adminProperties() {
        return "admin/properties";
    }
}
