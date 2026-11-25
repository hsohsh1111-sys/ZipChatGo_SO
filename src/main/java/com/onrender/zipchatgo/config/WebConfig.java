package com.onrender.zipchatgo.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

	// MemberRepositoryê°€ ?„ìš”?´ì„œ Spring Bean?¼ë¡œ ?±ë¡??AdminInterceptorë¥?ì£¼ì…ë°›ìŒ.
	private final AdminInterceptor adminInterceptor;

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		// classpath:/static/ ?€ Spring Bootê°€ ê¸°ë³¸?ìœ¼ë¡?"/" ?ë„ ?ë™ ?œë¹™?˜ë?ë¡?
		// ???±ë¡?€ /static/** ?•íƒœ??ë§í¬(guide.html ???€???¸í™˜???„í•´ ? ì?.
		registry.addResourceHandler("/static/**").addResourceLocations("classpath:/static/");

		// /templates/** ???œê±°??
		// Thymeleaf ?Œë”ë§ê³¼ SecurityConfig???¸ì¦/?¸ê? ê·œì¹™???°íšŒ?˜ëŠ” ë¬¸ì œê°€ ?ˆì—ˆ??
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		// ë¡œê·¸?¸ì´ ?„ìš”???˜ì´ì§€ë§?ëª…ì‹œ?ìœ¼ë¡?ì§€??(?”ì´?¸ë¦¬?¤íŠ¸ ?„ë‹Œ ëª…ì‹œ??ë³´í˜¸ ëª©ë¡ ë°©ì‹).
		registry.addInterceptor(new AuthInterceptor())
			.addPathPatterns(
				"/favorite",
				"/property/register",
				"/market/trend"
			);

		// ê´€ë¦¬ì ?„ìš© ê²½ë¡œ (API + ?˜ì¤‘??ë§Œë“¤ ê´€ë¦¬ì ?˜ì´ì§€ ????ë¯¸ë¦¬ ?¬í•¨)
		registry.addInterceptor(adminInterceptor)
			.addPathPatterns(
				"/api/admin/**",
				"/admin/**"
			);
	}
}
