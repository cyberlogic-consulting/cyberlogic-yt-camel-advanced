package ch.cyberlogic.camel.examples.docsign;

import ch.cyberlogic.camel.examples.docsign.model.SignDocumentRequest;
import ch.cyberlogic.camel.examples.docsign.model.SignDocumentResponse;
import ch.cyberlogic.camel.examples.docsign.service.SignDocumentRequestMapper;
import ch.cyberlogic.camel.examples.docsign.util.RouteTestUtil;
import ch.cyberlogic.camel.examples.docsign.util.TestConstants;
import ch.cyberlogic.camel.examples.docsign.util.endpoints.Endpoints;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.Message;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import static ch.cyberlogic.camel.examples.docsign.util.RouteTestUtil.awaitUntilLogAppearsInDBWithStatus;
import static ch.cyberlogic.camel.examples.docsign.util.containers.TestContainersConfigurations.getConfiguredArtemisContainer;
import static ch.cyberlogic.camel.examples.docsign.util.containers.TestContainersConfigurations.getConfiguredMockServerContainer;
import static ch.cyberlogic.camel.examples.docsign.util.containers.TestContainersConfigurations.getConfiguredPostgreSQLContainer;
import static ch.cyberlogic.camel.examples.docsign.util.containers.TestContainersConfigurations.getConfiguredSftpContainer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.verify.VerificationTimes.exactly;

@EnabledIf("${test.integration.enabled:false}")
@CamelSpringBootTest
@SpringBootTest
@ActiveProfiles("it")
@ImportTestcontainers
@DirtiesContext
public class ExampleDocumentSignIntegrationTest {

    static MockServerClient mockServerClient;

    static GenericContainer<?> sftpContainer = getConfiguredSftpContainer(
            TestConstants.USER,
            TestConstants.PASSWORD,
            TestConstants.SFTP_DIR);

    @ServiceConnection
    static ArtemisContainer artemisContainer = getConfiguredArtemisContainer();

    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer = getConfiguredPostgreSQLContainer(
            TestConstants.USER,
            TestConstants.PASSWORD,
            TestConstants.DB_NAME
    );

    static MockServerContainer mockServerContainer = getConfiguredMockServerContainer();

    @Autowired
    private CamelContext camelContext;

    @EndpointInject(TestConstants.MOCK_CLIENT_SEND_SERVICE)
    private MockEndpoint mockClientSendService;

    @Autowired
    private SignDocumentRequestMapper signDocumentRequestMapper;

    @Autowired
    private FluentProducerTemplate producerTemplate;

    @Autowired
    private ConsumerTemplate consumerTemplate;

    @Autowired
    private ObjectMapper jsonMapper;

    @DynamicPropertySource
    static void setUpProperties(DynamicPropertyRegistry registry) {
        RouteTestUtil.waitUntilContainersStart(
                sftpContainer,
                mockServerContainer);
        mockServerClient = new MockServerClient(
                mockServerContainer.getHost(),
                mockServerContainer.getServerPort()
        );
        RouteTestUtil.setTestSignDocumentServiceProperties(
                registry,
                mockServerContainer.getSecureEndpoint());
        RouteTestUtil.setTestSftpServerProperties(
                registry,
                sftpContainer.getMappedPort(22));
        RouteTestUtil.setTestClientSendServiceProperties(
                registry);
    }

    @BeforeEach
    void setUp() throws Exception {
        RouteTestUtil.setUpMockClientSendService(
                camelContext,
                mockClientSendService);
    }

