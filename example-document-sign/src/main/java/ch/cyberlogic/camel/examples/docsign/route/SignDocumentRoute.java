package ch.cyberlogic.camel.examples.docsign.route;

import ch.cyberlogic.camel.examples.docsign.model.SignDocumentResponse;
import ch.cyberlogic.camel.examples.docsign.service.SignDocumentRequestMapper;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.endpoint.dsl.HttpEndpointBuilderFactory.HttpEndpointBuilder;
import org.apache.camel.builder.endpoint.dsl.SqlEndpointBuilderFactory.SqlEndpointBuilder;
import org.springframework.stereotype.Component;

import static org.apache.camel.builder.endpoint.StaticEndpointBuilders.https;
import static org.apache.camel.builder.endpoint.StaticEndpointBuilders.sql;

@Component
public class SignDocumentRoute extends RouteBuilder {

    public static final String ROUTE_ID = "SignDocument";

    public static final String INPUT_ENDPOINT = "direct:signDocument";

    public static final SqlEndpointBuilder SQL_LOG_ENDPOINT = sql(
            "update document_sign_log set status=:#${body.getStatus}, last_update=:#${date:now} " +
                    "where id=:#${headers." + ReadDocumentRoute.DATABASE_LOG_ID + "}"
    );

    public static final HttpEndpointBuilder HTTPS_ENDPOINT = https(
            "{{signDocument.serviceUrl}}")
            .sslContextParameters("customCertificateSslContextParameters")
            .skipRequestHeaders(true);

    private final SignDocumentRequestMapper signDocumentRequestMapper;

    public SignDocumentRoute(SignDocumentRequestMapper signDocumentRequestMapper) {
        this.signDocumentRequestMapper = signDocumentRequestMapper;
    }

    @Override
    public void configure() {
        from(INPUT_ENDPOINT)
                .routeId(ROUTE_ID)
                .bean(signDocumentRequestMapper)
                .log("Sending document for signing: ${header." + Exchange.FILE_NAME + "}")
                .marshal().json()
                .to(HTTPS_ENDPOINT)
                .log("Received response from SignDocument service, unmarshalling: ${body}")
                .unmarshal().json(SignDocumentResponse.class)
                .log("Document signed status: ${body.getStatus}")
                .to(SQL_LOG_ENDPOINT)
                .to(SendDocumentRoute.INPUT_ENDPOINT);
    }
}
