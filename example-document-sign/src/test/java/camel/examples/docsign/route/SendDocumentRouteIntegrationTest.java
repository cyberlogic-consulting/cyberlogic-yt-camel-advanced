package camel.examples.docsign.route;

import camel.examples.docsign.model.ClientSendRequest;
import camel.examples.docsign.model.ClientSendResponse;
import camel.examples.docsign.model.SignDocumentResponse;
import camel.examples.docsign.service.ExchangeTransformer;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.EndpointInject;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.builder.RouteBuilder;
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
import org.testcontainers.containers.PostgreSQLContainer;

import static org.apache.camel.component.jms.JmsConstants.JMS_HEADER_REPLY_TO;
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

    private static final int TEST_TIMEOUT = 5000;

    private static final String USER = "user";
    private static final String PASSWORD = "password";
    private static final String DB_NAME = "integration";
    private static final String SEND_DOCUMENT_REQUEST_QUEUE = "client.send.request.queue";
    private static final String SEND_DOCUMENT_RESPONSE_QUEUE = "client.send.response.queue";

    private static final String CLIENT_SEND_RESPONSE_STATUS = "ClientSendRequest: Status ok";
    private static final String CLIENT_SEND_RESPONSE_MESSAGE = "Document sent";
    private static final LocalDateTime CLIENT_SEND_RESPONSE_TIMESTAMP = LocalDateTime.now();

    @ServiceConnection
    static ArtemisContainer artemisContainer = new ArtemisContainer("apache/activemq-artemis:2.39.0-alpine");

    @ServiceConnection
    static PostgreSQLContainer<?> postgreSQLContainer = new PostgreSQLContainer<>("postgres:17.2-alpine")
            .withDatabaseName(DB_NAME)
            .withUsername(USER)
            .withPassword(PASSWORD)
            .withInitScripts(List.of("it/db/init.sql", "it/db/add-row.sql"));

    private static final String MOCK_ROUTE_FINISHED = "mock:send-document-finished";

    private static final String MOCK_CLIENT_SEND_SERVICE = "mock:client-send-service";

    @Autowired
    private ProducerTemplate producerTemplate;

    @Autowired
    private ConsumerTemplate consumerTemplate;

    @EndpointInject(MOCK_ROUTE_FINISHED)
    private MockEndpoint mockRouteFinished;

    @EndpointInject(MOCK_CLIENT_SEND_SERVICE)
    private MockEndpoint mockClientSendService;

    @Autowired
    private CamelContext camelContext;

    @Autowired
    XmlMapper xmlMapper;

    @DynamicPropertySource
    static void setUpProperties(DynamicPropertyRegistry registry) {
        registry.add("clientSend.clientSendRequestQueue", () -> SEND_DOCUMENT_REQUEST_QUEUE);
        registry.add("clientSend.clientSendResponseQueue", () -> SEND_DOCUMENT_RESPONSE_QUEUE);
    }

    @BeforeEach
    void setUp() throws Exception {
        mockRouteFinished.reset();
        mockClientSendService.reset();

        AdviceWith.adviceWith(
                camelContext,
                SendDocumentRoute.ROUTE_ID,
                route -> route
                        .onCompletion()
                        .to(MOCK_ROUTE_FINISHED)
        );

        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("jms:" + SEND_DOCUMENT_REQUEST_QUEUE)
                        .id("MockClientSendService")
                        .log("MockClientSendService received request: ${body}; RequestHeaders: ${headers}")
                        .to(MOCK_CLIENT_SEND_SERVICE)
                        .setBody((exchange -> new ClientSendResponse(
                                CLIENT_SEND_RESPONSE_STATUS,
                                CLIENT_SEND_RESPONSE_MESSAGE,
                                CLIENT_SEND_RESPONSE_TIMESTAMP
                        )))
                        .marshal().jacksonXml()
                        .toD()
                        .pattern(ExchangePattern.InOnly)
                        .uri("jms:${header." + JMS_HEADER_REPLY_TO + "}?preserveMessageQos=true");
            }
        });
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
                CLIENT_SEND_RESPONSE_STATUS,
                CLIENT_SEND_RESPONSE_MESSAGE,
                CLIENT_SEND_RESPONSE_TIMESTAMP
        );

        producerTemplate.sendBodyAndHeaders(
                SendDocumentRoute.INPUT_ENDPOINT,
                signDocumentResponse,
                Map.of(
                        ExchangeTransformer.DOCUMENT_ID, documentId,
                        ExchangeTransformer.OWNER_ID, ownerId,
                        ExchangeTransformer.CLIENT_ID, clientId,
                        ReadDocumentRoute.DATABASE_LOG_ID, 1
                ));
        Thread.sleep(1000);

        Map dbResult = consumerTemplate.receive(
                        "sql:select * from document_sign_log where id = 1",
                        TEST_TIMEOUT)
                .getMessage().getBody(Map.class);

        mockClientSendService.expectedMessageCount(1);
        mockClientSendService.expectedBodiesReceived(expectedSerializedRequest);
        mockClientSendService.assertIsSatisfied(TEST_TIMEOUT);

        mockRouteFinished.expectedMessageCount(1);
        mockRouteFinished.expectedBodiesReceived(expectedResponse);
        mockRouteFinished.assertIsSatisfied(TEST_TIMEOUT);

        assertEquals(32767, dbResult.get("document_number"));
        assertEquals(ownerId, dbResult.get("owner"));
        assertEquals(CLIENT_SEND_RESPONSE_STATUS, dbResult.get("status"));
        assertNotNull(dbResult.get("last_update"));
    }
}
