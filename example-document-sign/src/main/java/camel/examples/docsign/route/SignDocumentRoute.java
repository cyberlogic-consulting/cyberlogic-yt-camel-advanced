package camel.examples.docsign.route;

import camel.examples.docsign.model.SignDocumentResponse;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import static org.apache.camel.builder.endpoint.StaticEndpointBuilders.https;

@Component
public class SignDocumentRoute extends RouteBuilder {

    public static final String ROUTE_ID = "SignDocument";

    public static final String INPUT_ENDPOINT = "direct:signDocument";

    @Override
    public void configure() {
        from(INPUT_ENDPOINT)
                .routeId(ROUTE_ID)
                .bean("exchangeTransformer", "prepareSignDocumentRequest")
                .log("Sending document for signing: ${header." + Exchange.FILE_NAME + "}")
                .marshal().json()
                .to(https("{{signDocument.serviceUrl}}")
                        .sslContextParameters("customCertificateSslContextParameters")
                        .skipRequestHeaders(true)
                )
                .log("Received response from SignDocument service, unmarshalling: ${body}")
                .unmarshal().json(SignDocumentResponse.class)
                .log("Document signed status: ${body.getStatus}")
                .to("sql:" +
                        "update document_sign_log set status=:#${body.getStatus}, last_update=:#${date:now} " +
                        "where id=:#${headers." + ReadDocumentRoute.DATABASE_LOG_ID + "}")
                .to(SendDocumentRoute.INPUT_ENDPOINT);
    }
}
