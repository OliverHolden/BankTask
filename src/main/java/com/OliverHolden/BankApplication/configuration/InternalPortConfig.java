package com.OliverHolden.BankApplication.configuration;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.tomcat.TomcatWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InternalPortConfig {

    @Bean
    @ConditionalOnProperty(name = "app.internal-port")
    public WebServerFactoryCustomizer<TomcatWebServerFactory> internalConnector(
            @Value("${app.internal-port}") int internalPort) {
        return factory -> {
            Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
            connector.setPort(internalPort);
            factory.addAdditionalConnectors(connector);
        };
    }
}
