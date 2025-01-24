package ch.cyberlogic.camel.examples.docsign.util;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.AdviceWith;

public class AdviceWithUtilConfigurable {

    private final CamelContext camelContext;

    private final String routeId;

    public AdviceWithUtilConfigurable(CamelContext camelContext, String routeId) {
        this.camelContext = camelContext;
        this.routeId = routeId;
    }

    public void replaceEndpoint(String originalEndpoint, String replacement) throws Exception {
        AdviceWith.adviceWith(
                camelContext,
                routeId,
                route -> route
                        .interceptSendToEndpoint(originalEndpoint)
                        .skipSendToOriginalEndpoint()
                        .to(replacement));
    }

    public void addEndpointOnRouteCompletion(String onCompletionEndpointUri) throws Exception {
        AdviceWith.adviceWith(
                camelContext,
                routeId,
                route -> route
                        .onCompletion()
                        .to(onCompletionEndpointUri));
    }

    public void replaceFromWith(String replacementEndpointUri) throws Exception {
        AdviceWith.adviceWith(
                camelContext,
                routeId,
                route -> route
                        .replaceFromWith(replacementEndpointUri)
        );
    }
}
