package net.forgecraft.serverpacklocator.utils;

public enum SyncType {
    LOADED_SERVER,
    LOADED_CLIENT,
    INITIAL_SYNC,
    FORCED_SYNC;

    public boolean loadOnServer() {
        return this == LOADED_SERVER;
    }

    public boolean loadOnClient() {
        return this == LOADED_CLIENT || this == LOADED_SERVER;
    }

    public boolean forceSync() {
        return this != INITIAL_SYNC;
    }
}
