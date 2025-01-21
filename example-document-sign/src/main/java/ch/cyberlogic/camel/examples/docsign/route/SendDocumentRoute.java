package ch.cyberlogic.camel.examples.docsign.route;

import ch.cyberlogic.camel.examples.docsign.model.ClientSendResponse;
import ch.cyberlogic.camel.examples.docsign.service.FileMetadataExtractor;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import static org.apache.camel.builder.endpoint.StaticEndpointBuilders.jms;

@Component
public class SendDocumentRoute extends RouteBuilder {

    public static final String ROUTE_ID = "SendDocument";

    public static final String INPUT_ENDPOINT = "direct:sendDocument";

    @Override
    public void configure() throws Exception {
        from(INPUT_ENDPOINT)
                .routeId(ROUTE_ID)
                .bean("clientSendRequestMapper", "prepareClientSendRequest")
                .log("Sending signed document to client: ${header." + FileMetadataExtractor.CLIENT_ID + "}")
                .marshal().jacksonXml()
                .to(ExchangePattern.InOut,
                        jms("{{clientSend.clientSendRequestQueue}}")
                                .replyTo("{{clientSend.clientSendResponseQueue}}")
                )
                .log("Received response from ClientSend service, unmarshalling: ${body}")
                .unmarshal().jacksonXml(ClientSendResponse.class)
                .log("Document sent status: ${body.getStatus}")
                .to("sql:" +
                        "update document_sign_log set status=:#${body.getStatus}, last_update=:#${date:now} " +
                        "where id=:#${headers." + ReadDocumentRoute.DATABASE_LOG_ID + "}")
                .log("Processing document ${header." + Exchange.FILE_NAME + "} finished.");
    }
}
