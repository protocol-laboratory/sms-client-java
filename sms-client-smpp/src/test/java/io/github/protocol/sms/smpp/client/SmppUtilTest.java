package io.github.protocol.sms.smpp.client;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class SmppUtilTest {

    @Test
    public void cutMessagesTest() {
        String sliceOrigin = "aaaaaaaaaa";
        String sliceOrigin1 = "bbbbbbbbbb";
        String sliceOrigin2 = "cccccccccc";
        int sliceLength = sliceOrigin.getBytes(StandardCharsets.UTF_8).length;
        List<byte[]> origin = List.of(sliceOrigin.getBytes(StandardCharsets.UTF_8),
                sliceOrigin1.getBytes(StandardCharsets.UTF_8), sliceOrigin2.getBytes(StandardCharsets.UTF_8));
        List<byte[]> slices = SmppUtil.splitMessages((sliceOrigin + sliceOrigin1 + sliceOrigin2)
                .getBytes(StandardCharsets.UTF_8), sliceLength);
        for (int i = 0; i < slices.size(); i++) {
            byte[] target = slices.get(i);
            Assertions.assertArrayEquals(
                    SmppUtil.getUdhiHeader((byte) 3, (byte) i), Arrays.copyOfRange(target, 0, 7));
            Assertions.assertArrayEquals(origin.get(i), Arrays.copyOfRange(target, 7, target.length));
        }
    }

}
