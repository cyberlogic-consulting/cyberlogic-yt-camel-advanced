package ch.cyberlogic.camel.examples.docsign.configuration;

import java.security.KeyStoreException;
import org.apache.camel.CamelContext;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SSLContextParamsConfiguration {

    @Value("${signDocument.trustStore}")
    private String trustStore;

    @Value("${signDocument.trustStorePassword}")
    private String trustStorePassword;

    @Bean
    SSLContextParameters customCertificateSslContextParameters(CamelContext camelContext) throws KeyStoreException {
        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setCamelContext(camelContext);

        KeyStoreParameters keyStoreParameters = new KeyStoreParameters();
        keyStoreParameters.setResource(trustStore);
        keyStoreParameters.setPassword(trustStorePassword);

        TrustManagersParameters trustManagersParameters = new TrustManagersParameters();
        trustManagersParameters.setKeyStore(keyStoreParameters);
        sslContextParameters.setTrustManagers(trustManagersParameters);
        return sslContextParameters;
    }
}
