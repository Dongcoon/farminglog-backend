package com.farmlog.export;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 파일 보관·행수·lease 정책을 환경 변수로 조정하되 안전한 기본값을 유지한다. */
@Getter @Setter
@Component
@ConfigurationProperties(prefix = "farmlog.export")
public class ExportProperties {
    private String rootDir = "./exports";
    private int retentionDays = 7;
    private int xlsxMaxRows = 200_000;
    private int pdfMaxRows = 5_000;
    private int leaseMinutes = 10;
    private int maxAttempts = 3;
    private long pollDelayMs = 1_000L;
}
