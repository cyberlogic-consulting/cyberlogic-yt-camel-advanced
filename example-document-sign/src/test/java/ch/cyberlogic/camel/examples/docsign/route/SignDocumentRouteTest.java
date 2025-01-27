package ch.cyberlogic.camel.examples.docsign.route;

import ch.cyberlogic.camel.examples.docsign.model.SignDocumentRequest;
import ch.cyberlogic.camel.examples.docsign.model.SignDocumentResponse;
import ch.cyberlogic.camel.examples.docsign.service.SignDocumentRequestMapper;
import ch.cyberlogic.camel.examples.docsign.util.AdviceWithUtilConfigurable;
import ch.cyberlogic.camel.examples.docsign.util.RouteTestUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
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
import static org.apache.camel.builder.endpoint.StaticEndpointBuilders.https;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@CamelSpringBootTest
@ActiveProfiles("junit")
@ExcludeRoutes({ReadDocumentRoute.class, SendDocumentRoute.class})
@DirtiesContext
public class SignDocumentRouteTest {

    private static final String MOCK_SEND_DOCUMENT = "mock:" + SendDocumentRoute.INPUT_ENDPOINT;
    private static final String MOCK_SQL = "mock:sql";
    private static final String MOCK_ON_EXCEPTION_SQL = "mock:on-exception-sql";
    private static final String MOCK_HTTP_SIGN_SERVICE = "mock:http-sign-service";
    private static final String TEST_START = "direct:sign-document-start";

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ObjectMapper jsonMapper;

    @Produce(TEST_START)
    private ProducerTemplate producerTemplate;

    @MockBean
    private SignDocumentRequestMapper signDocumentRequestMapper;

    @MockBean
    DataSource dataSource;

    @EndpointInject(MOCK_SEND_DOCUMENT)
    private MockEndpoint mockSendDocument;

    @EndpointInject(MOCK_SQL)
    private MockEndpoint mockSql;

    @EndpointInject(MOCK_HTTP_SIGN_SERVICE)
    private MockEndpoint mockHttpSignService;

    @EndpointInject(MOCK_ON_EXCEPTION_SQL)
    private MockEndpoint mockOnExceptionSql;

    @BeforeEach
    void replaceEndpoints() throws Exception {
        RouteTestUtil.resetMockEndpoints(
                mockSql,
                mockHttpSignService,
                mockSendDocument,
                mockOnExceptionSql
        );
        AdviceWithUtilConfigurable advice = new AdviceWithUtilConfigurable(
                camelContext,
                SignDocumentRoute.ROUTE_ID
        );
        advice.replaceFromWith(TEST_START);
        advice.replaceEndpoint("sql:" +
                        "update document_sign_log set status=:#${body.getStatus}, last_update=:#${date:now} " +
                        "where id=:#${headers." + ReadDocumentRoute.DATABASE_LOG_ID + "}",
                MOCK_SQL);
        advice.replaceEndpoint(https("{{signDocument.serviceUrl}}")
                        .sslContextParameters("customCertificateSslContextParameters")
                        .skipRequestHeaders(true)
                        .getRawUri(),
                MOCK_HTTP_SIGN_SERVICE);
        advice.replaceEndpoint(
                SendDocumentRoute.INPUT_ENDPOINT,
                MOCK_SEND_DOCUMENT);
        advice.replaceEndpoint(
                ErrorHandlingConfiguration.SQL_WRITE_EXCEPTION_ENDPOINT,
                MOCK_ON_EXCEPTION_SQL
        );
    }

    @Test
    void signDocumentRouteTest() throws JsonProcessingException, InterruptedException {
        String contents = "Hello World";
        String ownerId = "bigBank11";
        String documentType = "taxReport";
        String signType = "CAdES-C";
        String apiKey = "acb93ac2-2dce-4209-8ef2-2188ce2047c2";
        SignDocumentRequest expectedRequest = new SignDocumentRequest(
                Base64.getEncoder().encodeToString(contents.getBytes()),
                ownerId,
                signType,
                apiKey,
                documentType
        );
        ObjectWriter writer = jsonMapper.writer();
        byte[] expectedSerializedRequest = writer.writeValueAsBytes(expectedRequest);
        String status = "Status ok";
        String responseMessage = "Document signed";
        SignDocumentResponse expectedResponse = new SignDocumentResponse(
                contents,
                status,
                responseMessage,
                LocalDateTime.now()
        );
        mockHttpSignService.returnReplyBody(constant(writer.writeValueAsBytes(expectedResponse)));

        producerTemplate.sendBody(expectedRequest);

        verify(signDocumentRequestMapper, times(1)).prepareSignDocumentRequest(any());

        mockHttpSignService.expectedMessageCount(1);
        mockHttpSignService.expectedBodiesReceived(List.of(expectedSerializedRequest));

        mockSql.expectedMessageCount(1);

        mockSendDocument.expectedMessageCount(1);
        mockSendDocument.expectedBodiesReceived(expectedResponse);

        mockOnExceptionSql.expectedMessageCount(0);

        RouteTestUtil.checkAssertionsSatisfied(
                mockHttpSignService,
                mockSql,
                mockSendDocument,
                mockOnExceptionSql
        );
    }

    @Test
    void signDocumentRouteExceptionTest() throws InterruptedException {
        String expectedBody = "TestBody";
        String dbLogId = "1";
        RuntimeException expected = new RuntimeException("Test Exception");
        doThrow(expected).when(signDocumentRequestMapper).prepareSignDocumentRequest(any());

        assertThrows(expected.getClass(), () -> producerTemplate.sendBodyAndHeader(
                expectedBody,
                DATABASE_LOG_ID,
                dbLogId)
        );

        mockHttpSignService.expectedMessageCount(0);
        mockSql.expectedMessageCount(0);
        mockSendDocument.expectedMessageCount(0);
        mockOnExceptionSql.expectedMessageCount(1);
        mockOnExceptionSql.expectedHeaderReceived(DATABASE_LOG_ID, dbLogId);
        mockOnExceptionSql.expectedBodiesReceived(expectedBody);
        mockOnExceptionSql.expectedMessagesMatches(message ->
                message.getException().equals(expected));

        RouteTestUtil.checkAssertionsSatisfied(
                mockHttpSignService,
                mockSql,
                mockSendDocument,
                mockOnExceptionSql
        );
    }
}
