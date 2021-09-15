package ly.count.android.sdk;

import java.util.Date;

class MigrationHelper {
    /**
     * 0 - legacy version. State of the SDK before the first migration was introduced
     * 1 - adding device ID to all requests
     */
    final int DATA_SCHEMA_VERSIONS = 1;

    StorageProvider storage;
    ModuleLog L;

    public MigrationHelper(StorageProvider storage, ModuleLog moduleLog) {
        this.storage = storage;
        L = moduleLog;
        L.v("[MigrationHelper] Initialising");
    }

    /**
     * Called from SDK side to perform the required steps to check if the migration is required and then execute it if it is.
     */
    public void doWork() {
        L.v("[MigrationHelper] doWork");
        int currentVersion = getCurrentSchemaVersion();

        if (currentVersion < 0) {
            L.e("[MigrationHelper] doWork, returned schema version is negative, encountered serious issue");
            return;
        }

        while (currentVersion < DATA_SCHEMA_VERSIONS) {
            performMigrationStep(currentVersion);

            currentVersion = getCurrentSchemaVersion();
        }
    }

    /**
     * Return the current schema version.
     * If no schema version is stored, the initial version will be acquired
     *
     * @return
     */
    int getCurrentSchemaVersion() {
        int currentVersion = storage.getDataSchemaVersion();

        if (currentVersion == -1) {
            //no schema version set
            setInitialSchemaVersion();
            currentVersion = storage.getDataSchemaVersion();
        }

        return currentVersion;
    }

    /**
     * Perform migration from the provided version to the next one
     *
     * @param currentVersion
     */
    void performMigrationStep(int currentVersion) {
        int newVersion = currentVersion;

        switch (currentVersion) {
            case 0:
                L.w("[MigrationHelper] performMigrationStep, performing migration from version [0] -> [1]");
                performMigration0To1();
                newVersion = newVersion + 1;
                break;
            case DATA_SCHEMA_VERSIONS:
                L.w("[MigrationHelper] performMigrationStep, attempting to perform migration while already having the latest schema version, skipping [" + currentVersion + "]");
                break;
            default:
                L.w("[MigrationHelper] performMigrationStep, migration is performed out of the currently expected bounds, skipping [" + currentVersion + "]");
                break;
        }

        //assuming that the required migration steps are performed, increasing current schema version
        if (newVersion != currentVersion) {
            storage.setDataSchemaVersion(newVersion);
        }
    }

    /**
     * Set the current schema version the first time this code is executed
     *
     * If nothing is in storage then we can assume that this is the first run and no migration required.
     * In that case set the current version to the latest available one
     *
     * If something is in storage, assume that the SDK had been run before and migration is required.
     */
    void setInitialSchemaVersion() {
        if (storage.anythingSetInStorage()) {
            //we are on a legacy version
            storage.setDataSchemaVersion(0);
            return;
        }

        //no data means new install, apply the latest schema version
        storage.setDataSchemaVersion(DATA_SCHEMA_VERSIONS);
    }

    /**
     * Specific migration from schema version 0 to 1
     */
    void performMigration0To1() {

    }
}
