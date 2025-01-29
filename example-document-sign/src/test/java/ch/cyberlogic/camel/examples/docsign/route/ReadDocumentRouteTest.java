package ch.cyberlogic.camel.examples.docsign.route;

import ch.cyberlogic.camel.examples.docsign.service.FileMetadataExtractor;
import ch.cyberlogic.camel.examples.docsign.util.AdviceWithUtilConfigurable;
import ch.cyberlogic.camel.examples.docsign.util.RouteTestUtil;
import javax.sql.DataSource;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sql.SqlConstants;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.ExcludeRoutes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
    private static final String MOCK_ON_EXCEPTION_SQL = "mock:on-exception-sql";
    private static final String DIRECT_START = "direct:read-document-start";

    @Autowired
    private CamelContext camelContext;

    @Produce(DIRECT_START)
    private ProducerTemplate producerTemplate;

    @MockBean
    private FileMetadataExtractor fileMetadataExtractor;

    @MockBean
    DataSource dataSource;

    @EndpointInject(MOCK_SIGN_DOCUMENT)
    private MockEndpoint mockSignDocument;

    @EndpointInject(MOCK_SQL)
    private MockEndpoint mockSql;

    @EndpointInject(MOCK_ON_EXCEPTION_SQL)
    private MockEndpoint mockOnExceptionSql;

    @BeforeEach
    void replaceEndpoints() throws Exception {
        RouteTestUtil.resetMockEndpoints(mockSql, mockSignDocument, mockOnExceptionSql);
        AdviceWithUtilConfigurable advice = new AdviceWithUtilConfigurable(
                camelContext,
                ReadDocumentRoute.ROUTE_ID);
        advice.replaceFromWith(DIRECT_START);
        advice.replaceEndpoint(
                ReadDocumentRoute.SQL_LOG_ENDPOINT.getRawUri(),
                MOCK_SQL);
        advice.replaceEndpoint(
                SignDocumentRoute.INPUT_ENDPOINT,
                MOCK_SIGN_DOCUMENT);
        advice.replaceEndpoint(
                ErrorHandlingConfiguration.SQL_WRITE_EXCEPTION_ENDPOINT,
                MOCK_ON_EXCEPTION_SQL
        );
    }

    @Test
    void readDocumentRouteTest() throws InterruptedException {
        String contents = "Hello World";
        String fileName = "hello.txt";

        producerTemplate.sendBodyAndHeader(
                contents,
                Exchange.FILE_NAME,
                fileName
        );

        verify(fileMetadataExtractor, times(1)).extractFileMetadata(any());
        mockSql.expectedMessageCount(1);
        mockSql.expectedHeaderReceived(SqlConstants.SQL_RETRIEVE_GENERATED_KEYS, true);
        mockSignDocument.expectedMessageCount(1);
        mockSignDocument.expectedMessagesMatches(
                exchange -> exchange.getMessage().getHeader(ReadDocumentRoute.DATABASE_LOG_ID) != null);
        mockOnExceptionSql.expectedMessageCount(0);

        RouteTestUtil.checkAssertionsSatisfied(
                mockSql,
                mockSignDocument,
                mockOnExceptionSql
        );
    }

    @Test
    void readDocumentRouteExceptionTest() throws InterruptedException {
        String contents = "Hello World";
        String fileName = "hello.txt";
        RuntimeException expected = new RuntimeException("Test Exception");
        doThrow(expected).when(fileMetadataExtractor).extractFileMetadata(any());

        assertThrows(expected.getClass(), () -> producerTemplate.sendBodyAndHeader(
                contents,
                Exchange.FILE_NAME,
                fileName
        ));

        mockSql.expectedMessageCount(0);
        mockSignDocument.expectedMessageCount(0);
        mockOnExceptionSql.expectedMessageCount(0);
        RouteTestUtil.checkAssertionsSatisfied(
                mockSql,
                mockSignDocument,
                mockOnExceptionSql
        );
    }

}
