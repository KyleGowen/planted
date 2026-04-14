package com.planted.queue;

import com.planted.worker.PlantJobWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;

/**
 * Local dev: after the publishing transaction commits, runs {@link PlantJobWorker#process}
 * on {@code plantJobExecutor}. This avoids {@code @TransactionalEventListener}+{@code @Async},
 * which can surface failures to the HTTP thread and return 500 on enqueue endpoints.
 */
@Slf4j
@Service
@Profile("local")
public class LocalPlantJobPublisher implements PlantJobPublisher {

    private final Executor plantJobExecutor;
    private final ObjectProvider<PlantJobWorker> jobWorkerProvider;

    public LocalPlantJobPublisher(
            @Qualifier("plantJobExecutor") Executor plantJobExecutor,
            ObjectProvider<PlantJobWorker> jobWorkerProvider) {
        this.plantJobExecutor = plantJobExecutor;
        this.jobWorkerProvider = jobWorkerProvider;
    }

    @Override
    public void publish(PlantJobMessage message) {
        log.info("[LOCAL QUEUE] Publishing job: {} for plantId={}", message.getJobType(), message.getPlantId());
        Runnable task = () -> jobWorkerProvider.getObject().process(message);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            try {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        scheduleJob(task, message);
                    }
                });
            } catch (RuntimeException e) {
                throw new IllegalStateException(
                        "Failed to register after-commit job scheduling for " + message.getJobType(), e);
            }
        } else {
            scheduleJob(task, message);
        }
    }

    /**
     * Never throw from here: exceptions during after-commit would fail the HTTP request after a successful DB commit.
     */
    private void scheduleJob(Runnable task, PlantJobMessage message) {
        try {
            plantJobExecutor.execute(task);
        } catch (RejectedExecutionException e) {
            log.warn("[LOCAL QUEUE] plantJobExecutor rejected {}; falling back to ForkJoinPool.commonPool()", message.getJobType());
            CompletableFuture.runAsync(task, ForkJoinPool.commonPool());
        } catch (Exception e) {
            log.error("[LOCAL QUEUE] Failed to submit {}; falling back to ForkJoinPool.commonPool()", message.getJobType(), e);
            CompletableFuture.runAsync(task, ForkJoinPool.commonPool());
        }
    }
}
