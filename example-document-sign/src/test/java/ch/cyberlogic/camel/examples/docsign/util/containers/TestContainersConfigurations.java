package ch.cyberlogic.camel.examples.docsign.util.containers;

import java.util.ArrayList;
import java.util.List;
import org.testcontainers.activemq.ArtemisContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

public class TestContainersConfigurations {
    public static GenericContainer<?> getConfiguredSftpContainer(String User, String Password, String Directory) {
        return new GenericContainer<>("atmoz/sftp:alpine")
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("it/ssh_host_ed25519_key", 0777),
                        "/etc/ssh/ssh_host_ed25519_key"
                )
                .withExposedPorts(22)
                .withCommand(User + ":" + Password + ":::" + Directory);
    }

    public static PostgreSQLContainer<?> getConfiguredPostgreSQLContainer(String User, String Password, String DBName, List<String> additionalInitScripts) {
        List<String> initScripts = new ArrayList<>();
        initScripts.add("it/db/init.sql");
        initScripts.addAll(additionalInitScripts);
        return new PostgreSQLContainer<>("postgres:17.2-alpine")
                .withDatabaseName(DBName)
                .withUsername(User)
                .withPassword(Password)
                .withInitScripts(initScripts);
    }

    public static PostgreSQLContainer<?> getConfiguredPostgreSQLContainer(String User, String Password, String DBName) {
        return getConfiguredPostgreSQLContainer(User, Password, DBName, new ArrayList<>());
    }

    public static ArtemisContainer getConfiguredArtemisContainer() {
        return new ArtemisContainer("apache/activemq-artemis:2.39.0-alpine");
    }

    public static MockServerContainer getConfiguredMockServerContainer() {
        return new MockServerContainer(DockerImageName.parse("mockserver/mockserver:5.15.0"))
                .withEnv("MOCKSERVER_TLS_PRIVATE_KEY_PATH", "/config/ssl/mockserver_private_key_pk8.pem")
                .withEnv("MOCKSERVER_TLS_X509_CERTIFICATE_PATH", "/config/ssl/mockserver_cert_chain.pem")
                .withEnv("MOCKSERVER_CERTIFICATE_AUTHORITY_PRIVATE_KEY", "/config/ssl/rootCA_private_key_pk8.pem")
                .withEnv("MOCKSERVER_CERTIFICATE_AUTHORITY_X509_CERTIFICATE", "/config/ssl/rootCA_cert_chain.pem")
                .withCopyFileToContainer(
                        MountableFile.forClasspathResource("it/sign_document_service/mockserver_private_key_pk8.pem", 0777),
                        "/config/ssl/mockserver_private_key_pk8.pem"
                ).withCopyFileToContainer(
                        MountableFile.forClasspathResource("it/sign_document_service/mockserver_cert_chain.pem", 0777),
                        "/config/ssl/mockserver_cert_chain.pem"
                ).withCopyFileToContainer(
                        MountableFile.forClasspathResource("it/sign_document_service/rootCA_private_key_pk8.pem", 0777),
                        "/config/ssl/rootCA_private_key_pk8.pem"
                ).withCopyFileToContainer(
                        MountableFile.forClasspathResource("it/sign_document_service/rootCA_cert_chain.pem", 0777),
                        "/config/ssl/rootCA_cert_chain.pem"
                );
    }
}
