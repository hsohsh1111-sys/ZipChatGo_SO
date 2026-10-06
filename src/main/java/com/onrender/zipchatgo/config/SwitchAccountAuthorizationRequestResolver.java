package com.onrender.zipchatgo.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 세션에 "oauthSwitch" 플래그가 있을 때만 계정 선택/재로그인 파라미터를 붙인다.
 * - GOOGLE: prompt=select_account
 * - NAVER : auth_type=reauthenticate
 * - KAKAO : prompt=login
 */
@Component
public class SwitchAccountAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

	public static final String SESSION_SWITCH = "oauthSwitch";

	private final DefaultOAuth2AuthorizationRequestResolver delegate;

	public SwitchAccountAuthorizationRequestResolver(ClientRegistrationRepository repo) {
		this.delegate = new DefaultOAuth2AuthorizationRequestResolver(repo, "/oauth2/authorization");
	}

	@Override
	public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
		return customize(request, delegate.resolve(request));
	}

	@Override
	public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
		return customize(request, delegate.resolve(request, clientRegistrationId));
	}

	private OAuth2AuthorizationRequest customize(HttpServletRequest request, OAuth2AuthorizationRequest authRequest) {
		if (authRequest == null) {
			return null; // 인증 시작 URL이 아니면 플래그를 건드리지 않음
		}
		HttpSession session = request.getSession(false);
		if (session == null || session.getAttribute(SESSION_SWITCH) == null) {
			return authRequest;
		}
		session.removeAttribute(SESSION_SWITCH);

		Object regId = authRequest.getAttributes().get("registration_id");
		String key;
		String value;
		if ("google".equals(regId)) {
			key = "prompt";
			value = "select_account";
		} else if ("naver".equals(regId)) {
			key = "auth_type";
			value = "reauthenticate";
		} else if ("kakao".equals(regId)) {
			key = "prompt";
			value = "login";
		} else {
			return authRequest;
		}

		Map<String, Object> extra = new LinkedHashMap<>(authRequest.getAdditionalParameters());
		extra.put(key, value);
		return OAuth2AuthorizationRequest.from(authRequest).additionalParameters(extra).build();
	}
}
