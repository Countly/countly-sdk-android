package ly.count.sdk.internal;

import org.junit.Assert;
import org.junit.Test;

public class DeviceCoreTests {

    @Test
    public void testAsIs() {
        DeviceCore.UniqueTimeGenerator simulator = new DeviceCore.UniqueTimeGenerator();

        long last = simulator.timestamp();

        for (int i = 0; i < 10000; i++) {
            long next = simulator.timestamp();
            Assert.assertNotSame(last, next);
        }
    }

    @Test
    public void testMidTimeChange() {
        DeviceCore.UniqueTimeGenerator simulator = new DeviceCore.UniqueTimeGenerator();

        long last = simulator.timestamp();

        for (int i = 0; i < 100; i++) {
            long next = simulator.timestamp();
            Assert.assertNotSame(last, next);
        }

        simulator.addition = -10000;

        for (int i = 0; i < 100; i++) {
            long next = simulator.timestamp();
            Assert.assertNotSame(last, next);
        }

        simulator.addition = 0;

        for (int i = 0; i < 100; i++) {
            long next = simulator.timestamp();
            Assert.assertNotSame(last, next);
        }

        simulator.addition = 10000;

        for (int i = 0; i < 100; i++) {
            long next = simulator.timestamp();
            Assert.assertNotSame(last, next);
        }
    }

    @Test
    public void testMidTimeRandomChange() {
        DeviceCore.UniqueTimeGenerator simulator = new DeviceCore.UniqueTimeGenerator();

        long last = simulator.timestamp();

        for (int i = 0; i < 100000; i++) {
            if (i % 30 == 0) {
                simulator.addition = Math.round(Math.random() * 10000 - 5000);
            }
            long next = simulator.timestamp();
            Assert.assertNotSame(last, next);
        }

        simulator.addition = 0;

        for (int i = 0; i < 100000; i++) {
            if (i % 30 == 0) {
                simulator.addition += Math.round(Math.random() * 1000 - 500);
            }
            long next = simulator.timestamp();
            Assert.assertNotSame(last, next);
        }
    }
}
