package ch.cyberlogic.camel.examples.docsign.route;

import ch.cyberlogic.camel.examples.docsign.model.ClientSendRequest;
import ch.cyberlogic.camel.examples.docsign.model.ClientSendResponse;
import ch.cyberlogic.camel.examples.docsign.model.SignDocumentResponse;
import ch.cyberlogic.camel.examples.docsign.service.ExchangeTransformer;
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
import static org.apache.camel.builder.endpoint.StaticEndpointBuilders.jms;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@CamelSpringBootTest
@ActiveProfiles("junit")
@ExcludeRoutes({ReadDocumentRoute.class, SignDocumentRoute.class})
@DirtiesContext
public class SendDocumentRouteTest {

    private static final String MOCK_SQL = "mock:sql";
    private static final String MOCK_JMS_SEND_SERVICE = "mock:jms-send-service";
    private static final String MOCK_ROUTE_FINISHED = "mock:send-document-finished";
    private static final String TEST_START = "direct:send-document-start";

    @Autowired
    private CamelContext camelContext;

    @Autowired
    XmlMapper xmlMapper;

    @Produce(TEST_START)
    private ProducerTemplate producerTemplate;

    @MockitoBean
    private ExchangeTransformer exchangeTransformer;

    @MockitoBean
    DataSource dataSource;

    @EndpointInject(MOCK_ROUTE_FINISHED)
    private MockEndpoint mockRouteFinished;

    @EndpointInject(MOCK_SQL)
    private MockEndpoint mockSql;

    @EndpointInject(MOCK_JMS_SEND_SERVICE)
    private MockEndpoint mockJmsSendService;

    @BeforeEach
    void replaceEndpoints() throws Exception {
        AdviceWith.adviceWith(
                camelContext,
                SendDocumentRoute.ROUTE_ID,
                route -> route
                        .replaceFromWith(TEST_START)
        );
        AdviceWith.adviceWith(
                camelContext,
                SendDocumentRoute.ROUTE_ID,
                route -> route
                        .interceptSendToEndpoint("sql:" +
                                "update document_sign_log set status=:#${body.getStatus}, last_update=:#${date:now} " +
                                "where id=:#${headers." + ReadDocumentRoute.DATABASE_LOG_ID + "}"
                        )
                        .skipSendToOriginalEndpoint()
                        .to(MOCK_SQL)
        );
        AdviceWith.adviceWith(
                camelContext,
                SendDocumentRoute.ROUTE_ID,
                route -> route
                        .interceptSendToEndpoint(jms("{{clientSend.clientSendRequestQueue}}")
                                .replyTo("{{clientSend.clientSendResponseQueue}}")
                                .getRawUri())
                        .skipSendToOriginalEndpoint()
                        .to(MOCK_JMS_SEND_SERVICE)
        );
        AdviceWith.adviceWith(
                camelContext,
                SendDocumentRoute.ROUTE_ID,
                route -> route
                        .onCompletion()
                        .to(MOCK_ROUTE_FINISHED)
        );
    }

    @Test
    void signDocumentRouteTest() throws JsonProcessingException {
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

        camelContext.createProducerTemplate().sendBody(TEST_START, expectedRequest);

        verify(exchangeTransformer, times(1)).prepareClientSendRequest(any());

        mockJmsSendService.expectedMessageCount(1);
        mockJmsSendService.expectedBodiesReceived(List.of(expectedSerializedRequest));

        mockSql.expectedMessageCount(1);

        mockRouteFinished.expectedMessageCount(1);
        mockRouteFinished.expectedBodiesReceived(expectedResponse);
    }
}
