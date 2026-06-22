package org.pmoci.kskillauth;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;

/**
 * Device-unique, non-exportable EC P-256 key in the Android Keystore (StrongBox when
 * available). Replaces IMEI as the possession factor: the private key never leaves secure
 * hardware, and the server verifies the ECDSA signature with the enrolled public key.
 */
final class DeviceKeyStore {
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "pmoci_otp_device_key";

    private DeviceKeyStore() {
    }

    /** Returns the existing keypair, generating it on first use. */
    static KeyPair getOrCreate() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);

        if (keyStore.containsAlias(KEY_ALIAS)) {
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(KEY_ALIAS, null);
            PublicKey publicKey = keyStore.getCertificate(KEY_ALIAS).getPublicKey();
            return new KeyPair(publicKey, privateKey);
        }

        return generate(true);
    }

    private static KeyPair generate(boolean tryStrongBox) throws Exception {
        KeyPairGenerator generator =
                KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE);

        KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256);

        if (tryStrongBox && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true);
        }

        try {
            generator.initialize(builder.build());
            return generator.generateKeyPair();
        } catch (Exception strongBoxFailure) {
            if (tryStrongBox) {
                // StrongBox unavailable on this device -> retry with TEE-backed key.
                return generate(false);
            }
            throw strongBoxFailure;
        }
    }

    /** base64(DER SPKI) of the public key — the format the 0852 server enrolls and verifies. */
    static String publicKeyBase64() throws Exception {
        return CryptoUtil.base64(getOrCreate().getPublic().getEncoded());
    }

    /** ECDSA-P256-SHA256 signature (DER) over the given message. */
    static byte[] sign(byte[] message) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(getOrCreate().getPrivate());
        signature.update(message);
        return signature.sign();
    }
}
