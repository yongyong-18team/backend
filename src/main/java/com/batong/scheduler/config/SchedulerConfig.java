package com.batong.scheduler.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 스케줄러(TRD 12-6)를 켠다. 작업은 {@code SchedulerJobs}에 있다. */
@Configuration
@EnableScheduling
public class SchedulerConfig {
}
