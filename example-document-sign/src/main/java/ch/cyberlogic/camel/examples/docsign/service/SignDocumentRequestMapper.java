package ch.cyberlogic.camel.examples.docsign.service;

import ch.cyberlogic.camel.examples.docsign.model.SignDocumentRequest;
import java.util.Base64;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import static ch.cyberlogic.camel.examples.docsign.service.FileMetadataExtractor.DOCUMENT_TYPE;
import static ch.cyberlogic.camel.examples.docsign.service.FileMetadataExtractor.OWNER_ID;

@Service("signDocumentRequestMapper")
public class SignDocumentRequestMapper {

    @Value("${signDocument.signType}")
    private String signDocumentSignType;

    @Value("${signDocument.apiKey}")
    private String signDocumentApiKey;

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
