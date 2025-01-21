package ch.cyberlogic.camel.examples.docsign.util;

import java.util.concurrent.TimeUnit;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.awaitility.Awaitility;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.JsonBody;
import org.mockserver.model.MediaType;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class RouteTestUtil {

    public static void awaitUntilLogAppearsInDBWithStatus(
            ConsumerTemplate consumerTemplate,
            Long id,
            String status,
            Long timeoutMs) {
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

    public static void awaitUntilLogAppearsInDBWithDocumentId(
            ConsumerTemplate consumerTemplate,
            Integer documentId,
            Long timeoutMs) {
        Awaitility.await()
                .atMost(timeoutMs, TimeUnit.MILLISECONDS)
                .until(() -> consumerTemplate.receive(
                        "sql:" +
                                "select * " +
                                "from document_sign_log " +
                                "where document_number = " + documentId,
                        100)
                        != null);
    }

    public static void addEndpointOnRouteCompletion(
            CamelContext camelContext,
            String routeId,
            String onCompletionEndpointUri) throws Exception {
        AdviceWith.adviceWith(
                camelContext,
                routeId,
                route -> route
                        .onCompletion()
                        .to(onCompletionEndpointUri)
        );
    }

    public static void configureRegularSignServiceResponse(
            MockServerClient mockServerClient,
            String expectedRequestJson,
            String response) {
        JsonBody jsonExpectedRequestBody = new JsonBody(expectedRequestJson);
        mockServerClient
                .when(request()
                        .withMethod("POST")
                        .withPath(TestConstants.SIGN_DOCUMENT_SERVICE_PATH)
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody(jsonExpectedRequestBody))
                .respond(response()
                        .withStatusCode(200)
                        .withBody(response));
    }
}
