package com.onrender.zipchatgo.member;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Set;

/**
 * /oauth/start/{provider}?redirect=/some/path
 * 돌아올 경로를 세션에 저장한 뒤 Spring Security의 OAuth2 시작 URL로 보낸다.
 */
@Controller
public class OAuthStartController {

	private static final Set<String> PROVIDERS = Set.of("google", "naver", "kakao");
	private static final String SESSION_REDIRECT = "oauthRedirect";
	private static final String SESSION_SWITCH = "oauthSwitch";

	@GetMapping("/oauth/start/{provider}")
	public String start(@PathVariable String provider,
			@RequestParam(required = false) String redirect,
			@RequestParam(required = false) String switchAccount,
			HttpSession session) {
		if (!PROVIDERS.contains(provider)) {
			return "redirect:/login?oauthError=invalid_user";
		}
		if (isSafePath(redirect)) {
			session.setAttribute(SESSION_REDIRECT, redirect);
		} else {
			session.removeAttribute(SESSION_REDIRECT);
		}
		if ("1".equals(switchAccount)) {
			session.setAttribute(SESSION_SWITCH, true);
		} else {
			session.removeAttribute(SESSION_SWITCH);
		}
		return "redirect:/oauth2/authorization/" + provider;
	}

	/** 내부 경로만 허용 (open redirect 차단) */
	private static boolean isSafePath(String p) {
		return p != null && p.startsWith("/") && !p.startsWith("//")
				&& !p.contains("\\") && !p.contains("\r") && !p.contains("\n");
	}
}
