package com.ngao.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Project Ngao :: API Gateway &amp; Anti-Corruption Layer (ACL).
 *
 * <p>Two responsibilities:
 * <ul>
 *   <li><b>Ingress:</b> accept the mobile app's modern JSON and forward it to the
 *       Core Payment Engine over REST.</li>
 *   <li><b>Egress (ACL):</b> consume {@code core-transactions} events and translate
 *       them into a simulated legacy SOAP/XML core-banking push.</li>
 * </ul>
 */
@SpringBootApplication
public class ApiGatewayAclApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayAclApplication.class, args);
    }
}
