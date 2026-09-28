/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.dpdp.accelerator.common.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.core.util.CryptoException;
import org.wso2.carbon.core.util.CryptoUtil;
import org.wso2.dpdp.accelerator.common.config.DPDPConfigParser;
import org.wso2.dpdp.accelerator.common.exception.DPDPSystemException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utility for keystore-backed reversible encryption and decryption of sensitive
 * values.
 * Wraps Carbon's {@link CryptoUtil} and provides fail-fast cryptographic
 * operations.
 */
public final class CryptoUtils {

    private static final Log LOG = LogFactory.getLog(CryptoUtils.class);

    private static final byte[] TEST_KEY_BYTES = "DPDP_TEST_SECRET_KEY_16_BYTES!".substring(0, 16)
            .getBytes(StandardCharsets.UTF_8);
    private static final String TEST_CIPHER_PREFIX = "dpdp_test_enc:";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private static CryptoUtil cryptoUtilInstance;
    private static Boolean encryptionEnabledOverride;
    private static boolean testModeEnabled = false;

    private CryptoUtils() {

    }

    /**
     * Sets whether test-mode encryption/decryption is enabled.
     * When enabled, uses a local hardcoded key instead of Carbon CryptoUtil.
     * This must ONLY be used for unit and integration testing.
     *
     * @param testMode true to enable test mode, false otherwise
     */
    public static void setTestModeEnabled(boolean testMode) {

        testModeEnabled = testMode;
    }

    /**
     * Sets an explicit override for whether shared secret encryption is enabled,
     * primarily for unit and integration testing.
     *
     * @param encryptionEnabled the override value, or {@code null} to reset to configuration
     */
    public static void setEncryptionEnabled(Boolean encryptionEnabled) {

        encryptionEnabledOverride = encryptionEnabled;
    }

    /**
     * Checks whether shared secret encryption at rest is enabled.
     *
     * @return true if encryption is enabled, false otherwise
     */
    public static boolean isEncryptionEnabled() {

        if (encryptionEnabledOverride != null) {
            return encryptionEnabledOverride;
        }
        try {
            return DPDPConfigParser.getInstance().isEventNotificationEncryptSharedSecret();
        } catch (Exception e) {
            LOG.error("Failed to read encryption configuration; defaulting to disabled.", e);
            return false;
        }
    }

    /**
     * Sets a mock or custom {@link CryptoUtil} instance, primarily for unit
     * testing.
     *
     * @param cryptoUtil the CryptoUtil instance to use, or {@code null} to reset
     */
    public static void setCryptoUtil(CryptoUtil cryptoUtil) {

        cryptoUtilInstance = cryptoUtil;
    }

    private static boolean isCarbonCryptoAvailable() {

        return System.getProperty("carbon.home") != null;
    }

    /**
     * Encrypts the provided plaintext string using Carbon's keystore-backed
     * reversible encryption
     * and returns the Base64-encoded ciphertext.
     *
     * @param plainText the plaintext to encrypt
     * @return Base64-encoded ciphertext, or the input if null or empty
     * @throws DPDPSystemException if encryption fails
     */
    public static String encrypt(String plainText) {

        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        if (!isEncryptionEnabled()) {
            return plainText;
        }

        if (cryptoUtilInstance != null || isCarbonCryptoAvailable()) {
            try {
                CryptoUtil cryptoUtil = cryptoUtilInstance != null
                        ? cryptoUtilInstance
                        : CryptoUtil.getDefaultCryptoUtil();
                if (cryptoUtil == null) {
                    throw new DPDPSystemException("CryptoUtil is not available or registered.");
                }
                return cryptoUtil.encryptAndBase64Encode(plainText.getBytes(StandardCharsets.UTF_8));
            } catch (CryptoException e) {
                LOG.error("Error occurred while encrypting sensitive value", e);
                throw new DPDPSystemException("Error occurred while encrypting sensitive value", e);
            }
        }

        if (testModeEnabled) {
            return encryptTest(plainText);
        }
        throw new DPDPSystemException("CryptoUtil is not available; cannot encrypt sensitive value.");
    }

    /**
     * Decrypts the provided Base64-encoded ciphertext using Carbon's
     * keystore-backed reversible encryption. If the stored value is plaintext,
     * it is returned as-is.
     *
     * @param cipherText the Base64-encoded ciphertext to decrypt, or plaintext
     * @return the decrypted plaintext string, or the input if null or empty
     * @throws DPDPSystemException if decryption of an encrypted value fails
     */
    public static String decrypt(String cipherText) {

        if (cipherText == null || cipherText.isEmpty()) {
            return cipherText;
        }

        if (cipherText.startsWith(TEST_CIPHER_PREFIX)) {
            if (!testModeEnabled) {
                throw new DPDPSystemException(
                        "Test cipher prefix found but test mode is not enabled; refusing to decrypt.");
            }
            return decryptTest(cipherText);
        }

        if (!isEncryptedValue(cipherText)) {
            return cipherText;
        }

        if (cryptoUtilInstance != null || isCarbonCryptoAvailable()) {
            try {
                CryptoUtil cryptoUtil = cryptoUtilInstance != null
                        ? cryptoUtilInstance
                        : CryptoUtil.getDefaultCryptoUtil();
                if (cryptoUtil == null) {
                    throw new DPDPSystemException("CryptoUtil is not available or registered.");
                }
                byte[] decryptedBytes = cryptoUtil.base64DecodeAndDecrypt(cipherText);
                return new String(decryptedBytes, StandardCharsets.UTF_8);
            } catch (CryptoException | IllegalArgumentException e) {
                LOG.error("Error occurred while decrypting sensitive value", e);
                throw new DPDPSystemException("Error occurred while decrypting sensitive value", e);
            }
        }

        LOG.error("CryptoService is not available and ciphertext does not match test cipher prefix.");
        throw new DPDPSystemException("CryptoService is not registered and ciphertext cannot be decrypted.");
    }

    private static boolean isEncryptedValue(String value) {

        if (value == null || value.isEmpty()) {
            return false;
        }
        if (value.startsWith(TEST_CIPHER_PREFIX)) {
            return true;
        }
        return value.startsWith("eyJ");
    }

    private static String encryptTest(String plainText) {

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(TEST_KEY_BYTES, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherBytes, 0, combined, iv.length, cipherBytes.length);
            return TEST_CIPHER_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new DPDPSystemException("Error encrypting value in test mode", e);
        }
    }

    private static String decryptTest(String cipherText) {

        try {
            String encoded = cipherText.substring(TEST_CIPHER_PREFIX.length());
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            byte[] cipherBytes = new byte[combined.length - iv.length];
            System.arraycopy(combined, iv.length, cipherBytes, 0, cipherBytes.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(TEST_KEY_BYTES, "AES"),
                    new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plainBytes = cipher.doFinal(cipherBytes);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new DPDPSystemException("Error decrypting value in test mode", e);
        }
    }
}
