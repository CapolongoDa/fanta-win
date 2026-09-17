package it.capoldan.fantawin.config.springbootcfg;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Senza un TaskScheduler esplicito, @EnableScheduling usa di default un unico thread condiviso da
 * TUTTI i job @Scheduled dell'app: se due cron coincidono (es. FootballDataSyncJob, ogni giorno alle
 * 6:00, e RealMatchStatsScheduler, default ogni lunedi' alle 6:00, che il lunedi' scattano nello
 * stesso istante), il secondo aspetta semplicemente che il primo finisca prima di partire - non e'
 * un errore, ma allunga inutilmente i tempi (FootballDataSyncJob in particolare puo' durare parecchio,
 * vedi il rate limit di Football-Data.org). Un pool dedicato con piu' di un thread evita la coda.
 */
@Slf4j
@Configuration
public class SchedulingConfig {

    @Bean
    public TaskScheduler taskScheduler(@Value("${fantawin.scheduling.pool-size:3}") int poolSize) {
        log.info("Configuro TaskScheduler dedicato per i job @Scheduled: poolSize={}", poolSize);
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("fantawin-scheduler-");
        return scheduler;
    }
}
