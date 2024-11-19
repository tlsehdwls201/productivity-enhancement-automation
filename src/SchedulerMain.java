package com.example;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import java.util.Properties;

public class SchedulerMain {

    public static void main(String[] args) {
        try {
            // 설정 파일 로드
            Properties props = configLoader.getProperties();

            String cronExpression = props.getProperty("schedule.cron");
            if (cronExpression == null || cronExpression.isEmpty()) {
                System.err.println("Cron 표현식이 설정 파일에 없습니다.");
                return;
            }

            // JobDetail 정의
            JobDetail job = JobBuilder.newJob(App.class)
                    .withIdentity("naverNewsJob", "group1")
                    .build();

            // Trigger 정의 (Cron 스케줄 사용)
            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity("naverNewsTrigger", "group1")
                    .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
                    .build();

            // Scheduler 생성 및 시작
            Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
            scheduler.start();

            // Job과 Trigger 등록
            scheduler.scheduleJob(job, trigger);

            System.out.println("Scheduler가 시작되었습니다. Cron 표현식: " + cronExpression);

        } catch (SchedulerException se) {
            se.printStackTrace();
        }
    }
}
