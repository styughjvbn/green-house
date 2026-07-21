package com.greenhouse.backend.common.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

	public static final ZoneId FARM_TIME_ZONE = ZoneId.of("Asia/Seoul");

	@Bean
	public Clock farmClock() {
		return Clock.system(FARM_TIME_ZONE);
	}
}
