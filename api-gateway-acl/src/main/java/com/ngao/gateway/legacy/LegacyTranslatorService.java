package com.ngao.gateway.legacy;

import com.ngao.gateway.event.PaymentSuccessEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * The Anti-Corruption Layer's egress half.
 *
 * <p>Consumes {@link PaymentSuccessEvent}s from the {@code core-transactions} topic
 * and translates the clean, modern event into the verbose XML/SOAP dialect a legacy
 * core-banking platform (e.g. Temenos T24 / Oracle FLEXCUBE) expects. Here the
 * "push" is simulated by logging the envelope to the console; a production
 * implementation would marshal this and send it via a Spring WS
 * {@code WebServiceTemplate} to the real core endpoint.
 */
@Service
public class LegacyTranslatorService {

    private static final Logger log = LoggerFactory.getLogger(LegacyTranslatorService.class);

    @KafkaListener(
            topics = "${app.kafka.topic.core-transactions:core-transactions}",
            groupId = "legacy-translator"
    )
    public void onPaymentSuccess(PaymentSuccessEvent event) {
        log.info("ACL received PaymentSuccessEvent for transaction {} (amount={} {})",
                event.transactionId(), event.amount(), event.currency());

        String soapEnvelope = toLegacySoapEnvelope(event);

        log.info("""
                ===== SIMULATED LEGACY CORE-BANKING PUSH (SOAP/XML over HTTP) =====
                POST https://legacy-core.bank.internal/services/TransactionService
                SOAPAction: "PostTransaction"
                Content-Type: text/xml; charset=utf-8

                {}
                ==================================================================""",
                soapEnvelope);
    }

    /**
     * Map the modern event onto a legacy {@code PostTransactionRequest} SOAP body.
     * This mapping is the heart of the Anti-Corruption Layer: field names, the
     * envelope shape and the currency-as-attribute convention all belong to the
     * legacy world and are kept out of the rest of the platform.
     */
    private String toLegacySoapEnvelope(PaymentSuccessEvent e) {
        return """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                                  xmlns:core="http://legacy.bank.example/core/transaction">
                  <soapenv:Header/>
                  <soapenv:Body>
                    <core:PostTransactionRequest>
                      <core:TransactionReference>%s</core:TransactionReference>
                      <core:DebitAccount>%s</core:DebitAccount>
                      <core:CreditAccount>%s</core:CreditAccount>
                      <core:Amount currency="%s">%s</core:Amount>
                      <core:Narrative>%s</core:Narrative>
                      <core:Status>%s</core:Status>
                      <core:ValueDate>%s</core:ValueDate>
                    </core:PostTransactionRequest>
                  </soapenv:Body>
                </soapenv:Envelope>"""
                .formatted(
                        e.transactionId(),
                        escapeXml(e.fromAccount()),
                        escapeXml(e.toAccount()),
                        escapeXml(e.currency()),
                        e.amount(),
                        escapeXml(e.narrative() == null ? "" : e.narrative()),
                        escapeXml(e.status()),
                        e.occurredAt()
                );
    }

    /** Minimal XML escaping so free-text fields can't break the envelope. */
    private static String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
