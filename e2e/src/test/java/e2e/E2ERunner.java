package e2e;

import com.intuit.karate.junit5.Karate;

class E2ERunner {

    @Karate.Test
    Karate testAll() {
        // feature 파일은 classpath 루트의 서비스별 폴더와 scenario/에 위치하므로 절대 경로로 지정
        // relativeTo(getClass())를 쓰면 클래스 패키지(e2e/)를 기준으로 찾아서 실패함
        return Karate.run(
                "classpath:user",
                "classpath:coupon",
                "classpath:notification",
                "classpath:drop",
                "classpath:payment",
                "classpath:saga",
                "classpath:scenario"
        );
    }
}
