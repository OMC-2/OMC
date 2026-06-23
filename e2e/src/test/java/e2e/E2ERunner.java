package e2e;

import com.intuit.karate.junit5.Karate;

class E2ERunner {

    @Karate.Test
    Karate testAll() {
        return Karate.run().relativeTo(getClass());
    }
}
