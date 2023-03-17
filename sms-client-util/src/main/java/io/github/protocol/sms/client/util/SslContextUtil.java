/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.github.protocol.sms.client.util;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.Set;

public class SslContextUtil {

    public static SslContext buildFromJks(String keyPath,
                                          String keyPassword,
                                          String trustPath,
                                          String trustPassword,
                                          boolean skipSslVerify,
                                          Set<String> ciphers) {
        SslContextBuilder sslContextBuilder = SslContextBuilder.forClient();
        try (InputStream keyStream = Files.newInputStream(Paths.get(keyPath))) {
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(keyStream, keyPassword.toCharArray());
            String defaultKeyAlgorithm = KeyManagerFactory.getDefaultAlgorithm();
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(defaultKeyAlgorithm);
            keyManagerFactory.init(keyStore, keyPassword.toCharArray());
            sslContextBuilder.keyManager(keyManagerFactory);
        } catch (IOException ioe) {
            throw new RuntimeException("Failed to load key store", ioe);
        } catch (UnrecoverableKeyException | CertificateException | KeyStoreException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        try (InputStream trustStream = Files.newInputStream(Paths.get(trustPath))) {
            if (skipSslVerify) {
                sslContextBuilder.trustManager(InsecureTrustManagerFactory.INSTANCE);
            } else {
                KeyStore trustStore = KeyStore.getInstance("JKS");
                trustStore.load(trustStream, trustPassword.toCharArray());
                String defaultTrustAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(defaultTrustAlgorithm);
                trustManagerFactory.init(trustStore);
                sslContextBuilder.trustManager(trustManagerFactory);
            }
            if (ciphers != null) {
                sslContextBuilder.ciphers(ciphers);
            }
        } catch (IOException ioe) {
            throw new RuntimeException("Failed to load trust store", ioe);
        } catch (CertificateException | KeyStoreException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        try {
            return sslContextBuilder.build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build ssl context", e);
        }
    }
}
