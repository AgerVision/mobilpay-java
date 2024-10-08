package ro.mobilPay.util;

import java.io.StringReader;
import java.security.Key;
import java.security.PublicKey;
import java.security.Security;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.util.encoders.Base64;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

public class OpenSSL {
    

    private OpenSSL() {
    }
    
    public static ListItem openssl_seal(String cert, String xml) throws Exception {
        StringReader sr = new StringReader(cert);
        PEMParser pm = new PEMParser(sr);
        X509CertificateHolder x509 = (X509CertificateHolder)pm.readObject();
        pm.close();
        PublicKey  p509Key = BouncyCastleProvider.getPublicKey(x509.getSubjectPublicKeyInfo());
        //System.out.println("p509key:"+p509Key.toString());
        KeyGenerator generator = KeyGenerator.getInstance("ARCFOUR");
        generator.init(128);
        SecretKey key = generator.generateKey();
        //System.out.println("generated key(env):"+key.toString());
        Cipher cc = Cipher.getInstance("ARCFOUR");
        cc.init(Cipher.ENCRYPT_MODE,key);
        
        byte[] ksrc = cc.doFinal(xml.getBytes());
        //System.out.println("ksrc len is:"+ksrc.length);
        Cipher ccRSA = Cipher.getInstance("RSA");
        ccRSA.init(Cipher.ENCRYPT_MODE,p509Key);
        byte[] evk = ccRSA.doFinal(key.getEncoded());
        //System.out.println("evk:"+evk.length);
        //System.out.println("evkcvt:"+new String(Base64.encode(evk)));
        ListItem li = new ListItem(""+1,new String(Base64.encode(evk)),
            new String(Base64.encode(ksrc)));
        
        //System.out.println("env_key is "+li.key);            
        //System.out.println("data is "+li.val);
        
        return li;
    }

    public static String openssl_unseal(String data, String env_key, String prvkey) throws Exception {
        StringReader sr = new StringReader(prvkey);
        PEMParser pm = new PEMParser(sr);
        Object o = pm.readObject();
        pm.close();
        Key key;

        JcaPEMKeyConverter converter = new JcaPEMKeyConverter();
        if (o instanceof PEMKeyPair) {
            PEMKeyPair keyPair = (PEMKeyPair) o;
            key = converter.getPrivateKey(keyPair.getPrivateKeyInfo());
        } else if (o instanceof PrivateKeyInfo) {
            PrivateKeyInfo privateKeyInfo = (PrivateKeyInfo) o;
            key = converter.getPrivateKey(privateKeyInfo);
        } else {
            System.err.println("ERROR: Unexpected private key format: " + (o != null ? o.getClass().getName() : "null"));
            return null;
        }

        Cipher ccRSA = Cipher.getInstance("RSA");
        ccRSA.init(Cipher.DECRYPT_MODE,key);
        byte[] envb = Base64.decode(env_key);
        byte[] decrkey = ccRSA.doFinal(envb);

        SecretKeySpec sc = new SecretKeySpec(decrkey,"ARCFOUR");
        
        Cipher cc = Cipher.getInstance("ARCFOUR");
        cc.init(Cipher.DECRYPT_MODE,sc);
        
        byte[] ksrc = cc.doFinal(Base64.decode(data));
        
        return new String(ksrc);
    }

    public static void extraInit() {
        Security.addProvider(new BouncyCastleProvider());
    }    
}
