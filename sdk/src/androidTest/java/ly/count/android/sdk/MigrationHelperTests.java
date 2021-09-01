package ly.count.android.sdk;

import android.app.Activity;
import android.content.res.Configuration;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import static androidx.test.InstrumentationRegistry.getContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class MigrationHelperTests {
    ModuleLog mockLog;
    CountlyStore cs;
    StorageProvider sp;

    @Before
    public void setUp() {
        mockLog = mock(ModuleLog.class);

        cs = new CountlyStore(getContext(), mockLog);
        sp = cs;

        final CountlyStore countlyStore = new CountlyStore(getContext(), mockLog);
        countlyStore.clear();
    }

    @After
    public void tearDown() {

    }

    /**
     * Verify that the current SDK data schema version is the expected value
     */
    @Test
    public void validateDataSchemaVersion() {
        MigrationHelper mh = new MigrationHelper(sp, mockLog);
        assertEquals(1, mh.DATA_SCHEMA_VERSIONS);
    }

    /**
     * If the SDK has no data, the initial schema version should be set the latest schema version
     */
    @Test
    public void setInitialSchemaVersionEmpty() {
        StorageProvider spMock = mock(StorageProvider.class);
        when(spMock.anythingSetInStorage()).thenReturn(false);

        MigrationHelper mh = new MigrationHelper(spMock, mockLog);
        mh.setInitialSchemaVersion();

        verify(spMock).anythingSetInStorage();
        verify(spMock).setDataSchemaVersion(mh.DATA_SCHEMA_VERSIONS);
    }

    /**
     * If the SDK has data in storage, the initial schema version should be set to 0
     */
    @Test
    public void setInitialSchemaVersionLegacy() {
        StorageProvider spMock = mock(StorageProvider.class);
        when(spMock.anythingSetInStorage()).thenReturn(true);

        MigrationHelper mh = new MigrationHelper(spMock, mockLog);
        mh.setInitialSchemaVersion();

        verify(spMock).anythingSetInStorage();
        verify(spMock).setDataSchemaVersion(0);
    }

    /**
     * Validate the result of 'getCurrentSchemaVersion' if it's run the first time and there is no data.
     *
     * It should return the latest schema version
     */
    @Test
    public void getCurrentSchemaVersionEmpty() {
        cs.clear();
        MigrationHelper mh = new MigrationHelper(cs, mockLog);
        assertEquals(mh.DATA_SCHEMA_VERSIONS, mh.getCurrentSchemaVersion());

        //verify a rerun
        assertEquals(mh.DATA_SCHEMA_VERSIONS, mh.getCurrentSchemaVersion());
    }

    /**
     * Validate the result of 'getCurrentSchemaVersion' if it's run the first time and there is some data.
     *
     * It should return the legacy schema version - 0
     */
    @Test
    public void getCurrentSchemaVersionLegacy() {
        cs.clear();
        cs.addRequest("fff");
        MigrationHelper mh = new MigrationHelper(cs, mockLog);
        assertEquals(0, mh.getCurrentSchemaVersion());

        //verify a rerun
        assertEquals(0, mh.getCurrentSchemaVersion());
    }

    /**
     * Validate the result of 'getCurrentSchemaVersion' if there was a schema version set previously
     *
     * It should return the legacy schema version - 0
     */
    @Test
    public void getCurrentSchemaVersionMisc() {
        cs.clear();
        MigrationHelper mh = new MigrationHelper(sp, mockLog);
        assertEquals(mh.DATA_SCHEMA_VERSIONS, mh.getCurrentSchemaVersion());

        sp.setDataSchemaVersion(123);
        assertEquals(123, mh.getCurrentSchemaVersion());

        //verify a rerun
        assertEquals(123, mh.getCurrentSchemaVersion());

        sp.setDataSchemaVersion(-333);
        assertEquals(-333, mh.getCurrentSchemaVersion());
    }
}
