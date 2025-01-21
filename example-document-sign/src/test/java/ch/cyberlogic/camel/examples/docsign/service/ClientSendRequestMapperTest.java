package ch.cyberlogic.camel.examples.docsign.service;


import ch.cyberlogic.camel.examples.docsign.model.ClientSendRequest;
import ch.cyberlogic.camel.examples.docsign.model.SignDocumentResponse;
import java.time.LocalDateTime;
import java.util.Base64;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultMessage;
import org.junit.jupiter.api.Test;

import static ch.cyberlogic.camel.examples.docsign.service.FileMetadataExtractor.CLIENT_ID;
import static ch.cyberlogic.camel.examples.docsign.service.FileMetadataExtractor.DOCUMENT_ID;
import static ch.cyberlogic.camel.examples.docsign.service.FileMetadataExtractor.DOCUMENT_TYPE;
import static ch.cyberlogic.camel.examples.docsign.service.FileMetadataExtractor.OWNER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ClientSendRequestMapperTest extends CamelExchangeSupport{

    private final ClientSendRequestMapper mapper = new ClientSendRequestMapper();

    @Test
    void prepareClientSendRequestTest() {
        String contents = "Hello World";
        String documentId = "32767";
        String ownerId = "bigBank11";
        String documentType = "taxReport";
        String clientId = "32766";
        String status = "SignDocumentResponse: Status ok";
        String responseMessage = "Document signed";
        SignDocumentResponse signDocumentResponse = new SignDocumentResponse(
                Base64.getEncoder().encodeToString(contents.getBytes()),
                status,
                responseMessage,
                LocalDateTime.now()
        );
        ClientSendRequest expectedRequest = new ClientSendRequest(
                documentId,
                ownerId,
                signDocumentResponse.getSignedDocument(),
                clientId
        );
        Message message = new DefaultMessage(exchange);
        message.setBody(signDocumentResponse);
        message.setHeader(DOCUMENT_ID, documentId);
        message.setHeader(OWNER_ID, ownerId);
        message.setHeader(DOCUMENT_TYPE, documentType);
        message.setHeader(CLIENT_ID, clientId);
        exchange.setMessage(message);

        mapper.prepareClientSendRequest(exchange);

        ClientSendRequest actualRequest = message.getBody(ClientSendRequest.class);
        assertEquals(expectedRequest.getClientId(), actualRequest.getClientId());
        assertEquals(expectedRequest.getDocument(), actualRequest.getDocument());
        assertEquals(expectedRequest.getDocumentId(), actualRequest.getDocumentId());
        assertEquals(expectedRequest.getOwnerId(), actualRequest.getOwnerId());
    }
}
