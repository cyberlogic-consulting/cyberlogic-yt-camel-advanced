package ch.cyberlogic.camel.examples.docsign.route;

import ch.cyberlogic.camel.examples.docsign.service.ExchangeTransformer;
import javax.sql.DataSource;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sql.SqlConstants;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.ExcludeRoutes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@CamelSpringBootTest
@ActiveProfiles("junit")
@ExcludeRoutes({SignDocumentRoute.class, SendDocumentRoute.class})
@DirtiesContext
public class ReadDocumentRouteTest {

    private static final String MOCK_SIGN_DOCUMENT = "mock:" + SignDocumentRoute.INPUT_ENDPOINT;
    private static final String MOCK_SQL = "mock:sql";
    private static final String DIRECT_START = "direct:read-document-start";

    @Autowired
    private CamelContext camelContext;

    @Produce(DIRECT_START)
    private ProducerTemplate producerTemplate;

    @MockitoBean
    private ExchangeTransformer exchangeTransformer;

    @MockitoBean
    DataSource dataSource;

    @EndpointInject(MOCK_SIGN_DOCUMENT)
    private MockEndpoint mockSignDocument;

    @EndpointInject(MOCK_SQL)
    private MockEndpoint mockSql;

    @BeforeEach
    void replaceEndpoints() throws Exception {
        AdviceWith.adviceWith(
                camelContext,
                ReadDocumentRoute.ROUTE_ID,
                route -> route
                        .replaceFromWith(DIRECT_START)
        );
        AdviceWith.adviceWith(
                camelContext,
                ReadDocumentRoute.ROUTE_ID,
                route -> route
                        .interceptSendToEndpoint("sql:" +
                                "insert into document_sign_log (document_number, owner, last_update, status) " +
                                "values (:#${headerAs(documentId, Integer)}, :#${header.ownerId}, :#${date:now}, 'Read document')"
                        )
                        .skipSendToOriginalEndpoint()
                        .to(MOCK_SQL)
        );
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
    void readDocumentRouteTest() {
        String contents = "Hello World";
        String fileName = "hello.txt";

        camelContext.createProducerTemplate().sendBodyAndHeader(
                DIRECT_START,
                contents,
                Exchange.FILE_NAME,
                fileName
        );

        verify(exchangeTransformer, times(1)).extractFileMetadata(any());
        mockSql.expectedMessageCount(1);
        mockSql.expectedHeaderReceived(SqlConstants.SQL_RETRIEVE_GENERATED_KEYS, true);
        mockSignDocument.expectedMessageCount(1);
        mockSignDocument.expectedMessagesMatches(
                exchange -> exchange.getMessage().getHeader(ReadDocumentRoute.DATABASE_LOG_ID) != null);
    }


}
