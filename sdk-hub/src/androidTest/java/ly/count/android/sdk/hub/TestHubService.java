package ly.count.android.sdk.hub;

import android.content.Context;
import androidx.annotation.NonNull;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * The hub under test. It runs in its own process, so the test passes it the address of the mock
 * server through a file, the one thing both processes of the test app share.
 */
public class TestHubService extends CountlyHubService {
    static final String APP_KEY = "HUB_TEST_APP_KEY";
    private static final String SERVER_FILE = "hub_test_server_url";
    private static final Charset UTF_8 = Charset.forName("UTF-8");

    /**
     * Writes the server address the hub process reads when it starts.
     *
     * @param context the test context
     * @param serverUrl the mock server address
     * @throws IOException if the file cannot be written
     */
    static void writeServerUrl(@NonNull Context context, @NonNull String serverUrl) throws IOException {
        try (FileOutputStream out = new FileOutputStream(new File(context.getFilesDir(), SERVER_FILE))) {
            out.write(serverUrl.getBytes(UTF_8));
        }
    }

    /**
     * @return a hub that relays to the mock server and accepts the test app with the test app key
     */
    @Override
    protected @NonNull HubConfig onCreateHubConfig() {
        String serverUrl;
        File file = new File(getFilesDir(), SERVER_FILE);
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int read = 0;
            while (read < bytes.length) {
                int n = in.read(bytes, read, bytes.length - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            serverUrl = new String(bytes, 0, read, UTF_8);
        } catch (IOException e) {
            serverUrl = "http://127.0.0.1:1";
        }
        return new HubConfig(serverUrl)
            .allowApp(new HubAllowedApp(getPackageName(), APP_KEY))
            .setMaxRequestBytes(4 * 1024 * 1024);
    }
}
