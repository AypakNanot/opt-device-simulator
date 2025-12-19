/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All Rights Reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v10.html
 */
package com.optel.rpcs;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yang.gen.v1.urn.ccsa.yang.acc.connection.rev240702.Capacity;
import org.opendaylight.yang.gen.v1.urn.ccsa.yang.acc.connection.rev240702.capacity.CirOrTotalsize;
import org.opendaylight.yang.gen.v1.urn.ccsa.yang.acc.osu.rev240702.*;
import org.opendaylight.yang.gen.v1.urn.ccsa.yang.acc.osu.rev240702.osu.adjustment.notification.RequestedCapacity;
import org.opendaylight.yang.gen.v1.urn.ccsa.yang.acc.osu.rev240702.osu.adjustment.notification.RequestedCapacityBuilder;
import org.opendaylight.yang.gen.v1.urn.ccsa.yang.acc.yang.types.rev190213.Granularity;
import com.google.common.collect.Maps;
import org.opendaylight.yang.gen.v1.urn.ccsa.yang.acc.performance.rev210131.get.all.pm.state.output.PmStates;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.lighty.netconf.device.requests.notification.NotificationPublishService;
import org.opendaylight.yang.gen.v1.urn.ccsa.yang.acc.performance.rev210131.GetAllPmStateInput;
import org.opendaylight.yang.gen.v1.urn.ccsa.yang.acc.performance.rev210131.GetAllPmStateOutput;
import org.opendaylight.yang.gen.v1.urn.ccsa.yang.acc.performance.rev210131.GetAllPmStateOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.ccsa.yang.acc.performance.rev210131.get.all.pm.state.output.PmStatesBuilder;
import org.opendaylight.yang.gen.v1.urn.ccsa.yang.acc.performance.rev210131.pm.states.PmState;
import org.opendaylight.yang.gen.v1.urn.ccsa.yang.acc.performance.rev210131.pm.states.PmStateBuilder;
import org.opendaylight.yang.gen.v1.urn.ccsa.yang.acc.performance.rev210131.pm.states.PmStateKey;
import org.opendaylight.yangtools.binding.Augmentation;
import org.opendaylight.yangtools.yang.common.Decimal64;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ToasterServiceImpl implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ToasterServiceImpl.class);

    private final ExecutorService executor;
    private NotificationPublishService notificationPublishService;

    public ToasterServiceImpl() {
        executor = Executors.newFixedThreadPool(1);
    }


    public void setNotificationPublishService(NotificationPublishService notificationPublishService) {
        this.notificationPublishService = notificationPublishService;
    }

    @Override
    public void close() {
        executor.shutdown();
    }

    public ListenableFuture<RpcResult<GetAllPmStateOutput>> getAllPmState(GetAllPmStateInput input) {
        SettableFuture<RpcResult<GetAllPmStateOutput>> result = SettableFuture.create();
        executor.submit(() -> {
            GetAllPmStateOutputBuilder builder = new GetAllPmStateOutputBuilder();
            RpcResult<GetAllPmStateOutput> rpcResult = RpcResultBuilder.success(builder.build()).build();
            result.set(rpcResult);
            return rpcResult;
        });
        return result;
    }

    public ListenableFuture<RpcResult<SendOsuAdjustmentNotificationOutput>> sendOsuAdjustmentNotificationOutput(SendOsuAdjustmentNotificationInput input) {
        SettableFuture<RpcResult<SendOsuAdjustmentNotificationOutput>> result = SettableFuture.create();
        executor.submit(() -> {
            SendOsuAdjustmentNotificationOutputBuilder builder = new SendOsuAdjustmentNotificationOutputBuilder();
            OsuAdjustmentNotificationBuilder osuAdjustmentNotificationBuilder = new OsuAdjustmentNotificationBuilder();
            osuAdjustmentNotificationBuilder.setCtpName(input.getCtpName());
            osuAdjustmentNotificationBuilder.setFailReason(input.getFailReason());
            osuAdjustmentNotificationBuilder.setModifyResult(input.getModifyResult());
            osuAdjustmentNotificationBuilder.setOsuAdjustSerialNo(input.getOsuAdjustSerialNo());
            RequestedCapacityBuilder requestedCapacityBuilder = new RequestedCapacityBuilder();
            requestedCapacityBuilder.setCirOrTotalsize(Optional.ofNullable(input.getRequestedCapacity()).map(Capacity::getCirOrTotalsize).orElse(null));
            osuAdjustmentNotificationBuilder.setRequestedCapacity(requestedCapacityBuilder.build());
            notificationPublishService.publish(osuAdjustmentNotificationBuilder.build(), OsuAdjustmentNotification.QNAME);
            RpcResult<SendOsuAdjustmentNotificationOutput> rpcResult = RpcResultBuilder.success(builder.build()).build();
            result.set(rpcResult);
            return rpcResult;
        });
        return result;
    }
}
