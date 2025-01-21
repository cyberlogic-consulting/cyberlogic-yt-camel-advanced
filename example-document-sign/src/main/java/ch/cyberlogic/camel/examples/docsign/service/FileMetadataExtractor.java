package ch.cyberlogic.camel.examples.docsign.service;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.springframework.stereotype.Service;

@Service("fileMetadataExtractor")
public class FileMetadataExtractor {

    public static final String DOCUMENT_ID = "documentId";
    public static final String OWNER_ID = "ownerId";
    public static final String DOCUMENT_TYPE = "documentType";
    public static final String CLIENT_ID = "clientId";

    public void extractFileMetadata(Exchange exchange) {
        String fileName = exchange.getMessage().getHeader(Exchange.FILE_NAME, String.class);
        String[] fileNameParts = fileName.split("_");
        if (fileNameParts.length < 4) {
            throw new IllegalArgumentException("Invalid file name: " + fileName);
        }

        Message message = exchange.getMessage();
        message.setHeader(DOCUMENT_ID, fileNameParts[0]);
        message.setHeader(OWNER_ID, fileNameParts[1]);
        message.setHeader(DOCUMENT_TYPE, fileNameParts[2]);
        message.setHeader(CLIENT_ID, fileNameParts[3].split("\\.")[0]);
    }
}
