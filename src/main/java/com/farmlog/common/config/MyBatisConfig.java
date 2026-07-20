package com.farmlog.common.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis Mapper 인터페이스 스캔 설정.
 *
 * <p>일부러 {@code FarmlogApiApplication}(메인 클래스)이 아닌 별도 {@code @Configuration}에
 * {@code @MapperScan}을 둔다. {@code @WebMvcTest} 같은 슬라이스 테스트는 메인 클래스를
 * {@code @SpringBootConfiguration} 소스로만 사용하는데, 거기에 {@code @MapperScan}이 붙어 있으면
 * DataSource가 없는 슬라이스 테스트 컨텍스트에서도 매퍼 스캔이 시도되어 테스트가 깨질 수 있다.
 * 별도 설정 클래스로 분리하면 슬라이스 테스트에서 명시적으로 임포트하지 않는 한 영향을 주지 않는다.</p>
 */
@Configuration
@MapperScan("com.farmlog")
public class MyBatisConfig {
}
