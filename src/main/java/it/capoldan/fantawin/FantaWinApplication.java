package it.capoldan.fantawin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling necessaria sia per FootballDataSyncJob (gia' presente, @Scheduled non era ancora
// attivato a livello applicativo) sia per il nuovo RealMatchStatsScheduler.
@SpringBootApplication
@EnableScheduling
public class FantaWinApplication {

    public static void main(String[] args) {
        SpringApplication.run(FantaWinApplication.class, args);
    }
}