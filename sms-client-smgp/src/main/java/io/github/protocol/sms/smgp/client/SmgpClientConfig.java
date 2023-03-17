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

package io.github.protocol.sms.smgp.client;

import lombok.ToString;

import java.util.Set;

@ToString
public class SmgpClientConfig {

    public String host = "localhost";

    public int port = 9000;

    public int ioThreadsNum;

    public boolean useSsl;

    public String keyStorePath;

    public String keyStorePassword;

    public String trustStorePath;

    public String trustStorePassword;

    public boolean skipSslVerify;

    public Set<String> ciphers;

    public SmgpClientConfig host() {
        return this;
    }

    public SmgpClientConfig port() {
        return this;
    }

    public SmgpClientConfig ioThreadsNum() {
        return this;
    }

    public SmgpClientConfig useSsl(boolean useSsl) {
        this.useSsl = useSsl;
        return this;
    }

    public SmgpClientConfig keyStorePath(String keyStorePath) {
        this.keyStorePath = keyStorePath;
        return this;
    }

    public SmgpClientConfig keyStorePassword(String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
        return this;
    }

    public SmgpClientConfig trustStorePath(String trustStorePath) {
        this.trustStorePath = trustStorePath;
        return this;
    }

    public SmgpClientConfig trustStorePassword(String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
        return this;
    }

    public SmgpClientConfig skipSslVerify(boolean skipSslVerify) {
        this.skipSslVerify = skipSslVerify;
        return this;
    }

    public SmgpClientConfig ciphers(Set<String> ciphers) {
        this.ciphers = ciphers;
        return this;
    }
}
