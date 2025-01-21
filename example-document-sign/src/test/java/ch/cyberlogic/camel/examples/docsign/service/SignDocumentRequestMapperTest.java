package ch.cyberlogic.camel.examples.docsign.service;

import ch.cyberlogic.camel.examples.docsign.model.SignDocumentRequest;
import java.util.Base64;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultMessage;
import org.junit.jupiter.api.Test;

import static ch.cyberlogic.camel.examples.docsign.service.FileMetadataExtractor.CLIENT_ID;
import static ch.cyberlogic.camel.examples.docsign.service.FileMetadataExtractor.DOCUMENT_ID;
import static ch.cyberlogic.camel.examples.docsign.service.FileMetadataExtractor.DOCUMENT_TYPE;
import static ch.cyberlogic.camel.examples.docsign.service.FileMetadataExtractor.OWNER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SignDocumentRequestMapperTest extends CamelExchangeSupport {

    private final SignDocumentRequestMapper mapper = new SignDocumentRequestMapper();

    @Test
    void prepareSignDocumentRequestTest() {
        String contents = "Hello World";
        String documentId = "32767";
        String ownerId = "bigBank11";
        String documentType = "taxReport";
        String clientId = "32766";
        String signType = "CAdES-C";
        String apiKey = "acb93ac2-2dce-4209-8ef2-2188ce2047c2";
        mapper.setSignDocumentSignType(signType);
        mapper.setSignDocumentApiKey(apiKey);
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

        mapper.prepareSignDocumentRequest(exchange);

        assertEquals(expectedRequest, message.getBody());
    }
}
