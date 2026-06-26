package e2e;

import com.intuit.karate.Runner;
import org.junit.jupiter.api.Test;

class E2ERunner {

    @Test
    void testAll() {
        Runner.path(
                "classpath:user",
                "classpath:coupon",
                "classpath:notification",
                "classpath:drop",
                "classpath:payment",
                "classpath:saga",
                "classpath:scenario"
        ).parallel(1);
    }
}
