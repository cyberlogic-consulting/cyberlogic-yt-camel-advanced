package ch.cyberlogic.camel.examples.docsign.util.endpoints;

import org.apache.camel.builder.endpoint.dsl.SftpEndpointBuilderFactory.SftpEndpointBuilder;

import static org.apache.camel.builder.endpoint.StaticEndpointBuilders.sftp;

public class Endpoints {

    public static SftpEndpointBuilder sftpServer() {
        return sftp("{{sftp.server.host}}:{{sftp.server.port}}/{{sftp.server.directory}}")
                .username("{{sftp.server.user}}")
                .password("{{sftp.server.password}}")
                .knownHostsFile("{{sftp.server.known_hosts}}");
    }

    public static String getSftpEndpointUriWithErrorDirectory() {
        return sftpServer().getRawUri()
                .replace("{{sftp.server.directory}}", "{{sftp.server.directory}}/.processing/.error");
    }

    public static String getSftpEndpointUriWithDoneDirectory() {
        return sftpServer().getRawUri()
                .replace("{{sftp.server.directory}}", "{{sftp.server.directory}}/.processing/.done");
    }

}
