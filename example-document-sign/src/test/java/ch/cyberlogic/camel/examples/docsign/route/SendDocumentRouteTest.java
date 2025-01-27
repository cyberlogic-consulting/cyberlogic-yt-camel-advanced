package ch.cyberlogic.camel.examples.docsign.route;

import ch.cyberlogic.camel.examples.docsign.model.ClientSendRequest;
import ch.cyberlogic.camel.examples.docsign.model.ClientSendResponse;
import ch.cyberlogic.camel.examples.docsign.model.SignDocumentResponse;
import ch.cyberlogic.camel.examples.docsign.service.ClientSendRequestMapper;
import ch.cyberlogic.camel.examples.docsign.util.AdviceWithUtilConfigurable;
import ch.cyberlogic.camel.examples.docsign.util.RouteTestUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import javax.sql.DataSource;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.ExcludeRoutes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static ch.cyberlogic.camel.examples.docsign.route.ReadDocumentRoute.DATABASE_LOG_ID;
import static org.apache.camel.builder.Builder.constant;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@CamelSpringBootTest
@ActiveProfiles("junit")
@ExcludeRoutes({ReadDocumentRoute.class, SignDocumentRoute.class})
@DirtiesContext
public class SendDocumentRouteTest {

    private static final String MOCK_SQL = "mock:sql";
    private static final String MOCK_ON_EXCEPTION_SQL = "mock:on-exception-sql";
    private static final String MOCK_JMS_SEND_SERVICE = "mock:jms-send-service";
    private static final String MOCK_ROUTE_FINISHED = "mock:send-document-finished";
    private static final String TEST_START = "direct:send-document-start";

    @Autowired
    private CamelContext camelContext;

    @Autowired
    XmlMapper xmlMapper;

    @Produce(TEST_START)
    private ProducerTemplate producerTemplate;

    @MockBean
    private ClientSendRequestMapper clientSendRequestMapper;

    @MockBean
    DataSource dataSource;

    @EndpointInject(MOCK_ROUTE_FINISHED)
    private MockEndpoint mockRouteFinished;

    @EndpointInject(MOCK_SQL)
    private MockEndpoint mockSql;

    @EndpointInject(MOCK_JMS_SEND_SERVICE)
    private MockEndpoint mockJmsSendService;

    @EndpointInject(MOCK_ON_EXCEPTION_SQL)
    private MockEndpoint mockOnExceptionSql;

    @BeforeEach
    void replaceEndpoints() throws Exception {
        RouteTestUtil.resetMockEndpoints(
                mockSql,
                mockJmsSendService,
                mockRouteFinished,
                mockOnExceptionSql
        );
        AdviceWithUtilConfigurable advice = new AdviceWithUtilConfigurable(
                camelContext,
                SendDocumentRoute.ROUTE_ID
        );
        advice.replaceFromWith(TEST_START);
        advice.replaceEndpoint("sql:" +
                        "update document_sign_log set status=:#${body.getStatus}, last_update=:#${date:now} " +
                        "where id=:#${headers." + ReadDocumentRoute.DATABASE_LOG_ID + "}",
                MOCK_SQL);
        advice.replaceEndpoint(
                "jms:*",
                MOCK_JMS_SEND_SERVICE);
        advice.replaceEndpoint(
                ErrorHandlingConfiguration.SQL_WRITE_EXCEPTION_ENDPOINT,
                MOCK_ON_EXCEPTION_SQL
        );
        advice.addEndpointOnRouteCompletion(MOCK_ROUTE_FINISHED);
    }

    @Test
    void signDocumentRouteTest() throws JsonProcessingException, InterruptedException {
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
        byte[] expectedSerializedRequest = writer.writeValueAsBytes(expectedRequest);
        String status = "Status ok";
        String responseMessage = "Document signed";
        ClientSendResponse expectedResponse = new ClientSendResponse(
                status,
                responseMessage,
                LocalDateTime.now()
        );
        mockJmsSendService.returnReplyBody(constant(writer.writeValueAsBytes(expectedResponse)));

        producerTemplate.sendBody(expectedRequest);

        verify(clientSendRequestMapper, times(1)).prepareClientSendRequest(any());

        mockJmsSendService.expectedMessageCount(1);
        mockJmsSendService.expectedBodiesReceived(List.of(expectedSerializedRequest));

        mockSql.expectedMessageCount(1);

        mockRouteFinished.expectedMessageCount(1);
        mockRouteFinished.expectedBodiesReceived(expectedResponse);

        mockOnExceptionSql.expectedMessageCount(0);

        RouteTestUtil.checkAssertionsSatisfied(
                mockJmsSendService,
                mockSql,
                mockRouteFinished,
                mockOnExceptionSql
        );
    }

    @Test
    void signDocumentRouteExceptionTest() throws InterruptedException {
        String expectedBody = "TestBody";
        String dbLogId = "1";
        RuntimeException expected = new RuntimeException("Test Exception");
        doThrow(expected).when(clientSendRequestMapper).prepareClientSendRequest(any());

        assertThrows(expected.getClass(), () -> producerTemplate.sendBodyAndHeader(
                expectedBody,
                DATABASE_LOG_ID,
                dbLogId)
        );


        mockJmsSendService.expectedMessageCount(0);
        mockSql.expectedMessageCount(0);
        mockRouteFinished.expectedMessageCount(0);
        mockOnExceptionSql.expectedMessageCount(1);
        mockOnExceptionSql.expectedHeaderReceived(DATABASE_LOG_ID, dbLogId);
        mockOnExceptionSql.expectedBodiesReceived(expectedBody);
        mockOnExceptionSql.expectedMessagesMatches(message ->
                message.getException().equals(expected));

        RouteTestUtil.checkAssertionsSatisfied(
                mockJmsSendService,
                mockSql,
                mockRouteFinished,
                mockOnExceptionSql
        );
    }
}
