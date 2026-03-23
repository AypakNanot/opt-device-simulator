/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All Rights Reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v10.html
 */
package com.optel.rpcs;

import io.lighty.netconf.device.requests.notification.NotificationPublishService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;

public class ToasterServiceImpl implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ToasterServiceImpl.class);

    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;
    private NotificationPublishService notificationPublishService;
    private final AtomicLong count = new AtomicLong();
    private ScheduledFuture<?> future;

    public ToasterServiceImpl() {
        executor = Executors.newFixedThreadPool(1);
        scheduler = Executors.newScheduledThreadPool(1);
    }

    public void setNotificationPublishService(NotificationPublishService notificationPublishService) {
        this.notificationPublishService = notificationPublishService;
    }

    @Override
    public void close() {
        executor.shutdown();
        scheduler.shutdownNow();
    }


}
