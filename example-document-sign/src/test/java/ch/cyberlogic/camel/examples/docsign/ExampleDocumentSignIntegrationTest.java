package ch.cyberlogic.camel.examples.docsign;

import ch.cyberlogic.camel.examples.docsign.route.SendDocumentRoute;
import ch.cyberlogic.camel.examples.docsign.service.SignDocumentRequestMapper;
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

import static ch.cyberlogic.camel.examples.docsign.util.containers.TestContainersConfigurations.getConfiguredArtemisContainer;
import static ch.cyberlogic.camel.examples.docsign.util.containers.TestContainersConfigurations.getConfiguredMockServerContainer;
import static ch.cyberlogic.camel.examples.docsign.util.containers.TestContainersConfigurations.getConfiguredPostgreSQLContainer;
import static ch.cyberlogic.camel.examples.docsign.util.containers.TestContainersConfigurations.getConfiguredSftpContainer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
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

    static GenericContainer<?> sftpContainer = getConfiguredSftpContainer(USER, PASSWORD, SFTP_DIR);

    @ServiceConnection
    static ArtemisContainer artemisContainer = getConfiguredArtemisContainer();

    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer = getConfiguredPostgreSQLContainer(
            USER,
            PASSWORD,
            DB_NAME
    );

    static MockServerContainer mockServerContainer = getConfiguredMockServerContainer();

    @MockitoBean
    private SignDocumentRequestMapper signDocumentRequestMapper;

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
        registry.add("sftp.server.known_hosts", () -> "src/test/resources/it/known_hosts");
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
        doThrow(new RuntimeException("ABOBA")).when(signDocumentRequestMapper).prepareSignDocumentRequest(any());

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
