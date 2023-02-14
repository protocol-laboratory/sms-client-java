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

package io.github.protocol.sms.smpp.client;

import lombok.ToString;

@ToString
public class SmppClientConfig {

    public String host = "localhost";

    public int port;

    public int ioThreadsNum;

    public BindMode bindMode;

    public boolean autoBind;

    public int heartbeatIntervalSeconds;

    public String systemId;

    public String password;

    public String systemType;

    public byte interfaceVersion;

    public byte addrTon;

    public byte addrNpi;

    public String addressRange;

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

    public SmppClientConfig bindMode(BindMode bindMode) {
        this.bindMode = bindMode;
        return this;
    }

    public SmppClientConfig autoBind(boolean autoBind) {
        this.autoBind = autoBind;
        return this;
    }

    public SmppClientConfig heartbeatIntervalSeconds(int heartbeatIntervalSeconds) {
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
        return this;
    }

    public SmppClientConfig systemId(String systemId) {
        this.systemId = systemId;
        return this;
    }

    public SmppClientConfig password(String password) {
        this.password = password;
        return this;
    }

    public SmppClientConfig systemType(String systemType) {
        this.systemType = systemType;
        return this;
    }

    public SmppClientConfig interfaceVersion(byte interfaceVersion) {
        this.interfaceVersion = interfaceVersion;
        return this;
    }

    public SmppClientConfig addrTon(byte addrTon) {
        this.addrTon = addrTon;
        return this;
    }

    public SmppClientConfig addrNpi(byte addrNpi) {
        this.addrNpi = addrNpi;
        return this;
    }

    public SmppClientConfig addressRange(String addressRange) {
        this.addressRange = addressRange;
        return this;
    }
}
