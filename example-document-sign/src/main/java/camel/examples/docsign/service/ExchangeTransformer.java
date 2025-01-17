package camel.examples.docsign.service;

import camel.examples.docsign.model.ClientSendRequest;
import camel.examples.docsign.model.SignDocumentRequest;
import camel.examples.docsign.model.SignDocumentResponse;
import java.util.Base64;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service("exchangeTransformer")
public class ExchangeTransformer {

    @Value("${signDocument.signType}")
    private String signDocumentSignType;

    @Value("${signDocument.apiKey}")
    private String signDocumentApiKey;

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

    public void prepareSignDocumentRequest(Exchange exchange) {
        Message message = exchange.getMessage();
        SignDocumentRequest request = new SignDocumentRequest(
                Base64.getEncoder().encodeToString(message.getBody(byte[].class)),
                message.getHeader(OWNER_ID, String.class),
                signDocumentSignType,
                signDocumentApiKey,
                message.getHeader(DOCUMENT_TYPE, String.class)
        );
        message.setBody(request);
    }

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

    public String getSignDocumentSignType() {
        return signDocumentSignType;
    }

    public void setSignDocumentSignType(String signDocumentSignType) {
        this.signDocumentSignType = signDocumentSignType;
    }

    public String getSignDocumentApiKey() {
        return signDocumentApiKey;
    }

    public void setSignDocumentApiKey(String signDocumentApiKey) {
        this.signDocumentApiKey = signDocumentApiKey;
    }
}
