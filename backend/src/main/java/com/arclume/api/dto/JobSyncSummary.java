package com.arclume.api.dto;

public class JobSyncSummary {
    private int fetchedCount;
    private int insertedCount;
    private int updatedCount;
    private int skippedCount;
    private int failedCount;

    public JobSyncSummary() {}

    public JobSyncSummary(int fetchedCount, int insertedCount, int updatedCount, int skippedCount, int failedCount) {
        this.fetchedCount = fetchedCount;
        this.insertedCount = insertedCount;
        this.updatedCount = updatedCount;
        this.skippedCount = skippedCount;
        this.failedCount = failedCount;
    }

    public int getFetchedCount() {
        return fetchedCount;
    }

    public void setFetchedCount(int fetchedCount) {
        this.fetchedCount = fetchedCount;
    }

    public int getInsertedCount() {
        return insertedCount;
    }

    public void setInsertedCount(int insertedCount) {
        this.insertedCount = insertedCount;
    }

    public int getUpdatedCount() {
        return updatedCount;
    }

    public void setUpdatedCount(int updatedCount) {
        this.updatedCount = updatedCount;
    }

    public int getSkippedCount() {
        return skippedCount;
    }

    public void setSkippedCount(int skippedCount) {
        this.skippedCount = skippedCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }
}
