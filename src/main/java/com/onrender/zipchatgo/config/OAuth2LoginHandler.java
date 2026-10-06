package com.onrender.zipchatgo.config;

import com.onrender.zipchatgo.member.CustomOAuth2UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 소셜 로그인 결과 처리.
 * 성공: 세션 "loginMemberId" 설정, 게스트 세션 제거, 원래 목적지(없으면 /)로 이동
 * 실패: /login?oauthError=코드 로 이동
 */
@Component
public class OAuth2LoginHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

	public static final String SESSION_REDIRECT = "oauthRedirect";

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException {
		Object principal = authentication.getPrincipal();
		Object idAttr = (principal instanceof OAuth2User u)
				? u.getAttributes().get(CustomOAuth2UserService.ATTR_MEMBER_ID)
				: null;

		if (!(idAttr instanceof Number memberId)) {
			response.sendRedirect(request.getContextPath() + "/login?oauthError="
					+ CustomOAuth2UserService.ERR_INVALID_USER);
			return;
		}

		HttpSession session = request.getSession(true);
		session.removeAttribute("guest");
		session.setAttribute("loginMemberId", memberId.longValue());

		String target = "/";
		Object saved = session.getAttribute(SESSION_REDIRECT);
		session.removeAttribute(SESSION_REDIRECT);
		if (saved instanceof String s && isSafePath(s)) {
			target = s;
		}
		response.sendRedirect(request.getContextPath() + target);
	}

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws IOException {
		String code = "oauth_failed";
		if (exception instanceof OAuth2AuthenticationException oe && oe.getError() != null) {
			String c = oe.getError().getErrorCode();
			if (c != null && c.matches("[a-z_]{1,50}")) {
				code = c;
			}
		}
		HttpSession session = request.getSession(false);
		if (session != null) {
			session.removeAttribute(SESSION_REDIRECT);
		}
		response.sendRedirect(request.getContextPath() + "/login?oauthError="
				+ URLEncoder.encode(code, StandardCharsets.UTF_8));
	}

	/** 내부 경로만 허용 (open redirect 차단) */
	static boolean isSafePath(String p) {
		return p != null && p.startsWith("/") && !p.startsWith("//")
				&& !p.contains("\\") && !p.contains("\r") && !p.contains("\n");
	}
}
