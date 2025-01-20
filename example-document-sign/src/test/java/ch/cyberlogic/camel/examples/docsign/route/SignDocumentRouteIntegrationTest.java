package ch.cyberlogic.camel.examples.docsign.route;

import ch.cyberlogic.camel.examples.docsign.model.SignDocumentRequest;
import ch.cyberlogic.camel.examples.docsign.model.SignDocumentResponse;
import ch.cyberlogic.camel.examples.docsign.service.ExchangeTransformer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.mockserver.model.JsonBody;
import org.mockserver.model.MediaType;
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
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import static ch.cyberlogic.camel.examples.docsign.route.RouteTestUtil.awaitUntilLogAppearsInDBWithStatus;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.verify.VerificationTimes.exactly;

@EnabledIf("${test.integration.enabled:false}")
@CamelSpringBootTest
@ExcludeRoutes({ReadDocumentRoute.class, SendDocumentRoute.class})
@SpringBootTest
@ActiveProfiles("it")
@ImportTestcontainers
@DirtiesContext
public class SignDocumentRouteIntegrationTest {

    private static final Long TEST_TIMEOUT = 5000L;

    private static final String USER = "user";
    private static final String PASSWORD = "password";
    private static final String DB_NAME = "integration";
    private static final String SIGN_DOCUMENT_SERVICE_PATH = "/SignDocument/sign";
    private static final String SIGN_DOCUMENT_API_KEY = "acb93ac2-2dce-4209-8ef2-2188ce2047c2";
    private static final String SIGN_DOCUMENT_SIGN_TYPE = "CAdES-C";
    private static final String SIGN_DOCUMENT_TRUSTSTORE_PASSWORD = "password";
    private static final String SIGN_DOCUMENT_TRUSTSTORE = "it/sign_document_service/test_truststore.jks";

    static MockServerClient mockServerClient;

    static MockServerContainer mockServerContainer =
            new MockServerContainer(DockerImageName.parse("mockserver/mockserver:5.15.0"))
                    .withEnv("MOCKSERVER_TLS_PRIVATE_KEY_PATH", "/config/ssl/mockserver_private_key_pk8.pem")
                    .withEnv("MOCKSERVER_TLS_X509_CERTIFICATE_PATH", "/config/ssl/mockserver_cert_chain.pem")
                    .withEnv("MOCKSERVER_CERTIFICATE_AUTHORITY_PRIVATE_KEY", "/config/ssl/rootCA_private_key_pk8.pem")
                    .withEnv("MOCKSERVER_CERTIFICATE_AUTHORITY_X509_CERTIFICATE", "/config/ssl/rootCA_cert_chain.pem")
                    .withCopyFileToContainer(
                            MountableFile.forClasspathResource("it/sign_document_service/mockserver_private_key_pk8.pem", 0777),
                            "/config/ssl/mockserver_private_key_pk8.pem"
                    ).withCopyFileToContainer(
                            MountableFile.forClasspathResource("it/sign_document_service/mockserver_cert_chain.pem", 0777),
                            "/config/ssl/mockserver_cert_chain.pem"
                    ).withCopyFileToContainer(
                            MountableFile.forClasspathResource("it/sign_document_service/rootCA_private_key_pk8.pem", 0777),
                            "/config/ssl/rootCA_private_key_pk8.pem"
                    ).withCopyFileToContainer(
                            MountableFile.forClasspathResource("it/sign_document_service/rootCA_cert_chain.pem", 0777),
                            "/config/ssl/rootCA_cert_chain.pem"
                    );

    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:17.2-alpine")
            .withDatabaseName(DB_NAME)
            .withUsername(USER)
            .withPassword(PASSWORD)
            .withInitScripts(List.of("it/db/init.sql", "it/db/add-row.sql"));

    @ServiceConnection
    static ArtemisContainer artemisContainer = new ArtemisContainer("apache/activemq-artemis:2.39.0-alpine");

    private static final String MOCK_SEND_DOCUMENT = "mock:" + SendDocumentRoute.INPUT_ENDPOINT;

    @Autowired
    private ProducerTemplate producerTemplate;

    @Autowired
    private ConsumerTemplate consumerTemplate;

    @EndpointInject(MOCK_SEND_DOCUMENT)
    private MockEndpoint mock;

    @Autowired
    private CamelContext camelContext;

    @DynamicPropertySource
    static void setUpProperties(DynamicPropertyRegistry registry) {
        mockServerContainer.start();
        mockServerClient = new MockServerClient(
                mockServerContainer.getHost(),
                mockServerContainer.getServerPort()
        );

        registry.add("signDocument.serviceUrl", () -> mockServerContainer.getSecureEndpoint() + SIGN_DOCUMENT_SERVICE_PATH);
        registry.add("signDocument.apiKey", () -> SIGN_DOCUMENT_API_KEY);
        registry.add("signDocument.signType", () -> SIGN_DOCUMENT_SIGN_TYPE);
        registry.add("signDocument.trustStore", () -> SIGN_DOCUMENT_TRUSTSTORE);
        registry.add("signDocument.trustStorePassword", () -> SIGN_DOCUMENT_TRUSTSTORE_PASSWORD);
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
                SIGN_DOCUMENT_SIGN_TYPE,
                SIGN_DOCUMENT_API_KEY,
                documentType
        );
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        ObjectWriter writer = objectMapper.writer();
        String expectedSerializedRequest = writer.writeValueAsString(expectedRequest);
        JsonBody jsonBody = new JsonBody(expectedSerializedRequest);
        String status = "SignDocumentResponse: Status ok";
        String responseMessage = "Document signed";
        SignDocumentResponse expectedResponse = new SignDocumentResponse(
                contents,
                status,
                responseMessage,
                LocalDateTime.now()
        );
        String mockSerializedResponse = writer.writeValueAsString(expectedResponse);
        mockServerClient
                .when(request()
                        .withMethod("POST")
                        .withPath(SIGN_DOCUMENT_SERVICE_PATH)
                        .withContentType(MediaType.APPLICATION_JSON)
                        .withBody(jsonBody))
                .respond(response()
                        .withStatusCode(200)
                        .withBody(mockSerializedResponse));

        producerTemplate.sendBodyAndHeaders(
                SignDocumentRoute.INPUT_ENDPOINT,
                contents,
                Map.of(
                        ExchangeTransformer.OWNER_ID, ownerId,
                        ExchangeTransformer.DOCUMENT_TYPE, documentType,
                        ReadDocumentRoute.DATABASE_LOG_ID, 1
                ));
        awaitUntilLogAppearsInDBWithStatus(consumerTemplate, 1L, status, TEST_TIMEOUT);

        Map dbResult = consumerTemplate.receive(
                        "sql:select * from document_sign_log where id = 1",
                        TEST_TIMEOUT)
                .getMessage().getBody(Map.class);

        mockServerClient.verify(request()
                        .withMethod("POST")
                        .withPath(SIGN_DOCUMENT_SERVICE_PATH)
                        .withBody(expectedSerializedRequest),
                exactly(1));

        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(expectedResponse);
        mock.assertIsSatisfied(TEST_TIMEOUT);

        assertEquals(32767, dbResult.get("document_number"));
        assertEquals(ownerId, dbResult.get("owner"));
        assertEquals(status, dbResult.get("status"));
        assertNotNull(dbResult.get("last_update"));
    }
}
