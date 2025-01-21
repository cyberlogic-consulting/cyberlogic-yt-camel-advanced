package ch.cyberlogic.camel.examples.docsign;

import ch.cyberlogic.camel.examples.docsign.route.SendDocumentRoute;
import ch.cyberlogic.camel.examples.docsign.service.SignDocumentRequestMapper;
import ch.cyberlogic.camel.examples.docsign.util.RouteTestUtil;
import ch.cyberlogic.camel.examples.docsign.util.TestConstants;
import ch.cyberlogic.camel.examples.docsign.util.endpoints.Endpoints;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.Exchange;
import org.apache.camel.FluentProducerTemplate;
import org.apache.camel.Message;
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

    @MockitoBean
    private SignDocumentRequestMapper signDocumentRequestMapper;

    @Autowired
    private FluentProducerTemplate producerTemplate;

    @Autowired
    private ConsumerTemplate consumerTemplate;

    @DynamicPropertySource
    static void setUpProperties(DynamicPropertyRegistry registry) {
        mockServerContainer.start();
        mockServerClient = new MockServerClient(
                mockServerContainer.getHost(),
                mockServerContainer.getServerPort()
        );
        RouteTestUtil.setTestSignDocumentServiceProperties(
                registry,
                mockServerContainer.getSecureEndpoint());
        RouteTestUtil.setTestSftpServerProperties(registry, sftpContainer.getMappedPort(22));
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
        doThrow(new RuntimeException("Exception happened in the 2nd route")).when(signDocumentRequestMapper).prepareSignDocumentRequest(any());

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
