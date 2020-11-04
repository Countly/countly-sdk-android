package ly.count.android.sdk;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.InstrumentationRegistry.getContext;

@RunWith(AndroidJUnit4.class)
public class MultiThreadingTests {
    Countly mCountly;

    @Before
    public void setUp() {
        Countly.sharedInstance().setLoggingEnabled(true);

        mCountly = new Countly();
        mCountly.halt();
        mCountly.init((new CountlyConfig(getContext(), "appkey", "https://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting());
        mCountly.halt();
        mCountly.init((new CountlyConfig(getContext(), "appkey", "https://test.count.ly")).setDeviceId("1234").setLoggingEnabled(true).enableCrashReporting());
    }

    @Test
    public void busyThreads_1() throws BrokenBarrierException, InterruptedException {
        int eventThreads = 50;
        int locationThreads = 50;
        int viewThreads = 50;
        int crashThreads = 50;
        int ratingThreads = 50;

        final CyclicBarrier gate = new CyclicBarrier(eventThreads + locationThreads + viewThreads + crashThreads + ratingThreads + 1);
        List<Thread> runs = new ArrayList<>();

        for (int a = 0; a < eventThreads; a++) {
            runs.add(tBase(gate, a, 3, 0, createEventJob()));
        }

        for (int a = 0; a < locationThreads; a++) {
            runs.add(tBase(gate, a, 3, 0, createLocationJob()));
        }

        for (int a = 0; a < viewThreads; a++) {
            runs.add(tBase(gate, a, 3, 0, createViewJob()));
        }

        for (int a = 0; a < crashThreads; a++) {
            runs.add(tBase(gate, a, 3, 0, createCrashJob()));
        }

        for (int a = 0; a < ratingThreads; a++) {
            runs.add(tBase(gate, a, 3, 0, createRatingsJob()));
        }

        for (Thread t : runs) {
            t.start();
        }

        gate.await();

        for (Thread t : runs) {
            t.join();
        }

        //todo add validation
    }

    ThreadCall createEventJob() {
        return new ThreadCall() {
            @Override public void call(int threadNumber, int iter, long sleepTime) {
                Map<String, Object> segms = createSegmentation(iter);

                switch ((threadNumber + iter) % 7) {
                    case 0:
                        mCountly.events().recordEvent("key__" + threadNumber + "__" + iter);
                        break;
                    case 1:
                        mCountly.events().recordEvent("key__" + threadNumber + "__" + iter, iter + 21);
                        break;
                    case 2:
                        mCountly.events().recordEvent("key__" + threadNumber + "__" + iter, iter + 21, 66.66 + threadNumber);
                        break;
                    case 3:
                        mCountly.events().recordEvent("key__" + threadNumber + "__" + iter, segms);
                        break;
                    case 4:
                        mCountly.events().recordEvent("key__" + threadNumber + "__" + iter, segms, iter + 13);
                        break;
                    case 5:
                        mCountly.events().recordEvent("key__" + threadNumber + "__" + iter, segms, iter + 13, 123.345 + iter);
                        break;
                    case 6:
                        mCountly.events().recordEvent("key__" + threadNumber + "__" + iter, segms, iter + 1, 100.3 + iter, 1000 * iter);
                        break;
                }
            }
        };
    }

    ThreadCall createLocationJob() {
        return new ThreadCall() {
            @Override public void call(int threadNumber, int iter, long sleepTime) {
                switch ((threadNumber + iter) % 3) {
                    case 0:
                        mCountly.setLocation("aa" + iter, "vv" + iter, null, null);
                        break;
                    case 1:
                        mCountly.setLocation(null, null, iter + "," + iter, null);
                        break;
                    case 2:
                        mCountly.setLocation("aa" + iter, "vv" + iter, iter + "," + iter, iter + "." + iter + "." + iter + "." + iter);
                        break;
                }
            }
        };
    }

    ThreadCall createViewJob() {
        return new ThreadCall() {
            @Override public void call(int threadNumber, int iter, long sleepTime) {
                Map<String, Object> segms = createSegmentation(iter);

                switch ((threadNumber + iter) % 2) {
                    case 0:
                        mCountly.views().recordView("View Name:" + threadNumber);
                        break;
                    case 1:
                        mCountly.views().recordView("View Name:" + threadNumber, segms);
                        break;
                }
            }
        };
    }

    ThreadCall createCrashJob() {
        return new ThreadCall() {
            @Override public void call(int threadNumber, int iter, long sleepTime) {
                Map<String, Object> segms = createSegmentation(iter);

                switch ((threadNumber + iter) % 2) {
                    case 0:
                        mCountly.crashes().addCrashBreadcrumb("Crash crumb " + threadNumber + " " + iter);
                        mCountly.crashes().recordHandledException(new Throwable("Some message"), segms);
                        break;
                    case 1:
                        mCountly.crashes().addCrashBreadcrumb("Crash other crumb " + threadNumber + " " + iter);
                        mCountly.crashes().recordUnhandledException(new Throwable("Some Other message"), segms);
                        break;
                }
            }
        };
    }

    ThreadCall createRatingsJob() {
        return new ThreadCall() {
            @Override public void call(int threadNumber, int iter, long sleepTime) {
                mCountly.ratings().recordManualRating("widgetID: " + threadNumber, threadNumber % 5, "email" + threadNumber + "" + iter, "comment" + iter, true);
            }
        };
    }

    Thread tBase(final CyclicBarrier gate, final int threadNumber, final int iterationCount, final long sleepTime, final ThreadCall threadCall) {
        return new Thread() {
            @Override public void run() {
                try {
                    gate.await();
                } catch (BrokenBarrierException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                for (int a = 0; a < iterationCount; a++) {
                    threadCall.call(threadNumber, a, sleepTime);
/*
                    try {
                        //Thread.sleep(sleepTime + a % 1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }*/
                }
            }
        };
    }

    interface ThreadCall {
        void call(final int threadNumber, int iter, final long sleepTime);
    }

    Map<String, Object> createSegmentation(int a) {
        Map<String, Object> segms = new HashMap<>();
        segms.put("sk" + a, "f" + a);
        segms.put("sk" + a, true);
        segms.put("sk" + a, a * 5);
        segms.put("sk" + a, a * 5.5d);
        return segms;
    }
}
