package ch.cyberlogic.camel.examples.docsign.route;

import java.util.concurrent.TimeUnit;
import org.apache.camel.ConsumerTemplate;
import org.awaitility.Awaitility;

public class RouteTestUtil {

    static void awaitUntilLogAppearsInDBWithStatus(ConsumerTemplate consumerTemplate, Long id, String status, Long timeoutMs) {
        Awaitility.await()
                .atMost(timeoutMs, TimeUnit.MILLISECONDS)
                .until(() -> consumerTemplate.receive(
                        "sql:" +
                                "select * " +
                                "from document_sign_log " +
                                "where id = " + id + " " +
                                "and status = '" + status + "'",
                        100)
                        != null);
    }
}
