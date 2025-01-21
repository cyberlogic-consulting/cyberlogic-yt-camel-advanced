package ch.cyberlogic.camel.examples.docsign.route;

import ch.cyberlogic.camel.examples.docsign.model.SignDocumentRequest;
import ch.cyberlogic.camel.examples.docsign.model.SignDocumentResponse;
import ch.cyberlogic.camel.examples.docsign.util.RouteTestUtil;
import ch.cyberlogic.camel.examples.docsign.util.TestConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.ExcludeRoutes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.EnabledIf;
import org.testcontainers.activemq.ArtemisContainer;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import static ch.cyberlogic.camel.examples.docsign.service.FileMetadataExtractor.DOCUMENT_TYPE;
import static ch.cyberlogic.camel.examples.docsign.service.FileMetadataExtractor.OWNER_ID;
import static ch.cyberlogic.camel.examples.docsign.util.RouteTestUtil.awaitUntilLogAppearsInDBWithStatus;
import static ch.cyberlogic.camel.examples.docsign.util.containers.TestContainersConfigurations.getConfiguredArtemisContainer;
import static ch.cyberlogic.camel.examples.docsign.util.containers.TestContainersConfigurations.getConfiguredMockServerContainer;
import static ch.cyberlogic.camel.examples.docsign.util.containers.TestContainersConfigurations.getConfiguredPostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.verify.VerificationTimes.exactly;

@EnabledIf("${test.integration.enabled:false}")
@CamelSpringBootTest
@ExcludeRoutes({ReadDocumentRoute.class, SendDocumentRoute.class})
@SpringBootTest
@ActiveProfiles("it")
@ImportTestcontainers
@DirtiesContext
public class SignDocumentRouteIntegrationTest {

    private static final String MOCK_SEND_DOCUMENT = "mock:" + SendDocumentRoute.INPUT_ENDPOINT;

    static MockServerClient mockServerClient;

    static MockServerContainer mockServerContainer = getConfiguredMockServerContainer();

    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer = getConfiguredPostgreSQLContainer(
            TestConstants.USER,
            TestConstants.PASSWORD,
            TestConstants.DB_NAME,
            List.of("it/db/add-row.sql"));

    @ServiceConnection
    static ArtemisContainer artemisContainer = getConfiguredArtemisContainer();

    @Autowired
    private ProducerTemplate producerTemplate;

    @Autowired
    private ConsumerTemplate consumerTemplate;

    @EndpointInject(MOCK_SEND_DOCUMENT)
    private MockEndpoint mock;

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ObjectMapper jsonMapper;

    @DynamicPropertySource
    static void setUpProperties(DynamicPropertyRegistry registry) {
        mockServerContainer.start();
        mockServerClient = new MockServerClient(
                mockServerContainer.getHost(),
                mockServerContainer.getServerPort()
        );

        registry.add("signDocument.serviceUrl", () ->
                mockServerContainer.getSecureEndpoint() + TestConstants.SIGN_DOCUMENT_SERVICE_PATH);
        registry.add("signDocument.apiKey", () -> TestConstants.SIGN_DOCUMENT_API_KEY);
        registry.add("signDocument.signType", () -> TestConstants.SIGN_DOCUMENT_SIGN_TYPE);
        registry.add("signDocument.trustStore", () -> TestConstants.SIGN_DOCUMENT_TRUSTSTORE);
        registry.add("signDocument.trustStorePassword", () -> TestConstants.SIGN_DOCUMENT_TRUSTSTORE_PASSWORD);
    }

    @BeforeEach
    void setUp() throws Exception {
        mock.reset();
        AdviceWith.adviceWith(
                camelContext,
                SignDocumentRoute.ROUTE_ID,
                route -> route
                        .interceptSendToEndpoint(SendDocumentRoute.INPUT_ENDPOINT)
                        .skipSendToOriginalEndpoint()
                        .to(MOCK_SEND_DOCUMENT)
        );
    }

    @Test
    void signDocumentRouteTest() throws Exception {
        String contents = "Hello World";
        String ownerId = "bigBank11";
        String documentType = "taxReport";
        SignDocumentRequest expectedRequest = new SignDocumentRequest(
                Base64.getEncoder().encodeToString(contents.getBytes()),
                ownerId,
                TestConstants.SIGN_DOCUMENT_SIGN_TYPE,
                TestConstants.SIGN_DOCUMENT_API_KEY,
                documentType
        );
        ObjectWriter writer = jsonMapper.writer();
        String expectedSerializedRequest = writer.writeValueAsString(expectedRequest);
        String status = "SignDocumentResponse: Status ok";
        String responseMessage = "Document signed";
        SignDocumentResponse expectedResponse = new SignDocumentResponse(
                contents,
                status,
                responseMessage,
                LocalDateTime.now()
        );
        String mockSerializedResponse = writer.writeValueAsString(expectedResponse);
        RouteTestUtil.configureRegularSignServiceResponse(
                mockServerClient,
                expectedSerializedRequest,
                mockSerializedResponse
        );

        producerTemplate.sendBodyAndHeaders(
                SignDocumentRoute.INPUT_ENDPOINT,
                contents,
                Map.of(
                        OWNER_ID, ownerId,
                        DOCUMENT_TYPE, documentType,
                        ReadDocumentRoute.DATABASE_LOG_ID, 1
                ));
        awaitUntilLogAppearsInDBWithStatus(
                consumerTemplate,
                1L,
                status,
                TestConstants.TEST_TIMEOUT);

        Map dbResult = consumerTemplate.receive(
                        "sql:select * from document_sign_log where id = 1",
                        TestConstants.TEST_TIMEOUT)
                .getMessage().getBody(Map.class);

        mockServerClient.verify(request()
                        .withMethod("POST")
                        .withPath(TestConstants.SIGN_DOCUMENT_SERVICE_PATH)
                        .withBody(expectedSerializedRequest),
                exactly(1));

        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(expectedResponse);
        mock.assertIsSatisfied(TestConstants.TEST_TIMEOUT);

        assertEquals(32767, dbResult.get("document_number"));
        assertEquals(ownerId, dbResult.get("owner"));
        assertEquals(status, dbResult.get("status"));
        assertNotNull(dbResult.get("last_update"));
    }
}
