package com.onrender.zipchatgo.member;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 회원 탈퇴 시 Supabase Storage 에 남은 사진/서류 파일을 삭제한다.
 * 삭제에 실패해도 예외를 던지지 않고 로그만 남긴다(탈퇴 자체는 완료되어야 하므로).
 */
@Slf4j
@Component
public class SupabaseStorageCleaner {

    private static final int BATCH_SIZE = 100;

    private final String supabaseUrl;
    private final String serviceKey;
    private final String bucket;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public SupabaseStorageCleaner(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.service-key}") String serviceKey,
            @Value("${supabase.bucket}") String bucket) {
        this.supabaseUrl = supabaseUrl.endsWith("/")
                ? supabaseUrl.substring(0, supabaseUrl.length() - 1)
                : supabaseUrl;
        this.serviceKey = serviceKey;
        this.bucket = bucket;
    }

    public void deleteAll(List<String> filePaths) {
        if (filePaths == null || filePaths.isEmpty()) {
            return;
        }

        List<String> objectPaths = new ArrayList<>();
        for (String path : filePaths) {
            String normalized = normalize(path);
            if (normalized != null) {
                objectPaths.add(normalized);
            }
        }

        for (int i = 0; i < objectPaths.size(); i += BATCH_SIZE) {
            List<String> batch = objectPaths.subList(i, Math.min(i + BATCH_SIZE, objectPaths.size()));
            deleteBatch(batch);
        }
    }

    private void deleteBatch(List<String> paths) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(supabaseUrl + "/storage/v1/object/" + bucket))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + serviceKey)
                    .header("apikey", serviceKey)
                    .header("Content-Type", "application/json")
                    .method("DELETE", HttpRequest.BodyPublishers.ofString(toJson(paths)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() / 100 != 2) {
                log.warn("Supabase 파일 삭제 실패: status={}, count={}", response.statusCode(), paths.size());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Supabase 파일 삭제 중단", e);
        } catch (Exception e) {
            log.warn("Supabase 파일 삭제 오류: count={}", paths.size(), e);
        }
    }

    /**
     * DB 에 저장된 값이 객체 경로(예: 90001/photos/a.png)가 기본이지만,
     * 혹시 전체 URL 이 저장된 행이 있으면 버킷 이후 경로만 잘라서 사용한다.
     */
    private String normalize(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String p = path.trim();
        if (p.startsWith("http")) {
            String marker = "/" + bucket + "/";
            int idx = p.indexOf(marker);
            if (idx < 0) {
                return null;
            }
            p = p.substring(idx + marker.length());
        }
        return p.startsWith("/") ? p.substring(1) : p;
    }

    private String toJson(List<String> paths) {
        StringBuilder sb = new StringBuilder("{\"prefixes\":[");
        for (int i = 0; i < paths.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('"').append(paths.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return sb.append("]}").toString();
    }
}
