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

package io.github.protocol.sms.cmpp.client;

public class ConnectResult {

    private final String authenticatorISMG;

    private final byte version;

    public ConnectResult(String authenticatorISMG, byte version) {
        this.authenticatorISMG = authenticatorISMG;
        this.version = version;
    }

    public String authenticatorISMG() {
        return authenticatorISMG;
    }

    public byte version() {
        return version;
    }
}
