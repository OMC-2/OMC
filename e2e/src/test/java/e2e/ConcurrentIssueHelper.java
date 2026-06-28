package e2e;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.*;

/**
 * 쿠폰 동시 발급 테스트 헬퍼
 *
 * Karate 내부에서는 진짜 동시 HTTP 요청이 불가능하므로
 * Java CountDownLatch 를 사용해 모든 스레드를 동시에 출발시킨다.
 *
 * 사용법 (feature 파일):
 *   * def Helper = Java.type('e2e.ConcurrentIssueHelper')
 *   * def results = Helper.issueCoupons(baseUrl, gatewaySecret, couponId, tokens)
 *   results → List<Map> : [{issueStatus: 201, issueErrorCode: null}, {issueStatus: 409, issueErrorCode: 'COUPON-004'}, ...]
 */
public class ConcurrentIssueHelper {

    public static List<Map<String, Object>> issueCoupons(
            String baseUrl,
            String gatewaySecret,
            String couponId,
            List<String> tokens) throws Exception {

        int n = tokens.size();
        CountDownLatch readyLatch = new CountDownLatch(n);  // 모든 스레드 준비 대기
        CountDownLatch startLatch = new CountDownLatch(1);  // 동시 출발 신호
        ExecutorService executor = Executors.newFixedThreadPool(n);
        List<Future<Map<String, Object>>> futures = new ArrayList<>();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();

        for (String token : tokens) {
            final String t = token;
            futures.add(executor.submit(() -> {
                readyLatch.countDown();  // 내 준비 완료 알림
                startLatch.await();     // 모든 스레드가 준비될 때까지 대기

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/v1/coupons/" + couponId + "/issue"))
                        .header("X-Gateway-Secret", gatewaySecret)
                        .header("Authorization", "Bearer " + t)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .timeout(java.time.Duration.ofSeconds(30))
                        .build();

                HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());

                Map<String, Object> result = new HashMap<>();
                result.put("token", t);
                result.put("issueStatus", resp.statusCode());
                result.put("issueErrorCode", extractErrorCode(resp.body()));
                result.put("responseBody", resp.body());
                return result;
            }));
        }

        readyLatch.await();     // 모든 스레드 준비 완료까지 대기
        startLatch.countDown(); // 동시 출발

        List<Map<String, Object>> results = new ArrayList<>();
        for (Future<Map<String, Object>> future : futures) {
            results.add(future.get(30, TimeUnit.SECONDS));
        }

        executor.shutdown();
        return results;
    }

    private static String extractErrorCode(String body) {
        if (body == null) return null;
        int idx = body.indexOf("\"errorCode\":\"");
        if (idx < 0) return null;
        int start = idx + 13;
        int end = body.indexOf("\"", start);
        if (end < 0) return null;
        return body.substring(start, end);
    }
}
