package ch.cyberlogic.camel.examples.docsign.route;

import ch.cyberlogic.camel.examples.docsign.model.ClientSendResponse;
import ch.cyberlogic.camel.examples.docsign.service.ClientSendRequestMapper;
import ch.cyberlogic.camel.examples.docsign.service.FileMetadataExtractor;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.endpoint.dsl.JmsEndpointBuilderFactory.JmsEndpointProducerBuilder;
import org.apache.camel.builder.endpoint.dsl.SqlEndpointBuilderFactory.SqlEndpointBuilder;
import org.springframework.stereotype.Component;

import static org.apache.camel.builder.endpoint.StaticEndpointBuilders.jms;
import static org.apache.camel.builder.endpoint.StaticEndpointBuilders.sql;

@Component
public class SendDocumentRoute extends RouteBuilder {

    public static final String ROUTE_ID = "SendDocument";

    public static final String INPUT_ENDPOINT = "direct:sendDocument";

    public static final SqlEndpointBuilder SQL_LOG_ENDPOINT = sql(
            "update document_sign_log set status=:#${body.getStatus}, last_update=:#${date:now} " +
                    "where id=:#${headers." + ReadDocumentRoute.DATABASE_LOG_ID + "}"
    );

    public static final JmsEndpointProducerBuilder JMS_ENDPOINT = jms(
            "{{clientSend.clientSendRequestQueue}}")
            .replyTo("{{clientSend.clientSendResponseQueue}}")
            .requestTimeout(300000);

    private final ClientSendRequestMapper clientSendRequestMapper;

    public SendDocumentRoute(ClientSendRequestMapper clientSendRequestMapper) {
        this.clientSendRequestMapper = clientSendRequestMapper;
    }

    @Override
    public void configure() throws Exception {
        from(INPUT_ENDPOINT)
                .routeId(ROUTE_ID)
                .bean(clientSendRequestMapper)
                .log("Sending signed document to client: ${header." + FileMetadataExtractor.CLIENT_ID + "}")
                .marshal().jacksonXml()
                .to(ExchangePattern.InOut, JMS_ENDPOINT)
                .log("Received response from ClientSend service, unmarshalling: ${body}")
                .unmarshal().jacksonXml(ClientSendResponse.class)
                .log("Document sent status: ${body.getStatus}")
                .to(SQL_LOG_ENDPOINT)
                .log("Processing document ${header." + Exchange.FILE_NAME + "} finished.");
    }
}