    @Test
    void exampleDocumentSignIntegrationTest() throws JsonProcessingException, InterruptedException {
        String contents = "Hello World";
        String documentId = "32767";
        String ownerId = "bigBank11";
        String documentType = "taxReport";
        String clientId = "32766";
        String fileName = documentId
                + "_" + ownerId
                + "_" + documentType
                + "_" + clientId + ".pdf";
        String status = "Read document";
        SignDocumentRequest expectedRequest = new SignDocumentRequest(
                Base64.getEncoder().encodeToString(contents.getBytes()),
                ownerId,
                TestConstants.SIGN_DOCUMENT_SIGN_TYPE,
                TestConstants.SIGN_DOCUMENT_API_KEY,
                documentType
        );
        ObjectWriter writer = jsonMapper.writer();
        String expectedSerializedRequest = writer.writeValueAsString(expectedRequest);
        String signDocumentResponseStatus = "SignDocumentResponse: Status ok";
        String responseMessage = "Document signed";
        SignDocumentResponse expectedResponse = new SignDocumentResponse(
                contents,
                signDocumentResponseStatus,
                responseMessage,
                LocalDateTime.now()
        );
        String mockSerializedResponse = writer.writeValueAsString(expectedResponse);
        RouteTestUtil.configureRegularSignServiceResponse(
                mockServerClient,
                expectedSerializedRequest,
                mockSerializedResponse
        );

        producerTemplate
                .withBody(contents)
                .withHeader(Exchange.FILE_NAME, fileName)
                .to(Endpoints.sftpServer())
                .send();

        awaitUntilLogAppearsInDBWithStatus(
                consumerTemplate,
                1L,
                TestConstants.CLIENT_SEND_RESPONSE_STATUS_OK,
                TestConstants.TEST_TIMEOUT);
        Map dbResult = consumerTemplate.receive(
                        "sql:select * from document_sign_log where id = 1",
                        TestConstants.TEST_TIMEOUT)
                .getMessage().getBody(Map.class);
        Exchange initialFileFromDoneFolderExchange = consumerTemplate.receive(
                Endpoints.getSftpEndpointUriWithDoneDirectory(), TestConstants.TEST_TIMEOUT);

        mockClientSendService.expectedMessageCount(1);
        mockClientSendService.expectedBodiesReceived(expectedSerializedRequest);
        mockClientSendService.assertIsSatisfied(TestConstants.TEST_TIMEOUT);

        mockServerClient.verify(request()
                        .withMethod("POST")
                        .withPath(TestConstants.SIGN_DOCUMENT_SERVICE_PATH)
                        .withBody(expectedSerializedRequest),
                exactly(1));

        assertEquals(32767, dbResult.get("document_number"));
        assertEquals(ownerId, dbResult.get("owner"));
        assertEquals(TestConstants.CLIENT_SEND_RESPONSE_STATUS_OK, dbResult.get("status"));
        assertNotNull(dbResult.get("last_update"));

        assertNotNull(initialFileFromDoneFolderExchange);
        Message initialFileFromDoneFolder = initialFileFromDoneFolderExchange.getMessage();
        assertEquals(fileName, initialFileFromDoneFolder.getHeader(Exchange.FILE_NAME));
        assertEquals(contents, initialFileFromDoneFolder.getBody(String.class));
    }

    @Test
    void exampleDocumentSignIntegrationTestExceptionInSecondRoute() {
        String contents = "BadValue";
        String documentId = "000";
        String ownerId = "BadValue";
        String documentType = "BadValue";
        String clientId = "000";
        String fileName = documentId
                + "_" + ownerId
                + "_" + documentType
                + "_" + clientId + ".pdf";

        producerTemplate
                .withBody(contents)
                .withHeader(Exchange.FILE_NAME, fileName)
                .to(Endpoints.sftpServer())
                .send();
        Exchange initialFileFromErrorFolderExchange = consumerTemplate.receive(
                Endpoints.getSftpEndpointUriWithErrorDirectory(), TestConstants.TEST_TIMEOUT);

        assertNotNull(initialFileFromErrorFolderExchange);
        Message initialFileFromErrorFolder = initialFileFromErrorFolderExchange.getMessage();
        assertEquals(fileName, initialFileFromErrorFolder.getHeader(Exchange.FILE_NAME));
        assertEquals(contents, initialFileFromErrorFolder.getBody(String.class));
    }
}
