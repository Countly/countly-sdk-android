package ly.count.android.sdk.internal;

import junit.framework.Assert;

import org.junit.Test;

public class DeviceTests {

    @Test
    public void testAsIs() throws Exception {
        Device.TimeUniquenessEnsurer simulator = new Device.TimeUniquenessEnsurer();

        long last = simulator.uniqueTimestamp();

        for (int i = 0; i < 10000; i++) {
            long next = simulator.uniqueTimestamp();
            Assert.assertNotSame(last, next);
        }
    }

    @Test
    public void testMidTimeChange() throws Exception {
        Device.TimeUniquenessEnsurer simulator = new Device.TimeUniquenessEnsurer();

        long last = simulator.uniqueTimestamp();

        for (int i = 0; i < 100; i++) {
            long next = simulator.uniqueTimestamp();
            Assert.assertNotSame(last, next);
        }

        simulator.addition = -10000;

        for (int i = 0; i < 100; i++) {
            long next = simulator.uniqueTimestamp();
            Assert.assertNotSame(last, next);
        }

        simulator.addition = 0;

        for (int i = 0; i < 100; i++) {
            long next = simulator.uniqueTimestamp();
            Assert.assertNotSame(last, next);
        }

        simulator.addition = 10000;

        for (int i = 0; i < 100; i++) {
            long next = simulator.uniqueTimestamp();
            Assert.assertNotSame(last, next);
        }
    }

    @Test
    public void testMidTimeRandomChange() throws Exception {
        Device.TimeUniquenessEnsurer simulator = new Device.TimeUniquenessEnsurer();

        long last = simulator.uniqueTimestamp();

        for (int i = 0; i < 100000; i++) {
            if (i % 30 == 0) {
                simulator.addition = Math.round(Math.random() * 10000 - 5000);
            }
            long next = simulator.uniqueTimestamp();
            Assert.assertNotSame(last, next);
        }

        simulator.addition = 0;

        for (int i = 0; i < 100000; i++) {
            if (i % 30 == 0) {
                simulator.addition += Math.round(Math.random() * 1000 - 500);
            }
            long next = simulator.uniqueTimestamp();
            Assert.assertNotSame(last, next);
        }
    }
}
