package ly.count.android.sdk;

import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Created by artem on 11/06/15. Taken from https://www.owasp.org/index.php/Certificate_and_Public_Key_Pinning
 */

// Many thanks to Nikolay Elenkov for feedback.
// Shamelessly based upon Moxie's example code (AOSP/Google did not offer code)
// http://www.thoughtcrime.org/blog/authenticity-is-broken-in-ssl-but-your-app-ha/
public final class CertificateTrustManager implements X509TrustManager {

    // DER encoded public keys
    private final List<byte[]> keys;

    // DER encoded certificates
    private final List<byte[]> certificates;

    public CertificateTrustManager(List<String> keys, List<String> certs) throws CertificateException {
        if ((keys == null || keys.size() == 0) && (certs == null || certs.size() == 0)) {
            throw new IllegalArgumentException("You must specify non-empty keys list or certs list");
        }

        this.keys = new ArrayList<>();
        if (keys != null) for (String key : keys) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate cert = cf.generateCertificate(new ByteArrayInputStream(Base64.decode(key, Base64.DEFAULT)));
            this.keys.add(cert.getPublicKey().getEncoded());
        }

        this.certificates = new ArrayList<>();
        if (certs != null) for (String cert : certs) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Certificate certificate = cf.generateCertificate(new ByteArrayInputStream(Base64.decode(cert, Base64.DEFAULT)));
            this.certificates.add(certificate.getEncoded());
        }
    }

    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        if (chain == null) {
            throw new IllegalArgumentException("PublicKeyManager: X509Certificate array is null");
        }

        if (!(chain.length > 0)) {
            throw new IllegalArgumentException("PublicKeyManager: X509Certificate is empty");
        }

        if (!(null != authType && authType.contains("RSA"))) {
            throw new CertificateException("PublicKeyManager: AuthType is not RSA");
        }

        // Perform customary SSL/TLS checks
        TrustManagerFactory tmf;
        try {
            tmf = TrustManagerFactory.getInstance("X509");
            tmf.init((KeyStore) null);

            for (TrustManager trustManager : tmf.getTrustManagers()) {
                ((X509TrustManager) trustManager).checkServerTrusted(chain, authType);
            }

        } catch (Exception e) {
            throw new CertificateException(e);
        }

        byte serverPublicKey[] = chain[0].getPublicKey().getEncoded();
        byte serverCertificate[] = chain[0].getEncoded();


        for (byte[] key : keys) {
            if (Arrays.equals(key, serverPublicKey)) {
                return;
            }
        }

        for (byte[] key : certificates) {
            if (Arrays.equals(key, serverCertificate)) {
                return;
            }
        }

        throw new CertificateException("Public keys didn't pass checks");
    }

    public void checkClientTrusted(X509Certificate[] xcs, String string) {
        // throw new
        // UnsupportedOperationException("checkClientTrusted: Not supported yet.");
    }

    public X509Certificate[] getAcceptedIssuers() {
        // throw new
        // UnsupportedOperationException("getAcceptedIssuers: Not supported yet.");
        return null;
    }
}
