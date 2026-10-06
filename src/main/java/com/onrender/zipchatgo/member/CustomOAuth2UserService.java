package com.onrender.zipchatgo.member;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 소셜 로그인(구글/네이버/카카오) 성공 후 사용자 정보를 받아
 * 기존 회원이면 그 회원으로, 아니면 신규 가입시킨다.
 *
 * - 회원 식별은 (provider, providerId)로만 한다. 이메일로 계정을 연결하지 않는다(자동 연동 없음).
 * - 결과 OAuth2User의 attributes에 "memberId"를 담아 성공 핸들러에서 세션(loginMemberId)에 심는다.
 *
 * 이메일 충돌 정책
 * - GOOGLE: 기존 회원과 이메일이 겹치면 가입 거부
 * - NAVER : 연락처 이메일이라 본인 인증이 안 된 값 -> 겹치면 이메일을 비우고(NULL) 가입 진행
 * - KAKAO : 이메일 동의 없음 -> 항상 NULL
 */
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    public static final String ATTR_MEMBER_ID = "memberId";
    public static final String ERR_EMAIL_EXISTS = "email_exists";
    public static final String ERR_INVALID_USER = "invalid_user";

    private static final int MAX_EMAIL = 100;
    private static final int MAX_NAME = 50;

    private final MemberRepository memberRepository;
    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate = new DefaultOAuth2UserService();

    private record ParsedUser(String providerId, String name, String email) {}

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oauth2User = delegate.loadUser(userRequest);

        String provider = userRequest.getClientRegistration().getRegistrationId().toUpperCase();
        Map<String, Object> attrs = oauth2User.getAttributes();

        ParsedUser parsed = switch (provider) {
            case "GOOGLE" -> parseGoogle(attrs);
            case "NAVER" -> parseNaver(attrs);
            case "KAKAO" -> parseKakao(attrs);
            default -> throw error(ERR_INVALID_USER, "지원하지 않는 로그인 방식이에요.");
        };

        if (parsed.providerId() == null || parsed.providerId().isBlank()) {
            throw error(ERR_INVALID_USER, "소셜 계정 정보를 가져오지 못했어요.");
        }

        Member member = memberRepository
                .findByProviderAndProviderId(provider, parsed.providerId())
                .orElseGet(() -> register(provider, parsed));

        Map<String, Object> newAttrs = new HashMap<>(attrs);
        newAttrs.put(ATTR_MEMBER_ID, member.getId());

        String nameKey = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();

        return new DefaultOAuth2User(oauth2User.getAuthorities(), newAttrs, nameKey);
    }

    /** 신규 가입 (일반회원, 비밀번호 없음) */
    private Member register(String provider, ParsedUser p) {
        String email = normalizeEmail(p.email());

        if (email != null && memberRepository.existsByEmail(email)) {
            if ("GOOGLE".equals(provider)) {
                throw error(ERR_EMAIL_EXISTS,
                        "이미 가입된 이메일이에요. 이메일로 로그인해주세요.");
            }
            // NAVER: 이메일을 비우고 계속 진행
            email = null;
        }

        Member member = new Member();
        member.setEmail(email);
        member.setPassword(null);
        member.setName(cleanName(p.name(), defaultName(provider)));
        member.setMemberType("GENERAL");
        member.setProvider(provider);
        member.setProviderId(p.providerId());

        try {
            return memberRepository.save(member);
        } catch (DataAccessException e) {
            // 동시 가입 요청 등으로 UNIQUE 충돌이 난 경우: 이미 생긴 회원을 다시 조회
            return memberRepository
                    .findByProviderAndProviderId(provider, p.providerId())
                    .orElseThrow(() -> e);
        }
    }

    // ---------- 플랫폼별 응답 파싱 ----------

    private ParsedUser parseGoogle(Map<String, Object> attrs) {
        return new ParsedUser(str(attrs.get("sub")), str(attrs.get("name")), str(attrs.get("email")));
    }

    private ParsedUser parseNaver(Map<String, Object> attrs) {
        Map<String, Object> resp = asMap(attrs.get("response"));
        if (resp == null) {
            return new ParsedUser(null, null, null);
        }
        return new ParsedUser(str(resp.get("id")), str(resp.get("name")), str(resp.get("email")));
    }

    private ParsedUser parseKakao(Map<String, Object> attrs) {
        String id = str(attrs.get("id"));

        String nickname = null;
        Map<String, Object> account = asMap(attrs.get("kakao_account"));
        if (account != null) {
            Map<String, Object> profile = asMap(account.get("profile"));
            if (profile != null) {
                nickname = str(profile.get("nickname"));
            }
        }
        if (nickname == null) {
            Map<String, Object> props = asMap(attrs.get("properties"));
            if (props != null) {
                nickname = str(props.get("nickname"));
            }
        }
        // 카카오 이메일은 동의항목이 잠겨 있으므로 사용하지 않는다.
        return new ParsedUser(id, nickname, null);
    }

    // ---------- 유틸 ----------

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return (o instanceof Map) ? (Map<String, Object>) o : null;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String normalizeEmail(String email) {
        if (email == null) return null;
        String e = email.trim();
        if (e.isEmpty() || e.length() > MAX_EMAIL) return null;
        return e;
    }

    private static String cleanName(String name, String fallback) {
        String n = (name == null) ? "" : name.trim();
        if (n.isEmpty()) n = fallback;
        return n.length() > MAX_NAME ? n.substring(0, MAX_NAME) : n;
    }

    private static String defaultName(String provider) {
        return switch (provider) {
            case "GOOGLE" -> "구글 사용자";
            case "NAVER" -> "네이버 사용자";
            case "KAKAO" -> "카카오 사용자";
            default -> "소셜 사용자";
        };
    }

    private static OAuth2AuthenticationException error(String code, String message) {
        return new OAuth2AuthenticationException(new OAuth2Error(code, message, null), message);
    }
}
