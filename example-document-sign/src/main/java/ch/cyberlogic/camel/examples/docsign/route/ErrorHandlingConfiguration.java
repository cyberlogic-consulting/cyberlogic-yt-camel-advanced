package ch.cyberlogic.camel.examples.docsign.route;

import org.apache.camel.builder.RouteConfigurationBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import static ch.cyberlogic.camel.examples.docsign.route.ReadDocumentRoute.DATABASE_LOG_ID;

@Component
public class ErrorHandlingConfiguration extends RouteConfigurationBuilder {

    public static final String SQL_WRITE_EXCEPTION_ENDPOINT = "sql:" +
            "update document_sign_log set status=:#${exception.message}, last_update=:#${date:now} " +
            "where id=:#${headers." + DATABASE_LOG_ID + "}";

    @Override
    public void configuration() {
        routeConfiguration()
                .onException(Exception.class)
                .handled(false)
                .choice()
                .when(exchange -> !ObjectUtils.isEmpty(exchange.getMessage()
                        .getHeader(DATABASE_LOG_ID, String.class)))
                .to(SQL_WRITE_EXCEPTION_ENDPOINT)
                .end();
    }
}
