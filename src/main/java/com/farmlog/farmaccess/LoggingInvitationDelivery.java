package com.farmlog.farmaccess;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** SMTP/outbox 도입 전 개발 환경 전달 지점. 원문 토큰은 DB·감사·응답에 남기지 않고 한 번만 로그한다. */
@Component
@Profile({"default", "local", "dev", "test"})
public class LoggingInvitationDelivery implements InvitationDelivery {
  private static final Logger log = LoggerFactory.getLogger(LoggingInvitationDelivery.class);
  private final String webBaseUrl;

  public LoggingInvitationDelivery(
      @Value("${farmlog.invitation.web-base-url:http://localhost:5173}") String webBaseUrl) {
    this.webBaseUrl = webBaseUrl.replaceAll("/+$", "");
  }

  @Override
  public void deliver(String email, String rawToken) {
    String link =
        webBaseUrl
            + "/invitations/accept?token="
            + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    log.info("Development farm invitation delivery to {}: {}", email, link);
  }
}
