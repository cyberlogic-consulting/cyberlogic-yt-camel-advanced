package ch.cyberlogic.camel.examples.docsign;

import ch.cyberlogic.camel.examples.docsign.route.SendDocumentRoute;
import ch.cyberlogic.camel.examples.docsign.service.ExchangeTransformer;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.ExcludeRoutes;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.EnabledIf;
import org.testcontainers.activemq.ArtemisContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;

@EnabledIf("${test.integration.enabled:false}")
@CamelSpringBootTest
@ExcludeRoutes({SendDocumentRoute.class})
@SpringBootTest
@ActiveProfiles("it")
@ImportTestcontainers
@DirtiesContext
public class ExampleDocumentSignIntegrationTest {

    private static final int TEST_TIMEOUT = 5000;

    private static final String USER = "user";
    private static final String PASSWORD = "password";

    private static final String SFTP_DIR = "documents";

    private static final String DB_NAME = "integration";

    private static final String SIGN_DOCUMENT_SERVICE_PATH = "/SignDocument/sign";
    private static final String SIGN_DOCUMENT_API_KEY = "acb93ac2-2dce-4209-8ef2-2188ce2047c2";
    private static final String SIGN_DOCUMENT_SIGN_TYPE = "CAdES-C";
    private static final String SIGN_DOCUMENT_TRUSTSTORE_PASSWORD = "password";
    private static final String SIGN_DOCUMENT_TRUSTSTORE = "it/sign_document_service/test_truststore.jks";

    static MockServerClient mockServerClient;


    static GenericContainer<?> sftpContainer = new GenericContainer<>("atmoz/sftp:alpine")
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource("it/ssh_host_ed25519_key", 0777),
                    "/etc/ssh/ssh_host_ed25519_key"
            )
            .withExposedPorts(22)
            .withCommand(USER + ":" + PASSWORD + ":::" + SFTP_DIR);

    @ServiceConnection
    static ArtemisContainer artemisContainer = new ArtemisContainer("apache/activemq-artemis:2.39.0-alpine");

    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:17.2-alpine")
            .withDatabaseName(DB_NAME)
            .withUsername(USER)
            .withPassword(PASSWORD)
            .withInitScript("it/db/init.sql");

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

    @MockitoBean
    private ExchangeTransformer exchangeTransformer;

    @Autowired
    private ProducerTemplate producerTemplate;

    @Autowired
    private ConsumerTemplate consumerTemplate;

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

        registry.add("sftp.server.host", () -> "localhost");
        registry.add("sftp.server.port", () -> sftpContainer.getMappedPort(22));
        registry.add("sftp.server.directory", () -> SFTP_DIR);
        registry.add("sftp.server.user", () -> USER);
        registry.add("sftp.server.password", () -> PASSWORD);
    }

    @Test
    void exampleDocumentSignIntegrationTest() {

    }

    @Test
    void exampleDocumentSignIntegrationTestExceptionInSecondRoute() {
        String contents = "Hello World";
        String documentId = "32767";
        String ownerId = "bigBank11";
        String documentType = "taxReport";
        String clientId = "32766";
        String fileName = documentId
                + "_" + ownerId
                + "_" + documentType
                + "_" + clientId + ".pdf";
        doCallRealMethod().when(exchangeTransformer).extractFileMetadata(any());
        doThrow(new RuntimeException("ABOBA")).when(exchangeTransformer).prepareSignDocumentRequest(any());

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

        assertNotNull(initialFileFromErrorFolderExchange);
        Message initialFileFromErrorFolder = initialFileFromErrorFolderExchange.getMessage();
        assertEquals(fileName, initialFileFromErrorFolder.getHeader(Exchange.FILE_NAME));
        assertEquals(contents, initialFileFromErrorFolder.getBody(String.class));
    }
}
