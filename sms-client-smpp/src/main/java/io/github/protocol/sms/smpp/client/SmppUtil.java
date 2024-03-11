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

import io.github.protocol.codec.smpp.SmppConst;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SmppUtil {

    public static List<byte[]> splitMessages(byte[] messageContent, int sliceSize) {
        List<byte[]> slices = new ArrayList<>();
        sliceSize = sliceSize & SmppConst.UNSIGNED_BYTE_MAX;
        double sliceNum = Math.ceil(messageContent.length / (double) sliceSize);
        for (int i = 0; i < sliceNum; i++) {
            int from = i * sliceSize;
            int to = from + sliceSize;
            if (i == sliceNum - 1) {
                to = messageContent.length;
            }
            byte[] sliceMessage = Arrays.copyOfRange(messageContent, from, to);
            byte[] header = getUdhiHeader((byte) sliceNum, (byte) i);
            byte[] convertedMessage = new byte[header.length + sliceMessage.length];
            System.arraycopy(header, 0, convertedMessage, 0, header.length);
            System.arraycopy(sliceMessage, 0, convertedMessage, header.length, sliceMessage.length);
            slices.add(convertedMessage);
        }
        return slices;
    }

    public static byte[] getUdhiHeader(byte total, byte seq) {
        byte[] header = new byte[7];
        header[0] = 0x06;
        header[1] = 0x08;
        header[2] = 0x04;
        header[3] = 0x00;
        header[4] = 0x00;
        header[5] = total;
        header[6] = seq;
        return header;
    }

}
