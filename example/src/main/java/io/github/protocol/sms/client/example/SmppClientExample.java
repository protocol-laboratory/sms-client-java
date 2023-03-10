package io.github.protocol.sms.client.example;

import io.github.protocol.sms.smpp.client.SmppClient;
import io.github.protocol.sms.smpp.client.SmppClientConfig;

public class SmppClientExample {
    public static void main(String[] args) throws Exception {
        SmppClientConfig config = new SmppClientConfig();
        SmppClient smppClient = new SmppClient(config);
        smppClient.start();
    }
}
