package org.example;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.ContentSigner;

import javax.security.auth.x500.X500Principal;


public class Certificate {

    private static Certificate instance;

    private final Map<String, X509Certificate> cache = new HashMap<>();
    private final String issuer;
    private final KeyFactory keyFactory;
    private final PrivateKey caPriKey;
    private final Date notBeforeDate;
    private final Date notAfterDate;
    private final PublicKey serverPublicKey;
    private final PrivateKey serverPrivateKey;
    private final SslContext sslCtx;

    public static Certificate theCertificate() throws Exception {
        if (instance == null) {
            instance = new Certificate();
        }
        return instance;
    }

    public Certificate() throws Exception {
        // create a keyFactory instance to handle RSA keys, which will be used later to create or manipulate keys
        this.keyFactory = KeyFactory.getInstance("RSA");

        // configures the SSL context for the client side
        // effectively disables certificate verification, allowing client to trust all server certificates
        // regardless of their authenticity
        this.sslCtx = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();

        // load certificate from src/main/resources/
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        X509Certificate caCert = (X509Certificate) CertificateFactory
                .getInstance("X.509")
                .generateCertificate(cl.getResourceAsStream("ca.crt"));

        this.caPriKey = retrievePrivateKey(Objects.requireNonNull(cl.getResourceAsStream("ca_private.der")));
        this.issuer = retrieveIssuer(caCert);
        this.notBeforeDate = caCert.getNotBefore();
        this.notAfterDate = caCert.getNotAfter();

        // randomly generate a pair of public, private key per connection
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
        kpg.initialize(2048, new SecureRandom());
        KeyPair keyPair = kpg.genKeyPair();
        this.serverPrivateKey = keyPair.getPrivate();
        this.serverPublicKey = keyPair.getPublic();
    }

    public SslContext getSslContext() {
        return sslCtx;
    }

    private String retrieveIssuer(X509Certificate caCert) {
        X500Principal issuer = caCert.getIssuerX500Principal();
        String[] dnFields = issuer.getName(X500Principal.RFC2253).split(",");
        StringBuilder reversedIssuer = new StringBuilder();

        for (int i = dnFields.length - 1; i >= 0; i--) {
            reversedIssuer.append(dnFields[i].trim());
            if (i > 0) {
                reversedIssuer.append(", ");
            }
        }
        return reversedIssuer.toString();
    }

    private PrivateKey retrievePrivateKey(InputStream istream) throws IOException, InvalidKeySpecException {
//         Using try-with-resources to ensure streams are closed automatically
        try (ByteArrayOutputStream ostream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = istream.read(buffer)) != -1) {
                ostream.write(buffer, 0, length);
            }

            // Generate and return the PrivateKey from the full byte array
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(ostream.toByteArray()));
        } finally {
            istream.close(); // Ensure input stream is closed if try-with-resources isn't possible
        }
    }

    public X509Certificate getCertificate(String host) throws Exception {
        if (host == null || host.isEmpty()) {
            return null;
        }

        host = host.trim().toLowerCase();
        X509Certificate cert = cache.get(host);
        if (cert == null) {
            cert = createCert(host);
            cache.put(host, cert);
            return cert;
        }
        return cert;
    }

    private X509Certificate createCert(String ...hosts) throws IOException, GeneralSecurityException, OperatorCreationException {
        String subject = "C=US, ST=MA, L=BOS, O=tufts-project, OU=study, CN=" + hosts[0];
        JcaX509v3CertificateBuilder jv3Builder = new JcaX509v3CertificateBuilder(new X500Name(issuer),
                BigInteger.valueOf(System.currentTimeMillis() + (long) (Math.random() * 10000) + 1000),
                this.notBeforeDate,
                this.notAfterDate,
                new X500Name(subject),
                this.serverPublicKey);

        GeneralNames subjectAltName = new GeneralNames(
                Arrays.stream(hosts)
                        .map(host -> new GeneralName(GeneralName.dNSName, host))
                        .toArray(GeneralName[]::new)
        );
        jv3Builder.addExtension(Extension.subjectAlternativeName, false, subjectAltName);
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(this.caPriKey);
        return new JcaX509CertificateConverter().getCertificate(jv3Builder.build(signer));
    }

    public PrivateKey getServerPrivateKey() {
        return serverPrivateKey;
    }
}
