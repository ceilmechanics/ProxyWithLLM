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
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.ContentSigner;


public class Certificate {

    private static Certificate instance;

    private Map<String, X509Certificate> cache = new HashMap<>();
//    private SslContext sslCtx;
    private String issuer;
    private KeyFactory keyFactory;
    private PrivateKey caPriKey;
    private Date notBeforeDate;
    private Date notAfterDate;
    private PublicKey serverPublicKey;
    private PrivateKey serverPrivateKey;

    public static Certificate getInstance() throws GeneralSecurityException, IOException {
        if (instance == null) {
            instance = new Certificate();
        }
        return instance;
    }

    private Certificate() throws GeneralSecurityException, IOException {
        init();
    }

    public void init() throws GeneralSecurityException, IOException {
        // create a keyFactory instance to handle RSA keys, which will be used later to create or manipulate keys
        this.keyFactory = KeyFactory.getInstance("RSA");

        // configures the SSL context for the client side
        // effectively disables certificate verification, allowing client to trust all server certificates
        // regardless of their authenticity
        // we've uploaded the root certificate in browser, do not need this any more
//        this.sslCtx = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();

        // load certificate from src/main/resources/
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        X509Certificate caCert = (X509Certificate) CertificateFactory
                .getInstance("X.509")
                .generateCertificate(
                        cl.getResourceAsStream("ca.crt")
                );

        // get root certificate's private key, issuer, valid & expiration date,
        this.caPriKey = retrievePrivateKey(Objects.requireNonNull(cl.getResourceAsStream("ca_private.der")));
        this.issuer = caCert.getIssuerX500Principal().getName();
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

    private PrivateKey retrievePrivateKey(InputStream istream) throws IOException, InvalidKeySpecException {
        ByteArrayOutputStream ostream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ( (length = istream.read(buffer)) != -1) {
            ostream.write(buffer, 0, length);
        }
        istream.close();
        ostream.close();

        return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(buffer));
    }

    public X509Certificate getCertificate(String host) throws GeneralSecurityException, IOException, OperatorCreationException {
        if (host == null || host.isEmpty()) {
            return null;
        }

        X509Certificate cert = cache.get(host);
        if (cert == null) {
            host = host.trim().toLowerCase();
            cert = createCert(host);
            cache.put(host, cert);
        }
        return cert;
    }

    public static X509Certificate createCert(String host) throws IOException, GeneralSecurityException, OperatorCreationException {
        JcaX509v3CertificateBuilder jv3Builder = new JcaX509v3CertificateBuilder(new X500Name(instance.issuer),
                // requires unique serial number to resolve unsafe certificate issue
                // to avoid serial number collision, randomly generate it with current timestamp
                BigInteger.valueOf(System.currentTimeMillis() + (long) (Math.random() * 10000) + 1000),
                instance.notBeforeDate,
                instance.notAfterDate,
                new X500Name("C=US, ST=MA, L=Boston, O=Tufts-cs112-project, OU=study, CN=" + host),
                instance.serverPublicKey);

        // SAN to make cert safe on browser
        jv3Builder.addExtension(Extension.subjectAlternativeName,
                false,
                new GeneralName(GeneralName.dNSName, host)
        );

        // uses the private key of root certificate
        // to sign the dynamically generated certificates
        // client should receive a certificate that claims to be from target server (google.com),
        // but indeed is signed by the proxy’s root CA.
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(instance.caPriKey);
        return new JcaX509CertificateConverter().getCertificate(jv3Builder.build(signer));
    }

    public PrivateKey getServerPrivateKey() {
        return serverPrivateKey;
    }


}
