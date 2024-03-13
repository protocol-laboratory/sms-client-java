/*
 * Copyright 2024 shoothzj <shoothzj@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.protocol.sms.smpp.client;

import lombok.ToString;

import java.util.Set;

@ToString
public class SmppClientConfig {

    public String host = "localhost";

    public int port = 2775;

    public int ioThreadsNum;

    public int heartbeatIntervalSeconds;

    public boolean useSsl;

    public String keyStorePath;

    public String keyStorePassword;

    public String trustStorePath;

    public String trustStorePassword;

    public boolean skipSslVerify;

    public Set<String> ciphers;

    public SmppClientConfig host(String host) {
        this.host = host;
        return this;
    }

    public SmppClientConfig port(int port) {
        this.port = port;
        return this;
    }

    public SmppClientConfig ioThreadsNum(int ioThreadsNum) {
        this.ioThreadsNum = ioThreadsNum;
        return this;
    }

    public SmppClientConfig heartbeatIntervalSeconds(int heartbeatIntervalSeconds) {
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
        return this;
    }

    public SmppClientConfig useSsl(boolean useSsl) {
        this.useSsl = useSsl;
        return this;
    }

    public SmppClientConfig keyStorePath(String keyStorePath) {
        this.keyStorePath = keyStorePath;
        return this;
    }

    public SmppClientConfig keyStorePassword(String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
        return this;
    }

    public SmppClientConfig trustStorePath(String trustStorePath) {
        this.trustStorePath = trustStorePath;
        return this;
    }

    public SmppClientConfig trustStorePassword(String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
        return this;
    }

    public SmppClientConfig skipSslVerify(boolean skipSslVerify) {
        this.skipSslVerify = skipSslVerify;
        return this;
    }

    public SmppClientConfig ciphers(Set<String> ciphers) {
        this.ciphers = ciphers;
        return this;
    }
}
