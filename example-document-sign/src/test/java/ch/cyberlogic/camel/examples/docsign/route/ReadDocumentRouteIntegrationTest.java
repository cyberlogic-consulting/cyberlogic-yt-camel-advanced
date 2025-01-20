package ch.cyberlogic.camel.examples.docsign.route;

import ch.cyberlogic.camel.examples.docsign.service.ExchangeTransformer;
import java.util.Map;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
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
import org.springframework.test.context.junit.jupiter.EnabledIf;
import org.testcontainers.activemq.ArtemisContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

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

    private static final int TEST_TIMEOUT = 5000;

    private static final String USER = "user";
    private static final String PASSWORD = "password";

    private static final String SFTP_DIR = "documents";

    private static final String DB_NAME = "integration";


    static GenericContainer<?> sftpContainer = new GenericContainer<>("atmoz/sftp:alpine")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("it/ssh_host_ed25519_key", 0777),
                    "/etc/ssh/ssh_host_ed25519_key"
            )
            .withExposedPorts(22)
            .withCommand(USER + ":" + PASSWORD + ":::" + SFTP_DIR);

    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:17.2-alpine")
            .withDatabaseName(DB_NAME)
            .withUsername(USER)
            .withPassword(PASSWORD)
            .withInitScript("it/db/init.sql");

    @ServiceConnection
    static ArtemisContainer artemisContainer = new ArtemisContainer("apache/activemq-artemis:2.39.0-alpine");

    private static final String MOCK_SIGN_DOCUMENT = "mock:" + SignDocumentRoute.INPUT_ENDPOINT;

    @Autowired
    private ProducerTemplate producerTemplate;

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
        registry.add("sftp.server.directory", () -> SFTP_DIR);
        registry.add("sftp.server.user", () -> USER);
        registry.add("sftp.server.password", () -> PASSWORD);
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

        producerTemplate.sendBodyAndHeader("sftp://{{sftp.server.host}}:{{sftp.server.port}}/{{sftp.server.directory}}" +
                        "?username={{sftp.server.user}}&password={{sftp.server.password}}" +
                        "&knownHostsFile={{sftp.server.known_hosts}}",
                contents,
                Exchange.FILE_NAME,
                fileName);
        Map dbResult = consumerTemplate.receive(
                        "sql:select * from document_sign_log " +
                                "where document_number = ' " + documentId + "'",
                                TEST_TIMEOUT)
                .getMessage().getBody(Map.class);


        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived(contents);
        mock.expectedHeaderReceived(Exchange.FILE_NAME, fileName);
        mock.expectedHeaderReceived(ExchangeTransformer.DOCUMENT_ID, documentId);
        mock.expectedHeaderReceived(ExchangeTransformer.OWNER_ID, ownerId);
        mock.expectedHeaderReceived(ExchangeTransformer.DOCUMENT_TYPE, documentType);
        mock.expectedHeaderReceived(ExchangeTransformer.CLIENT_ID, clientId);
        mock.expectedMessagesMatches(
                exchange -> exchange.getMessage().getHeader(ReadDocumentRoute.DATABASE_LOG_ID) != null);

        mock.assertIsSatisfied(TEST_TIMEOUT);

        assertEquals(documentId, dbResult.get("document_number").toString());
        assertEquals(ownerId, dbResult.get("owner"));
        assertEquals("Read document", dbResult.get("status"));
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

        producerTemplate.sendBodyAndHeader("sftp://{{sftp.server.host}}:{{sftp.server.port}}/{{sftp.server.directory}}" +
                        "?username={{sftp.server.user}}&password={{sftp.server.password}}" +
                        "&knownHostsFile={{sftp.server.known_hosts}}",
                contents,
                Exchange.FILE_NAME,
                fileName);
        Exchange initialFileFromErrorFolderExchange = consumerTemplate.receive(
                        "sftp://{{sftp.server.host}}:{{sftp.server.port}}/{{sftp.server.directory}}/.processing/.error" +
                                "?username={{sftp.server.user}}&password={{sftp.server.password}}" +
                                "&knownHostsFile={{sftp.server.known_hosts}}", TEST_TIMEOUT);



        mock.expectedMessageCount(0);
        mock.assertIsSatisfied();

        assertNotNull(initialFileFromErrorFolderExchange);
        Message initialFileFromErrorFolder = initialFileFromErrorFolderExchange.getMessage();
        assertEquals(fileName, initialFileFromErrorFolder.getHeader(Exchange.FILE_NAME));
        assertEquals(contents, initialFileFromErrorFolder.getBody(String.class));
    }
}
