package ch.cyberlogic.camel.examples.docsign.route;

import ch.cyberlogic.camel.examples.docsign.configuration.SSLContextParamsConfiguration;
import ch.cyberlogic.camel.examples.docsign.model.ClientSendRequest;
import ch.cyberlogic.camel.examples.docsign.model.ClientSendResponse;
import ch.cyberlogic.camel.examples.docsign.model.SignDocumentResponse;
import ch.cyberlogic.camel.examples.docsign.service.SignDocumentRequestMapper;
import ch.cyberlogic.camel.examples.docsign.util.AdviceWithUtilConfigurable;
import ch.cyberlogic.camel.examples.docsign.util.RouteTestUtil;
import ch.cyberlogic.camel.examples.docsign.util.TestConstants;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.ExcludeRoutes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.EnabledIf;
import org.testcontainers.activemq.ArtemisContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import static ch.cyberlogic.camel.examples.docsign.service.FileMetadataExtractor.CLIENT_ID;
import static ch.cyberlogic.camel.examples.docsign.service.FileMetadataExtractor.DOCUMENT_ID;
import static ch.cyberlogic.camel.examples.docsign.service.FileMetadataExtractor.OWNER_ID;
import static ch.cyberlogic.camel.examples.docsign.util.RouteTestUtil.awaitUntilLogAppearsInDBWithStatus;
import static ch.cyberlogic.camel.examples.docsign.util.containers.TestContainersConfigurations.getConfiguredArtemisContainer;
import static ch.cyberlogic.camel.examples.docsign.util.containers.TestContainersConfigurations.getConfiguredPostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnabledIf("${test.integration.enabled:false}")
@CamelSpringBootTest
@ExcludeRoutes({ReadDocumentRoute.class, SignDocumentRoute.class})
@SpringBootTest
@ActiveProfiles("it")
@ImportTestcontainers
@DirtiesContext
public class SendDocumentRouteIntegrationTest {

    private static final String MOCK_ROUTE_FINISHED = "mock:send-document-finished";

    @ServiceConnection
    static ArtemisContainer artemisContainer = getConfiguredArtemisContainer();

    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer = getConfiguredPostgreSQLContainer(
            TestConstants.USER,
            TestConstants.PASSWORD,
            TestConstants.DB_NAME,
            List.of("it/db/add-row.sql"));

    @MockitoBean
    private SSLContextParamsConfiguration excludedSSLContextParamsConfiguration;

    @MockitoBean
    private SignDocumentRequestMapper excludedSignDocumentRequestMapper;

    @Autowired
    private ProducerTemplate producerTemplate;

    @Autowired
    private ConsumerTemplate consumerTemplate;

    @EndpointInject(MOCK_ROUTE_FINISHED)
    private MockEndpoint mockRouteFinished;

    @EndpointInject(TestConstants.MOCK_CLIENT_SEND_SERVICE)
    private MockEndpoint mockClientSendService;

    @Autowired
    private CamelContext camelContext;

    @Autowired
    XmlMapper xmlMapper;

    @DynamicPropertySource
    static void setUpProperties(DynamicPropertyRegistry registry) {
        RouteTestUtil.setTestClientSendServiceProperties(registry);
    }

    @BeforeEach
    void setUp() throws Exception {
        mockRouteFinished.reset();
        mockClientSendService.reset();

        new AdviceWithUtilConfigurable(camelContext, SendDocumentRoute.ROUTE_ID)
                .addEndpointOnRouteCompletion(MOCK_ROUTE_FINISHED);

        RouteTestUtil.setUpMockClientSendService(
                camelContext,
                mockClientSendService);
    }

    @Test
    void signDocumentRouteTest() throws Exception {
        String contents = "Hello World";
        String documentId = "32767";
        String ownerId = "bigBank11";
        String clientId = "32766";
        String signDocumentResponseStatus = "SignDocumentResponse: Status ok";
        String signDocumentResponseMessage = "Document signed";
        SignDocumentResponse signDocumentResponse = new SignDocumentResponse(
                Base64.getEncoder().encodeToString(contents.getBytes()),
                signDocumentResponseStatus,
                signDocumentResponseMessage,
                LocalDateTime.now()
        );
        ClientSendRequest expectedRequest = new ClientSendRequest(
                documentId,
                ownerId,
                signDocumentResponse.getSignedDocument(),
                clientId
        );
        ObjectWriter writer = xmlMapper.writer();
        String expectedSerializedRequest = writer.writeValueAsString(expectedRequest);
        ClientSendResponse expectedResponse = new ClientSendResponse(
                TestConstants.CLIENT_SEND_RESPONSE_STATUS_OK,
                TestConstants.CLIENT_SEND_RESPONSE_MESSAGE_REGULAR,
                TestConstants.CLIENT_SEND_RESPONSE_TIMESTAMP_REGULAR
        );

        producerTemplate.sendBodyAndHeaders(
                SendDocumentRoute.INPUT_ENDPOINT,
                signDocumentResponse,
                Map.of(
                        DOCUMENT_ID, documentId,
                        OWNER_ID, ownerId,
                        CLIENT_ID, clientId,
                        ReadDocumentRoute.DATABASE_LOG_ID, 1
                ));
        awaitUntilLogAppearsInDBWithStatus(
                consumerTemplate,
                1L,
                TestConstants.CLIENT_SEND_RESPONSE_STATUS_OK,
                TestConstants.TEST_TIMEOUT);

        Map dbResult = consumerTemplate.receive(
                        "sql:select * from document_sign_log where id = 1",
                        TestConstants.TEST_TIMEOUT)
                .getMessage().getBody(Map.class);

        mockClientSendService.expectedMessageCount(1);
        mockClientSendService.expectedBodiesReceived(expectedSerializedRequest);
        mockClientSendService.assertIsSatisfied(TestConstants.TEST_TIMEOUT);

        mockRouteFinished.expectedMessageCount(1);
        mockRouteFinished.expectedBodiesReceived(expectedResponse);
        mockRouteFinished.assertIsSatisfied(TestConstants.TEST_TIMEOUT);

        assertEquals(32767, dbResult.get("document_number"));
        assertEquals(ownerId, dbResult.get("owner"));
        assertEquals(TestConstants.CLIENT_SEND_RESPONSE_STATUS_OK, dbResult.get("status"));
        assertNotNull(dbResult.get("last_update"));
    }
}
