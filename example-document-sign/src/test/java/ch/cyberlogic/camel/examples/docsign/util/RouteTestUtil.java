package ch.cyberlogic.camel.examples.docsign.util;

import java.util.concurrent.TimeUnit;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.awaitility.Awaitility;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.JsonBody;
import org.mockserver.model.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;

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

    public static void setTestSftpServerProperties(
            DynamicPropertyRegistry registry,
            Integer port) {
        registry.add("sftp.server.host", () -> "localhost");
        registry.add("sftp.server.port", () -> port);
        registry.add("sftp.server.directory", () -> TestConstants.SFTP_DIR);
        registry.add("sftp.server.user", () -> TestConstants.USER);
        registry.add("sftp.server.password", () -> TestConstants.PASSWORD);
        registry.add("sftp.server.known_hosts", () -> "src/test/resources/it/known_hosts");
    }

    public static void setTestSignDocumentServiceProperties(
            DynamicPropertyRegistry registry,
            String endpoint) {
        registry.add("signDocument.serviceUrl",
                () -> endpoint + TestConstants.SIGN_DOCUMENT_SERVICE_PATH);
        registry.add("signDocument.apiKey", () -> TestConstants.SIGN_DOCUMENT_API_KEY);
        registry.add("signDocument.signType", () -> TestConstants.SIGN_DOCUMENT_SIGN_TYPE);
        registry.add("signDocument.trustStore", () -> TestConstants.SIGN_DOCUMENT_TRUSTSTORE);
        registry.add("signDocument.trustStorePassword", () -> TestConstants.SIGN_DOCUMENT_TRUSTSTORE_PASSWORD);
    }

    public static void setTestClientSendServiceProperties(DynamicPropertyRegistry registry) {
        registry.add("clientSend.clientSendRequestQueue",
                () -> TestConstants.CLIENT_SEND_REQUEST_QUEUE);
        registry.add("clientSend.clientSendResponseQueue",
                () -> TestConstants.CLIENT_SEND_RESPONSE_QUEUE);
    }
}
