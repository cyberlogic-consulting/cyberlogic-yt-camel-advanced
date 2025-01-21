package ch.cyberlogic.camel.examples.docsign.service;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;

public class CamelExchangeSupport {

    private final CamelContext camelContext = new DefaultCamelContext();

    protected Exchange exchange;

    @BeforeEach
    public void createExchange() {
        exchange = new DefaultExchange(camelContext);
    }

    protected Exchange getExchange() {
        return exchange;
    }
}
