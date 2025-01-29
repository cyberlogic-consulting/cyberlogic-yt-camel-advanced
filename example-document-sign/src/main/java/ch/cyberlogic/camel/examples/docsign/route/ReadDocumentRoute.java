package ch.cyberlogic.camel.examples.docsign.route;

import ch.cyberlogic.camel.examples.docsign.service.FileMetadataExtractor;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.endpoint.dsl.SftpEndpointBuilderFactory.SftpEndpointConsumerBuilder;
import org.apache.camel.builder.endpoint.dsl.SqlEndpointBuilderFactory.SqlEndpointBuilder;
import org.apache.camel.component.sql.SqlConstants;
import org.springframework.stereotype.Component;

import static org.apache.camel.builder.endpoint.StaticEndpointBuilders.sftp;
import static org.apache.camel.builder.endpoint.StaticEndpointBuilders.sql;

@Component
public class ReadDocumentRoute extends RouteBuilder {

    public static final String ROUTE_ID = "ReadDocument";

    public static final String DATABASE_LOG_ID = "DatabaseLogId";

    public static final SqlEndpointBuilder SQL_LOG_ENDPOINT = sql(
            "insert into document_sign_log (document_number, owner, last_update, status) " +
                    "values (:#${headerAs(documentId, Integer)}, :#${header.ownerId}, :#${date:now}, 'Read document')"
    );

    public static final SftpEndpointConsumerBuilder SFTP_ENDPOINT = sftp("{{sftp.server.host}}:{{sftp.server.port}}/{{sftp.server.directory}}")
            .username("{{sftp.server.user}}")
            .password("{{sftp.server.password}}")
            .knownHostsFile("{{sftp.server.known_hosts}}")
            .moveFailed(".error")
            .preMove(".processing")
            .move(".done");

    private final FileMetadataExtractor fileMetadataExtractor;

    public ReadDocumentRoute(FileMetadataExtractor fileMetadataExtractor) {
        this.fileMetadataExtractor = fileMetadataExtractor;
    }

    @Override
    public void configure() {
        from(SFTP_ENDPOINT)
                .routeId(ROUTE_ID)
                .log("Read document: ${header." + Exchange.FILE_NAME + "}")
                .bean(fileMetadataExtractor)
                .setHeader(SqlConstants.SQL_RETRIEVE_GENERATED_KEYS, constant(true))
                .to(SQL_LOG_ENDPOINT)
                .setHeader(
                        DATABASE_LOG_ID,
                        simple("${headers." + SqlConstants.SQL_GENERATED_KEYS_DATA + ".get(0).get('id')}"))
                .to(SignDocumentRoute.INPUT_ENDPOINT);
    }
}
