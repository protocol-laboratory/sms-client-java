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

import io.github.protocol.codec.smpp.SmppBindReceiver;
import io.github.protocol.codec.smpp.SmppBindReceiverBody;
import io.github.protocol.codec.smpp.SmppBindReceiverResp;
import io.github.protocol.codec.smpp.SmppBindTransceiver;
import io.github.protocol.codec.smpp.SmppBindTransceiverBody;
import io.github.protocol.codec.smpp.SmppBindTransceiverResp;
import io.github.protocol.codec.smpp.SmppBindTransmitter;
import io.github.protocol.codec.smpp.SmppBindTransmitterBody;
import io.github.protocol.codec.smpp.SmppBindTransmitterResp;
import io.github.protocol.codec.smpp.SmppConst;
import io.github.protocol.codec.smpp.SmppDecoder;
import io.github.protocol.codec.smpp.SmppDeliverSm;
import io.github.protocol.codec.smpp.SmppDeliverSmResp;
import io.github.protocol.codec.smpp.SmppEncoder;
import io.github.protocol.codec.smpp.SmppEnquireLink;
import io.github.protocol.codec.smpp.SmppEnquireLinkResp;
import io.github.protocol.codec.smpp.SmppHeader;
import io.github.protocol.codec.smpp.SmppMessage;
import io.github.protocol.codec.smpp.SmppQuerySmResp;
import io.github.protocol.codec.smpp.SmppSubmitMultiResp;
import io.github.protocol.codec.smpp.SmppSubmitSm;
import io.github.protocol.codec.smpp.SmppSubmitSmBody;
import io.github.protocol.codec.smpp.SmppSubmitSmResp;
import io.github.protocol.sms.client.util.BoundAtomicInt;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SmppClient extends SimpleChannelInboundHandler<SmppMessage> {

    private final SmppClientConfig config;

    private final BoundAtomicInt seq;

    private volatile CompletableFuture<BindResult> bindResultFuture;

    private final Map<Integer, CompletableFuture<SubmitSmResult>> submitSmFutures;

    private volatile State state;

    private EventLoopGroup group;

    private ChannelHandlerContext ctx;

    public SmppClient(SmppClientConfig config) {
        this.config = config;
        this.seq = new BoundAtomicInt(0x7FFFFFFF);
        this.submitSmFutures = new ConcurrentHashMap<>();
        this.state = State.None;
    }

    public void start() throws Exception {
        if (group != null) {
            throw new IllegalStateException("smpp client already started");
        }
        log.info("begin start smpp client, config is {}", config);
        if (config.ioThreadsNum > 0) {
            group = new NioEventLoopGroup(config.ioThreadsNum);
        } else {
            group = new NioEventLoopGroup();
        }
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.remoteAddress(config.host, config.port);
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new SmppDecoder());
                        p.addLast(SmppEncoder.INSTANCE);
                        p.addLast(SmppClient.this);
                    }
                });
        ChannelFuture channelFuture = bootstrap.connect().sync();
        if (channelFuture.isSuccess()) {
            log.info("smpp client started");
        } else {
            log.error("smpp client start failed", channelFuture.cause());
            throw new Exception("smpp client start failed", channelFuture.cause());
        }
    }

    public CompletableFuture<BindResult> bindTransmitterAsync(SmppBindTransmitterBody bindTransmitterBody) {
        CompletableFuture<BindResult> future = new CompletableFuture<>();
        SmppHeader header = new SmppHeader(SmppConst.BIND_TRANSMITTER_ID, seq.nextVal());
        ctx.writeAndFlush(new SmppBindTransmitter(header, bindTransmitterBody)).addListener(new GenericFutureListener<Future<? super Void>>() {
            @Override
            public void operationComplete(Future<? super Void> f) throws Exception {
                if (f.isSuccess()) {
                    bindResultFuture = future;
                } else {
                    future.completeExceptionally(f.cause());
                }
            }
        });
        return future;
    }

    public CompletableFuture<SubmitSmResult> submitSmAsync(SmppSubmitSmBody submitSmBody) {
        CompletableFuture<SubmitSmResult> future = new CompletableFuture<>();
        if (config.bindMode == BindMode.Transceiver) {
            throw new UnsupportedOperationException("Transceiver mode not support submitSmAsync");
        }
        if (state != State.Ready) {
            future.completeExceptionally(new IllegalStateException("Smpp Client not ready"));
            return future;
        }
        SmppHeader header = new SmppHeader(SmppConst.SUBMIT_SM_ID, seq.nextVal());
        ctx.writeAndFlush(new SmppSubmitSm(header, submitSmBody)).addListener(f -> {
            if (f.isSuccess()) {
                submitSmFutures.put(header.sequenceNumber(), future);
            } else {
                future.completeExceptionally(f.cause());
            }
        });
        return future;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
        if (config.autoBind) {
            SmppHeader header;
            SmppMessage smppMessage;
            switch (config.bindMode) {
                case Receiver:
                    header = new SmppHeader(SmppConst.BIND_RECEIVER_ID, seq.nextVal());
                    SmppBindReceiverBody bindReceiverBody = new SmppBindReceiverBody(config.systemId, config.password,
                            config.systemType, config.interfaceVersion, config.addrTon,
                            config.addrNpi, config.addressRange);
                    smppMessage = new SmppBindReceiver(header, bindReceiverBody);
                    break;
                case Transmitter:
                    header = new SmppHeader(SmppConst.BIND_TRANSMITTER_ID, seq.nextVal());
                    SmppBindTransmitterBody bindTransmitterBody = new SmppBindTransmitterBody(config.systemId, config.password,
                            config.systemType, config.interfaceVersion, config.addrTon,
                            config.addrNpi, config.addressRange);
                    smppMessage = new SmppBindTransmitter(header, bindTransmitterBody);
                    break;
                case Transceiver:
                    header = new SmppHeader(SmppConst.BIND_TRANSCEIVER_ID, seq.nextVal());
                    SmppBindTransceiverBody bindTransceiverBody = new SmppBindTransceiverBody(config.systemId,
                            config.password, config.systemType, config.interfaceVersion, config.addrTon,
                            config.addrNpi, config.addressRange);
                    smppMessage = new SmppBindTransceiver(header, bindTransceiverBody);
                    break;
                default:
                    throw new IllegalStateException("Unknown bind mode: " + config.bindMode);
            }
            ChannelFuture channelFuture = ctx.writeAndFlush(smppMessage);
            channelFuture.addListener(f -> {
                if (f.isSuccess()) {
                    state = State.Connecting;
                } else {
                    state = State.None;
                }
            });
        }
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, SmppMessage msg) throws Exception {
        if (msg instanceof SmppBindReceiver) {
            processBindReceiver((SmppBindReceiver) msg);
        } else if (msg instanceof SmppBindReceiverResp) {
            processBindReceiverResp((SmppBindReceiverResp) msg);
        } else if (msg instanceof SmppBindTransmitterResp) {
            processBindTransmitterResp((SmppBindTransmitterResp) msg);
        } else if (msg instanceof SmppQuerySmResp) {
            processQuerySmResp((SmppQuerySmResp) msg);
        } else if (msg instanceof SmppSubmitSmResp) {
            processSubmitSmResp((SmppSubmitSmResp) msg);
        } else if (msg instanceof SmppDeliverSm) {
            processDeliverSm((SmppDeliverSm) msg);
        } else if (msg instanceof SmppDeliverSmResp) {
            processDeliverSmResp((SmppDeliverSmResp) msg);
        } else if (msg instanceof SmppBindTransceiverResp) {
            processBindTransceiverResp((SmppBindTransceiverResp) msg);
        } else if (msg instanceof SmppEnquireLinkResp) {
            processEnquireLinkResp((SmppEnquireLinkResp) msg);
        } else if (msg instanceof SmppSubmitMultiResp) {
            processSubmitMultiResp((SmppSubmitMultiResp) msg);
        }
    }

    private void processBindReceiver(SmppBindReceiver bindReceiver) {
        throw new IllegalStateException("Client side can't process bind receiver request");
    }

    private void processBindReceiverResp(SmppBindReceiverResp bindReceiverResp) {
        if (config.bindMode != BindMode.Receiver) {
            throw new IllegalStateException("Client mode is not receiver");
        }
        if (bindReceiverResp.header().commandStatus() == 0) {
            bindReady(bindReceiverResp.body().systemId());
            state = State.Ready;
        } else {
            state = State.None;
        }
    }

    private void processBindTransmitterResp(SmppBindTransmitterResp bindTransmitterResp) {
        if (config.bindMode != BindMode.Transmitter) {
            throw new IllegalStateException("Client mode is not transmitter");
        }
        if (bindTransmitterResp.header().commandStatus() == 0) {
            bindReady(bindTransmitterResp.body().systemId());
            state = State.Ready;
        } else {
            state = State.None;
        }
    }

    private void processQuerySmResp(SmppQuerySmResp querySmResp) {
    }

    private void processSubmitSmResp(SmppSubmitSmResp submitSmResp) {
        CompletableFuture<SubmitSmResult> future = submitSmFutures.remove(submitSmResp.header().sequenceNumber());
        if (future != null) {
            future.complete(new SubmitSmResult(submitSmResp.body().messageId()));
        }
    }

    private void processDeliverSm(SmppDeliverSm smppDeliverSm) {
    }

    private void processDeliverSmResp(SmppDeliverSmResp deliverSmResp) {
    }

    private void processBindTransceiverResp(SmppBindTransceiverResp bindTransceiverResp) {
        if (config.bindMode != BindMode.Transceiver) {
            throw new IllegalStateException("Client mode is not transceiver");
        }
        if (bindTransceiverResp.header().commandStatus() == 0) {
            state = State.Ready;
            bindReady(bindTransceiverResp.body().systemId());
        } else {
            state = State.None;
        }
    }

    private void processEnquireLinkResp(SmppEnquireLinkResp enquireLinkResp) {
    }

    private void processSubmitMultiResp(SmppSubmitMultiResp submitMultiResp) {
    }

    private void bindReady(String systemId) {
        if (bindResultFuture != null) {
            bindResultFuture.complete(new BindResult(systemId));
        }
        if (config.heartbeatIntervalSeconds > 0) {
            ctx.channel().eventLoop().scheduleWithFixedDelay(() -> {
                ctx.writeAndFlush(new SmppEnquireLink(new SmppHeader(SmppConst.ENQUIRE_LINK_ID, seq.nextVal())));
            }, 0, config.heartbeatIntervalSeconds, TimeUnit.SECONDS);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
    }

    public void stop() {
        if (group != null) {
            group.shutdownGracefully();
        }
    }
}
