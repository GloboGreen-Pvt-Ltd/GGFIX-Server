package com.repairshop.saas.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Sends transactional email via the Resend HTTP API (https://resend.com), using
 * the JDK's built-in {@link HttpClient} — no extra dependency. If the API key is
 * blank or the call fails it logs and returns {@code false}; the OTP flow still
 * succeeds (in dev the code is also surfaced in the response body), so email is
 * a best-effort delivery channel, never a hard dependency.
 */
@Service
@Slf4j
public class EmailService {

    private final String apiKey;
    private final String fromEmail;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public EmailService(
            @Value("${app.resend.api-key:}") String apiKey,
            @Value("${app.resend.from:noreply@globogreen.in}") String fromEmail) {
        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Send a branded 6-digit OTP email. Returns true only if Resend accepted it. */
    public boolean sendOtpEmail(String to, String code, String purpose) {
        if (!isConfigured()) {
            log.warn("EmailService: RESEND_API_KEY not set — skipping email to {} (dev code still returned).", to);
            return false;
        }
        try {
            String payload = mapper.writeValueAsString(Map.of(
                    "from", "GGFIX <" + fromEmail + ">",
                    "to", List.of(to),
                    "subject", "Your GGFIX verification code: " + code,
                    "html", otpHtml(code, purpose)
            ));
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.resend.com/emails"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(12))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                log.info("EmailService: OTP email sent to {} (resend {}).", to, res.statusCode());
                return true;
            }
            log.error("EmailService: Resend {} for {}: {}", res.statusCode(), to, res.body());
            return false;
        } catch (Exception e) {
            log.error("EmailService: failed to send OTP email to {}: {}", to, e.getMessage());
            return false;
        }
    }

    private String otpHtml(String code, String purpose) {
        String safePurpose = (purpose == null || purpose.isBlank()) ? "verify your request" : purpose;
        String tpl = """
            <!DOCTYPE html>
            <html>
              <body style="margin:0;padding:0;background:#f1f5f9;font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
                <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#f1f5f9;padding:32px 0;">
                  <tr><td align="center">
                    <table role="presentation" width="480" cellpadding="0" cellspacing="0" style="max-width:480px;background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 1px 3px rgba(15,23,42,0.10);">
                      <tr><td style="background:#16A34A;padding:22px 32px;">
                        <span style="color:#ffffff;font-size:20px;font-weight:800;letter-spacing:0.5px;">GGFIX</span>
                        <span style="color:#DCFCE7;font-size:12px;">&nbsp;&middot; Repair &middot; Buy &middot; Sell</span>
                      </td></tr>
                      <tr><td style="padding:32px;">
                        <h1 style="margin:0 0 8px;font-size:20px;color:#0f172a;">Your verification code</h1>
                        <p style="margin:0 0 20px;font-size:14px;color:#475569;line-height:22px;">
                          Use the code below to {{PURPOSE}}. It expires in <strong>10 minutes</strong>.
                        </p>
                        <div style="text-align:center;margin:8px 0 24px;">
                          <div style="display:inline-block;background:#F0FDF4;border:1px solid #BBF7D0;border-radius:12px;padding:16px 28px;">
                            <span style="font-size:34px;font-weight:800;letter-spacing:10px;color:#16A34A;">{{CODE}}</span>
                          </div>
                        </div>
                        <p style="margin:0;font-size:12.5px;color:#94a3b8;line-height:20px;">
                          If you didn't request this, you can safely ignore this email. For your security, never share this code with anyone.
                        </p>
                      </td></tr>
                      <tr><td style="padding:16px 32px;background:#f8fafc;border-top:1px solid #e2e8f0;">
                        <span style="font-size:11px;color:#94a3b8;">&copy; GGFIX &middot; www.globogreen.in</span>
                      </td></tr>
                    </table>
                  </td></tr>
                </table>
              </body>
            </html>
            """;
        return tpl.replace("{{PURPOSE}}", safePurpose).replace("{{CODE}}", code);
    }
}
