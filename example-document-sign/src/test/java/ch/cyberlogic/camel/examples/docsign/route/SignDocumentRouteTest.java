package ch.cyberlogic.camel.examples.docsign.route;

import ch.cyberlogic.camel.examples.docsign.model.SignDocumentRequest;
import ch.cyberlogic.camel.examples.docsign.model.SignDocumentResponse;
import ch.cyberlogic.camel.examples.docsign.service.SignDocumentRequestMapper;
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
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.ExcludeRoutes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.apache.camel.builder.Builder.constant;
import static org.apache.camel.builder.endpoint.StaticEndpointBuilders.https;
import static org.mockito.ArgumentMatchers.any;
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
    private static final String MOCK_HTTP_SIGN_SERVICE = "mock:http-sign-service";
    private static final String DIRECT_START = "direct:sign-document-start";

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ObjectMapper jsonMapper;

    @Produce(DIRECT_START)
    private ProducerTemplate producerTemplate;

    @MockitoBean
    private SignDocumentRequestMapper signDocumentRequestMapper;

    @MockitoBean
    DataSource dataSource;

    @EndpointInject(MOCK_SEND_DOCUMENT)
    private MockEndpoint mockSignDocument;

    @EndpointInject(MOCK_SQL)
    private MockEndpoint mockSql;

    @EndpointInject(MOCK_HTTP_SIGN_SERVICE)
    private MockEndpoint mockHttpSignService;

    @BeforeEach
    void replaceEndpoints() throws Exception {
        AdviceWith.adviceWith(
                camelContext,
                SignDocumentRoute.ROUTE_ID,
                route -> route
                        .replaceFromWith(DIRECT_START)
        );
        RouteTestUtil.replaceEndpoint(
                camelContext,
                SignDocumentRoute.ROUTE_ID,
                "sql:" +
                        "update document_sign_log set status=:#${body.getStatus}, last_update=:#${date:now} " +
                        "where id=:#${headers." + ReadDocumentRoute.DATABASE_LOG_ID + "}",
                MOCK_SQL
        );
        RouteTestUtil.replaceEndpoint(
                camelContext,
                SignDocumentRoute.ROUTE_ID,
                https("{{signDocument.serviceUrl}}")
                        .sslContextParameters("customCertificateSslContextParameters")
                        .skipRequestHeaders(true)
                        .getRawUri(),
                MOCK_HTTP_SIGN_SERVICE
        );
        RouteTestUtil.replaceEndpoint(
                camelContext,
                SignDocumentRoute.ROUTE_ID,
                SendDocumentRoute.INPUT_ENDPOINT,
                MOCK_SEND_DOCUMENT
        );
    }

    @Test
    void signDocumentRouteTest() throws JsonProcessingException {
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

        camelContext.createProducerTemplate().sendBody(DIRECT_START, expectedRequest);

        verify(signDocumentRequestMapper, times(1)).prepareSignDocumentRequest(any());

        mockHttpSignService.expectedMessageCount(1);
        mockHttpSignService.expectedBodiesReceived(List.of(expectedSerializedRequest));

        mockSql.expectedMessageCount(1);

        mockSignDocument.expectedMessageCount(1);
        mockSignDocument.expectedBodiesReceived(expectedResponse);
    }
}
