package ch.cyberlogic.camel.examples.docsign.util;

import java.time.LocalDateTime;

public class TestConstants {

    /**
     * General
     */
    public static final String USER = "user";
    public static final String PASSWORD = "password";
    public static final Long TEST_TIMEOUT = 5000L;

    /**
     * SFTP Server
     */
    public static final String SFTP_DIR = "documents";
    
    /**
     * PG Database
     */
    public static final String DB_NAME = "integration";

    /**
     * SignDocument Service
     */
    public static final String SIGN_DOCUMENT_SERVICE_PATH = "/SignDocument/sign";
    public static final String SIGN_DOCUMENT_API_KEY = "acb93ac2-2dce-4209-8ef2-2188ce2047c2";
    public static final String SIGN_DOCUMENT_SIGN_TYPE = "CAdES-C";
    public static final String SIGN_DOCUMENT_TRUSTSTORE_PASSWORD = "password";
    public static final String SIGN_DOCUMENT_TRUSTSTORE = "it/sign_document_service/test_truststore.jks";

    /**
     * ClientSend Service
     */
    public static final String CLIENT_SEND_REQUEST_QUEUE = "client.send.request.queue";
    public static final String CLIENT_SEND_RESPONSE_QUEUE = "client.send.response.queue";
    public static final String CLIENT_SEND_RESPONSE_STATUS_OK = "ClientSendRequest: Status ok";
    public static final String CLIENT_SEND_RESPONSE_MESSAGE_REGULAR = "Document sent";
    public static final LocalDateTime CLIENT_SEND_RESPONSE_TIMESTAMP_REGULAR = LocalDateTime.now();
    public static final String MOCK_CLIENT_SEND_SERVICE = "mock:client-send-service";

}
