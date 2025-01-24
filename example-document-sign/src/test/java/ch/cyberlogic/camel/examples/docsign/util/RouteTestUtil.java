package ch.cyberlogic.camel.examples.docsign.util;

import ch.cyberlogic.camel.examples.docsign.model.ClientSendResponse;
import java.util.concurrent.TimeUnit;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.awaitility.Awaitility;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.JsonBody;
import org.mockserver.model.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;

import static org.apache.camel.component.jms.JmsConstants.JMS_HEADER_REPLY_TO;
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

    public static void waitUntilContainersStart(GenericContainer<?>... containers) {
        for (GenericContainer<?> container : containers) {
            container.start();
        }
    }

    public static void setUpMockClientSendService(CamelContext camelContext, MockEndpoint mockClientSendServiceEndpoint) throws Exception {
        camelContext.addRoutes(
                new RouteBuilder() {
                    @Override
                    public void configure() {
                        from("jms:" + TestConstants.CLIENT_SEND_REQUEST_QUEUE)
                                .id("MockClientSendService")
                                .log("MockClientSendService received request: ${body}; RequestHeaders: ${headers}")
                                .to(mockClientSendServiceEndpoint)
                                .setBody((exchange -> new ClientSendResponse(
                                        TestConstants.CLIENT_SEND_RESPONSE_STATUS_OK,
                                        TestConstants.CLIENT_SEND_RESPONSE_MESSAGE_REGULAR,
                                        TestConstants.CLIENT_SEND_RESPONSE_TIMESTAMP_REGULAR
                                )))
                                .marshal().jacksonXml()
                                .toD()
                                .pattern(ExchangePattern.InOnly)
                                .uri("jms:${header." + JMS_HEADER_REPLY_TO + "}?preserveMessageQos=true");
                    }
                }
        );
    }

    public static void resetMockEndpoints(MockEndpoint... mockEndpoints) {
        for (MockEndpoint mockEndpoint : mockEndpoints) {
            mockEndpoint.reset();
        }
    }

    public static void checkAssertionsSatisfied(MockEndpoint... mockEndpoints) throws InterruptedException {
        for (MockEndpoint mockEndpoint : mockEndpoints) {
            mockEndpoint.assertIsSatisfied(TestConstants.TEST_TIMEOUT);
        }
    }

    public static void cleanUpIntegrationDB(ProducerTemplate producerTemplate) {
        producerTemplate.sendBody("sql:delete from document_sign_log", null);
    }
}
