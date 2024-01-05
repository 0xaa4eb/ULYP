package com.ulyp.agent.queue;

import lombok.Getter;

@Getter
class ExitRecordQueueItem {

    private final int recordingId;
    private final int callId;
    private final Object returnValue;
    private final boolean thrown;
    private final long nanoTime;

    ExitRecordQueueItem(int recordingId, int callId, Object returnValue, boolean thrown, long nanoTime) {
        this.recordingId = recordingId;
        this.callId = callId;
        this.returnValue = returnValue;
        this.thrown = thrown;
        this.nanoTime = nanoTime;
    }
}
