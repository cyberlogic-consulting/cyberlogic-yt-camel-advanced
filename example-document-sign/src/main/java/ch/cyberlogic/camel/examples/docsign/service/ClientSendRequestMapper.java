package ch.cyberlogic.camel.examples.docsign.service;

import ch.cyberlogic.camel.examples.docsign.model.ClientSendRequest;
import ch.cyberlogic.camel.examples.docsign.model.SignDocumentResponse;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.springframework.stereotype.Service;

import static ch.cyberlogic.camel.examples.docsign.service.FileMetadataExtractor.CLIENT_ID;
import static ch.cyberlogic.camel.examples.docsign.service.FileMetadataExtractor.DOCUMENT_ID;
import static ch.cyberlogic.camel.examples.docsign.service.FileMetadataExtractor.OWNER_ID;

@Service("clientSendRequestMapper")
public class ClientSendRequestMapper {
    public void prepareClientSendRequest(Exchange exchange) {
        Message message = exchange.getMessage();
        SignDocumentResponse signDocumentResponse = message.getBody(SignDocumentResponse.class);
        ClientSendRequest request = new ClientSendRequest(
                message.getHeader(DOCUMENT_ID, String.class),
                message.getHeader(OWNER_ID, String.class),
                signDocumentResponse.getSignedDocument(),
                message.getHeader(CLIENT_ID, String.class)
        );
        message.setBody(request);
    }
}
