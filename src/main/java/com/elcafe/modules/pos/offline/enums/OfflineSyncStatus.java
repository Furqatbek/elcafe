package com.elcafe.modules.pos.offline.enums;

/**
 * Status of offline order synchronization.
 */
public enum OfflineSyncStatus {
    PENDING,    // Waiting to be synced
    SYNCING,    // Currently being processed
    SYNCED,     // Successfully synced
    FAILED,     // Failed to sync after max attempts
    CONFLICT    // Conflict detected during sync
}
