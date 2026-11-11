package com.henrique.dashboard.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CollectionScheduleDispatcher {
    private final CollectionRunService runs;

    @Scheduled(fixedDelayString = "${dashboard.schedule.poll-ms:5000}")
    public void dispatchDueSchedules() { runs.dispatchDueSchedules(); }
}
