package ch.cyberlogic.camel.examples.docsign.service;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultMessage;
import org.junit.jupiter.api.Test;

import static ch.cyberlogic.camel.examples.docsign.service.FileMetadataExtractor.CLIENT_ID;
import static ch.cyberlogic.camel.examples.docsign.service.FileMetadataExtractor.DOCUMENT_ID;
import static ch.cyberlogic.camel.examples.docsign.service.FileMetadataExtractor.DOCUMENT_TYPE;
import static ch.cyberlogic.camel.examples.docsign.service.FileMetadataExtractor.OWNER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FileMetadataExtractorTest extends CamelExchangeSupport {

    private final FileMetadataExtractor extractor = new FileMetadataExtractor();

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

        extractor.extractFileMetadata(exchange);

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

        assertThrows(IllegalArgumentException.class, () -> extractor.extractFileMetadata(exchange));
    }
}
