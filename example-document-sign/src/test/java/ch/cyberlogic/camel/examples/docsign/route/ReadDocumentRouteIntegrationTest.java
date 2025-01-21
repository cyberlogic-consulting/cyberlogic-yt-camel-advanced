package ch.cyberlogic.camel.examples.docsign.route;

import ch.cyberlogic.camel.examples.docsign.configuration.SSLContextParamsConfiguration;
import ch.cyberlogic.camel.examples.docsign.service.SignDocumentRequestMapper;
import ch.cyberlogic.camel.examples.docsign.util.TestConstants;
import ch.cyberlogic.camel.examples.docsign.util.endpoints.Endpoints;
import java.util.Map;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.Message;
import org.apache.camel.builder.AdviceWith;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import static ch.cyberlogic.camel.examples.docsign.service.FileMetadataExtractor.CLIENT_ID;
import static ch.cyberlogic.camel.examples.docsign.service.FileMetadataExtractor.DOCUMENT_ID;
import static ch.cyberlogic.camel.examples.docsign.service.FileMetadataExtractor.DOCUMENT_TYPE;
import static ch.cyberlogic.camel.examples.docsign.service.FileMetadataExtractor.OWNER_ID;
import static ch.cyberlogic.camel.examples.docsign.util.RouteTestUtil.awaitUntilLogAppearsInDBWithDocumentId;
import static ch.cyberlogic.camel.examples.docsign.util.containers.TestContainersConfigurations.getConfiguredArtemisContainer;
import static ch.cyberlogic.camel.examples.docsign.util.containers.TestContainersConfigurations.getConfiguredPostgreSQLContainer;
import static ch.cyberlogic.camel.examples.docsign.util.containers.TestContainersConfigurations.getConfiguredSftpContainer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@EnabledIf("${test.integration.enabled:false}")
@CamelSpringBootTest
@ExcludeRoutes({SignDocumentRoute.class, SendDocumentRoute.class})
@SpringBootTest
@ActiveProfiles("it")
@ImportTestcontainers
@DirtiesContext
public class ReadDocumentRouteIntegrationTest {

    private static final String MOCK_SIGN_DOCUMENT = "mock:" + SignDocumentRoute.INPUT_ENDPOINT;

    static GenericContainer<?> sftpContainer = getConfiguredSftpContainer(
            TestConstants.USER,
            TestConstants.PASSWORD,
            TestConstants.SFTP_DIR);

    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer = getConfiguredPostgreSQLContainer(
            TestConstants.USER,
            TestConstants.PASSWORD,
            TestConstants.DB_NAME);

    @ServiceConnection
    static ArtemisContainer artemisContainer = getConfiguredArtemisContainer();

    @MockitoBean
    private SSLContextParamsConfiguration excludedSSLContextParamsConfiguration;

    @MockitoBean
    private SignDocumentRequestMapper excludedSignDocumentRequestMapper;

    @Autowired
    private FluentProducerTemplate producerTemplate;

    @Autowired
    private ConsumerTemplate consumerTemplate;

    @EndpointInject(MOCK_SIGN_DOCUMENT)
    private MockEndpoint mock;

    @Autowired
    private CamelContext camelContext;

    @DynamicPropertySource
    static void setUpProperties(DynamicPropertyRegistry registry) {
        registry.add("sftp.server.host", () -> "localhost");
        registry.add("sftp.server.port", () -> sftpContainer.getMappedPort(22));
        registry.add("sftp.server.directory", () -> TestConstants.SFTP_DIR);
        registry.add("sftp.server.user", () -> TestConstants.USER);
        registry.add("sftp.server.password", () -> TestConstants.PASSWORD);
        registry.add("sftp.server.known_hosts", () -> "src/test/resources/it/known_hosts");
    }

    @BeforeEach
    void setUp() throws Exception {
        mock.reset();
        AdviceWith.adviceWith(
                camelContext,
                ReadDocumentRoute.ROUTE_ID,
                route -> route
                        .interceptSendToEndpoint(SignDocumentRoute.INPUT_ENDPOINT)
                        .skipSendToOriginalEndpoint()
                        .to(MOCK_SIGN_DOCUMENT)
        );
    }

    @Test
    void readDocumentRouteTest() throws Exception {
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

        producerTemplate
                .withBody(contents)
                .withHeader(Exchange.FILE_NAME, fileName)
                .to(Endpoints.sftpServer())
                .send();
        awaitUntilLogAppearsInDBWithDocumentId(
                consumerTemplate,
                Integer.valueOf(documentId),
                TestConstants.TEST_TIMEOUT);
        Map dbResult = consumerTemplate.receive(
                        "sql:select * from document_sign_log " +
                                "where document_number = ' " + documentId + "'",
                        TestConstants.TEST_TIMEOUT)
                .getMessage().getBody(Map.class);

        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(contents);
        mock.expectedHeaderReceived(Exchange.FILE_NAME, fileName);
        mock.expectedHeaderReceived(DOCUMENT_ID, documentId);
        mock.expectedHeaderReceived(OWNER_ID, ownerId);
        mock.expectedHeaderReceived(DOCUMENT_TYPE, documentType);
        mock.expectedHeaderReceived(CLIENT_ID, clientId);
        mock.expectedMessagesMatches(
                exchange -> exchange.getMessage().getHeader(ReadDocumentRoute.DATABASE_LOG_ID) != null);

        mock.assertIsSatisfied(TestConstants.TEST_TIMEOUT);

        assertEquals(documentId, dbResult.get("document_number").toString());
        assertEquals(ownerId, dbResult.get("owner"));
        assertEquals(status, dbResult.get("status"));
        assertNotNull(dbResult.get("last_update"));
    }

    @Test
    void readDocumentRouteTestInvalidFileName() throws Exception {
        String contents = "Hello World";
        String ownerId = "bigBank11";
        String documentType = "taxReport";
        String clientId = "32766";
        String fileName = ownerId
                + "_" + documentType
                + "_" + clientId + ".pdf";

        producerTemplate
                .withBody(contents)
                .withHeader(Exchange.FILE_NAME, fileName)
                .to(Endpoints.sftpServer())
                .send();


        Exchange initialFileFromErrorFolderExchange =
                consumerTemplate.receive(getSftpEndpointWithErrorDirectory(), TestConstants.TEST_TIMEOUT);


        mock.expectedMessageCount(0);
        mock.assertIsSatisfied();

        assertNotNull(initialFileFromErrorFolderExchange);
        Message initialFileFromErrorFolder = initialFileFromErrorFolderExchange.getMessage();
        assertEquals(fileName, initialFileFromErrorFolder.getHeader(Exchange.FILE_NAME));
        assertEquals(contents, initialFileFromErrorFolder.getBody(String.class));
    }

    private String getSftpEndpointWithErrorDirectory() {
        return Endpoints.sftpServer().getRawUri()
                .replace("{{sftp.server.directory}}", "{{sftp.server.directory}}/.processing/.error");
    }
}
