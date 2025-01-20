package ch.cyberlogic.camel.examples.docsign.service;


import ch.cyberlogic.camel.examples.docsign.model.ClientSendRequest;
import ch.cyberlogic.camel.examples.docsign.model.SignDocumentRequest;
import ch.cyberlogic.camel.examples.docsign.model.SignDocumentResponse;
import java.time.LocalDateTime;
import java.util.Base64;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static ch.cyberlogic.camel.examples.docsign.service.ExchangeTransformer.CLIENT_ID;
import static ch.cyberlogic.camel.examples.docsign.service.ExchangeTransformer.DOCUMENT_ID;
import static ch.cyberlogic.camel.examples.docsign.service.ExchangeTransformer.DOCUMENT_TYPE;
import static ch.cyberlogic.camel.examples.docsign.service.ExchangeTransformer.OWNER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ExchangeTransformerTest {

    private final ExchangeTransformer transformer = new ExchangeTransformer();

    private final CamelContext camelContext = new DefaultCamelContext();

    private Exchange exchange;

    @BeforeEach
    public void createExchange() {
        exchange = new DefaultExchange(camelContext);
    }

    @Test
    void extractFileMetadataTest() {
        String contents = "Hello World";
        String documentId = "32767";
        String ownerId = "bigBank11";
        String documentType = "taxReport";
        String clientId = "32766";
        String fileName = documentId
                + "_" + ownerId
                + "_" + documentType
                + "_" + clientId + ".pdf";
        Message message = new DefaultMessage(exchange);
        message.setBody(contents);
        message.setHeader(Exchange.FILE_NAME, fileName);
        exchange.setMessage(message);

        transformer.extractFileMetadata(exchange);

        assertEquals(contents, message.getBody());
        assertEquals(fileName, message.getHeader(Exchange.FILE_NAME));
        assertEquals(documentId, message.getHeader(DOCUMENT_ID));
        assertEquals(ownerId, message.getHeader(OWNER_ID));
        assertEquals(documentType, message.getHeader(DOCUMENT_TYPE));
        assertEquals(clientId, message.getHeader(CLIENT_ID));
    }

    @Test
    void extractFileMetadataTestInvalidFileName() {
        String contents = "Hello World";
        String ownerId = "bigBank11";
        String documentType = "taxReport";
        String clientId = "32766";
        String fileName = ownerId
                + "_" + documentType
                + "_" + clientId + ".pdf";
        Message message = new DefaultMessage(exchange);
        message.setBody(contents);
        message.setHeader(Exchange.FILE_NAME, fileName);
        exchange.setMessage(message);

        assertThrows(IllegalArgumentException.class, () -> transformer.extractFileMetadata(exchange));
    }

    @Test
    void prepareSignDocumentRequestTest() {
        String contents = "Hello World";
        String documentId = "32767";
        String ownerId = "bigBank11";
        String documentType = "taxReport";
        String clientId = "32766";
        String signType = "CAdES-C";
        String apiKey = "acb93ac2-2dce-4209-8ef2-2188ce2047c2";
        transformer.setSignDocumentSignType(signType);
        transformer.setSignDocumentApiKey(apiKey);
        SignDocumentRequest expectedRequest = new SignDocumentRequest(
                Base64.getEncoder().encodeToString(contents.getBytes()),
                ownerId,
                signType,
                apiKey,
                documentType
        );
        Message message = new DefaultMessage(exchange);
        message.setBody(contents);
        message.setHeader(DOCUMENT_ID, documentId);
        message.setHeader(OWNER_ID, ownerId);
        message.setHeader(DOCUMENT_TYPE, documentType);
        message.setHeader(CLIENT_ID, clientId);
        exchange.setMessage(message);

        transformer.prepareSignDocumentRequest(exchange);

        assertEquals(expectedRequest, message.getBody());
    }

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

        transformer.prepareClientSendRequest(exchange);

        ClientSendRequest actualRequest = message.getBody(ClientSendRequest.class);
        assertEquals(expectedRequest.getClientId(), actualRequest.getClientId());
        assertEquals(expectedRequest.getDocument(), actualRequest.getDocument());
        assertEquals(expectedRequest.getDocumentId(), actualRequest.getDocumentId());
        assertEquals(expectedRequest.getOwnerId(), actualRequest.getOwnerId());
    }
}
