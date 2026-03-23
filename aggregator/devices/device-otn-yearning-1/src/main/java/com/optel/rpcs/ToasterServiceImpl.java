/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All Rights Reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v10.html
 */
package com.optel.rpcs;

import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.NumberUtil;
import com.google.common.collect.Sets;
import com.google.common.collect.Maps;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.lighty.netconf.device.requests.notification.NotificationPublishService;
import org.opendaylight.yang.gen.v1.com.optel.yang.otn.extension.rev241204.*;
import org.opendaylight.yang.gen.v1.com.optel.yang.otn.extension.rev241204.get.vcfgotn.vc.connection.output.Relation;
import org.opendaylight.yang.gen.v1.com.optel.yang.otn.extension.rev241204.get.vcfgotn.vc.connection.output.RelationBuilder;
import org.opendaylight.yang.gen.v1.com.optel.yang.otn.extension.rev241204.get.vcfgotn.vc.connection.output.RelationKey;
import org.opendaylight.yang.gen.v1.urn.ccsa.yang.acc.fgotn.rev240916.*;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ToasterServiceImpl implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ToasterServiceImpl.class);

    private final ExecutorService executor;
    private NotificationPublishService notificationPublishService;
    private AtomicInteger count = new AtomicInteger(1);

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

    public ListenableFuture<RpcResult<FgoduflexTwoWayDmOutput>> fgoduflexTwoWayDm(FgoduflexTwoWayDmInput input) {
        return submit(() -> {
            var builder = new FgoduflexTwoWayDmOutputBuilder();
            builder.setFgoduflexDelay(Uint64.ONE);
            return RpcResultBuilder.success(builder.build()).build();
        });
    }

    public ListenableFuture<RpcResult<SendFgoduflexNotificationOutput>> sendFgoduflexNotification(SendFgoduflexNotificationInput input) {
        return submit(() -> {
            FgoduflexNotificationBuilder builder = new FgoduflexNotificationBuilder();
            builder.setCtpName(input.getCtpName());
            builder.setFailReason(input.getFailReason());
            builder.setFgoduflexAdjustmentSerialNo(input.getFgoduflexAdjustmentSerialNo());
            builder.setModifyResult(input.getModifyResult());
            builder.setTsDetails(input.getTsDetails());
            notificationPublishService.publish(builder.build(), FgoduflexNotification.QNAME);
            return RpcResultBuilder.success(new SendFgoduflexNotificationOutputBuilder().build()).build();
        });
    }

    public ListenableFuture<RpcResult<ModifyFgoduflexConnectionCapacityOutput>> modifyFgoduflexConnectionCapacity(ModifyFgoduflexConnectionCapacityInput input) {
        SettableFuture<RpcResult<ModifyFgoduflexConnectionCapacityOutput>> result = SettableFuture.create();
        executor.submit(() -> {
            var builder = new ModifyFgoduflexConnectionCapacityOutputBuilder();
            result.set(RpcResultBuilder.success(builder.build()).build());
        });
        CompletableFuture.runAsync(() -> {
            ThreadUtil.sleep(3000L);
            int andIncrement = count.getAndIncrement();
            FgoduflexNotificationBuilder builder = new FgoduflexNotificationBuilder();
            if (NumberUtil.isOdd(andIncrement)) {
                builder.setModifyResult(BwAdjustmentResult.Success);
            } else {
                builder.setModifyResult(BwAdjustmentResult.Failure);
                builder.setFailReason("xxx");
            }
            builder.setCtpName(input.getFgoduflexCtpName());
            builder.setFgoduflexAdjustmentSerialNo(Uint64.ONE);
            builder.setTsDetails(input.getTsDetail());
            notificationPublishService.publish(builder.build(), FgoduflexNotification.QNAME);
        });
        return result;
    }

    public ListenableFuture<RpcResult<GetVcfgotnVcConnectionOutput>> getVcfgotnVcConnection(GetVcfgotnVcConnectionInput input) {
        return submit(() -> {
            var builder = new GetVcfgotnVcConnectionOutputBuilder();
            Map<RelationKey, Relation> map = new HashMap<>();
            Set<String> fgotnConnName = input.getFgotnConnName();
            Objects.requireNonNull(fgotnConnName).forEach(item -> {
                RelationKey relationKey = new RelationKey(item);
                RelationBuilder relationBuilder = new RelationBuilder();
                relationBuilder.setFgotnConnName(item);
                relationBuilder.setVcConnName(Set.of(item + " vc-conn-name1", item + " vc-conn-name2"));
                map.put(relationKey, relationBuilder.build());
            });
            builder.setRelation(map);
            return RpcResultBuilder.success(builder.build()).build();
        });
    }

    private <T> ListenableFuture<RpcResult<T>> submit(java.util.concurrent.Callable<RpcResult<T>> task) {
        SettableFuture<RpcResult<T>> result = SettableFuture.create();
        executor.submit(() -> {
            try {
                result.set(task.call());
            } catch (Exception e) {
                LOG.error("Error executing task", e);
                result.setException(e);
            }
        });
        return result;
    }
}
