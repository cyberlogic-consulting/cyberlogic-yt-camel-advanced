package camel.examples.docsign.model;

import java.util.Objects;

public class SignDocumentRequest {
    private String document;
    private String ownerId;
    private String signType;
    private String apikey;
    private String documentType;

    public SignDocumentRequest() {
    }

    public SignDocumentRequest(String document, String ownerId, String signType, String apikey, String documentType) {
        this.document = document;
        this.ownerId = ownerId;
        this.signType = signType;
        this.apikey = apikey;
        this.documentType = documentType;
    }

    public String getDocument() {
        return document;
    }

    public void setDocument(String document) {
        this.document = document;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getSignType() {
        return signType;
    }

    public void setSignType(String signType) {
        this.signType = signType;
    }

    public String getApikey() {
        return apikey;
    }

    public void setApikey(String apikey) {
        this.apikey = apikey;
    }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SignDocumentRequest that = (SignDocumentRequest) o;
        return Objects.equals(document, that.document) && Objects.equals(ownerId, that.ownerId) && Objects.equals(signType, that.signType) && Objects.equals(apikey, that.apikey) && Objects.equals(documentType, that.documentType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(document, ownerId, signType, apikey, documentType);
    }

    @Override
    public String toString() {
        return "SignDocumentRequest{" +
                "document='" + document + '\'' +
                ", ownerId='" + ownerId + '\'' +
                ", signType='" + signType + '\'' +
                ", apikey='" + apikey + '\'' +
                ", documentType='" + documentType + '\'' +
                '}';
    }
}
