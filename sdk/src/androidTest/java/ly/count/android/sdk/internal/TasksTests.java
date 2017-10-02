package ly.count.android.sdk.internal;

import android.support.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.reflect.Whitebox;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@RunWith(AndroidJUnit4.class)
public class TasksTests {
    private Tasks tasks;

    @Before
    public void setupEveryTest(){
        tasks = new Tasks("test");
    }

    @After
    public void cleanupEveryTests(){
        tasks.shutdown();
    }

    @Test
    public void testSetup(){
        Assert.assertNotNull(Whitebox.getInternalState(tasks, "executor"));
        Assert.assertNotNull(Whitebox.getInternalState(tasks, "pending"));
    }

    @Test
    public void testShutdown() throws Exception {
        Tasks other = new Tasks("test");
        other.run(new Tasks.Task<Object>(0L) {
            @Override
            public Object call() throws Exception {
                Thread.sleep(50);
                return true;
            }
        });
        other.shutdown();
        Assert.assertTrue(Whitebox.<ExecutorService>getInternalState(other, "executor").isShutdown());
        Assert.assertFalse(Whitebox.<ExecutorService>getInternalState(other, "executor").isTerminated());
        Thread.sleep(200);
        Assert.assertTrue(Whitebox.<ExecutorService>getInternalState(other, "executor").isShutdown());
        Assert.assertTrue(Whitebox.<ExecutorService>getInternalState(other, "executor").isTerminated());
    }

    @Test
    public void testCallback() throws Exception {
        final int result = 123;
        final Boolean[] called = new Boolean[]{false, false};

        tasks.run(new Tasks.Task<Object>(0L) {
            @Override
            public Object call() throws Exception {
                called[0] = true;
                return result;
            }
        }, new Tasks.Callback<Object>() {
            @Override
            public void call(Object param) throws Exception {
                Assert.assertEquals(result, param);
                called[1] = true;
            }
        });

        Thread.sleep(100);

        Assert.assertEquals(Boolean.TRUE, called[0]);
        Assert.assertEquals(Boolean.TRUE, called[1]);
    }

    @Test
    public void testTaskIdsWork() throws Exception {
        final int result = 123;
        final int[] modification = new int[]{0};

        Tasks.Task<Integer> task0 = new Tasks.Task<Integer>(0L) {
            @Override
            public Integer call() throws Exception {
                Thread.sleep(50);
                modification[0] = 0;
                return result;
            }
        };
        Tasks.Task<Integer> task1 = new Tasks.Task<Integer>(1L) {
            @Override
            public Integer call() throws Exception {
                Thread.sleep(50);
                modification[0] = 1;
                return result;
            }
        };
        Tasks.Task<Integer> task2 = new Tasks.Task<Integer>(1L) {
            @Override
            public Integer call() throws Exception {
                Thread.sleep(50);
                modification[0] = 2;
                return result;
            }
        };

        Future<Integer> future0 = tasks.run(task0);
        Future<Integer> future1 = tasks.run(task1);
        Future<Integer> future2 = tasks.run(task2);

        Assert.assertNotSame(future0, future1);
        Assert.assertSame(future1, future2);

        Thread.sleep(200);

        Assert.assertEquals(1, modification[0]);
    }
}
