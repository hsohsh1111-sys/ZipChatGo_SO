package com.onrender.zipchatgo.property;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 내가 등록한 매물 조회/수정/삭제 API.
 * 모든 요청은 세션(loginMemberId) 기준이며, 본인 매물이 아니면 "매물을 찾을 수 없습니다."로 거부한다.
 * 게스트 세션에는 loginMemberId가 없으므로 자동으로 막힌다.
 */
@RestController
@RequestMapping("/api/my/properties")
@RequiredArgsConstructor
public class MyPropertyController {

    private static final String SESSION_KEY = "loginMemberId";

    private final MemberPropertyService memberPropertyService;

    @GetMapping
    public Map<String, Object> list(HttpSession session) {
        return run(session, memberId -> {
            Map<String, Object> result = new HashMap<>();
            result.put("properties", memberPropertyService.listMine(memberId));
            return result;
        });
    }

    @GetMapping("/{propertyId}")
    public Map<String, Object> detail(@PathVariable Long propertyId, HttpSession session) {
        return run(session, memberId -> {
            Map<String, Object> result = new HashMap<>();
            result.put("property", memberPropertyService.detail(memberId, propertyId));
            return result;
        });
    }

    @PutMapping("/{propertyId}")
    public Map<String, Object> update(@PathVariable Long propertyId,
                                      @RequestBody PropertyRegisterRequest request,
                                      HttpSession session) {
        return run(session, memberId -> {
            MemberProperty saved = memberPropertyService.update(memberId, propertyId, request);
            Map<String, Object> result = new HashMap<>();
            result.put("propertyId", saved.getId());
            result.put("status", saved.getStatus());
            return result;
        });
    }

    @DeleteMapping("/{propertyId}")
    public Map<String, Object> delete(@PathVariable Long propertyId, HttpSession session) {
        return run(session, memberId -> {
            memberPropertyService.delete(memberId, propertyId);
            return new HashMap<>();
        });
    }

    @DeleteMapping("/{propertyId}/photos/{photoId}")
    public Map<String, Object> deletePhoto(@PathVariable Long propertyId,
                                           @PathVariable Long photoId,
                                           HttpSession session) {
        return run(session, memberId -> {
            memberPropertyService.deletePhoto(memberId, propertyId, photoId);
            return new HashMap<>();
        });
    }

    @DeleteMapping("/{propertyId}/documents/{documentId}")
    public Map<String, Object> deleteDocument(@PathVariable Long propertyId,
                                              @PathVariable Long documentId,
                                              HttpSession session) {
        return run(session, memberId -> {
            memberPropertyService.deleteDocument(memberId, propertyId, documentId);
            return new HashMap<>();
        });
    }

    /** 로그인 확인 + 예외를 {success:false, message} 형태로 변환하는 공통 처리 */
    private Map<String, Object> run(HttpSession session, Function<Long, Map<String, Object>> action) {
        Object memberIdObj = session.getAttribute(SESSION_KEY);
        if (memberIdObj == null) {
            return fail("로그인이 필요합니다.");
        }

        try {
            Map<String, Object> result = new HashMap<>(action.apply((Long) memberIdObj));
            result.put("success", true);
            return result;
        } catch (IllegalStateException e) {
            return fail(e.getMessage());
        } catch (Exception e) {
            return fail("처리 중 오류가 발생했어요.");
        }
    }

    private Map<String, Object> fail(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", message);
        return result;
    }
}
