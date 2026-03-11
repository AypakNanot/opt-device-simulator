/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All Rights Reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v10.html
 */
package com.optel.rpcs;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.optel.dto.GetPmDataDto;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.lighty.netconf.device.requests.notification.NotificationPublishService;
import io.lighty.netconf.device.utils.FileUtil;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.alarms.rev200630.AlarmState;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.alarms.rev200630.alarms.top.AlarmsBuilder;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.alarms.rev200630.alarms.top.alarms.Alarm;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.alarms.rev200630.alarms.top.alarms.AlarmBuilder;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.alarms.rev200630.alarms.top.alarms.AlarmKey;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.alarms.types.rev200630.CRITICAL;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.alarms.types.rev200630.HARDWAREALARM;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.openconfig.types.rev200630.Timeticks64;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.performance.rev200630.GetPmDataInput;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.performance.rev200630.GetPmDataOutput;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.performance.rev200630.GetPmDataOutputBuilder;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.performance.rev200630.get.pm.data.output.Performance;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.performance.rev200630.get.pm.data.output.PerformanceBuilder;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.performance.rev200630.get.pm.data.output.PerformanceKey;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.performance.rev200630.get.pm.data.output.performance.pm.data.value.InstantAvgMinMaxBuilder;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.performance.rev200630.get.pm.data.output.performance.pm.data.value.InstantBuilder;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.performance.types.rev200630.*;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.notification.rpc.rev200630.*;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.notification.rpc.rev200630.start.notification.input.AlarmParameter;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.notification.rpc.rev200630.start.notification.input.AlarmParameterKey;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.system.rev200630.AlarmsNotification;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.system.rev200630.AlarmsNotificationBuilder;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.system.rev200630.alarms.notification.DeleteBuilder;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.system.rev200630.alarms.notification.UpdateBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev200909.DateAndTime;
import org.opendaylight.yangtools.yang.common.Decimal64;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.annotation.Nonnull;
import javax.xml.bind.JAXBContext;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import java.io.InputStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.*;

public class ToasterServiceImpl implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ToasterServiceImpl.class);

    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;
    private NotificationPublishService notificationPublishService;
    private final AtomicLong count = new AtomicLong();
    private final AtomicLong id = new AtomicLong(1);
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

    @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_BAD_PRACTICE")
    public ListenableFuture<RpcResult<StartNotificationOutput>> startNotification(StartNotificationInput input) {
        SettableFuture<RpcResult<StartNotificationOutput>> result = SettableFuture.create();
        if (future != null) {
            future.cancel(true);
        }
        if (notificationPublishService != null) {
            int period = Integer.parseInt(input.getPeriod());
            Preconditions.checkArgument(period >= 0 && period <= 3600, "周期范围为0-3600");
            if (period == 0) {
                notificationPublishService.publish(getAlarmsNotification(input, Instant.now().getEpochSecond(), true), AlarmsNotification.QNAME);
            } else if (MapUtil.isNotEmpty(input.getAlarmParameter())) {
                AtomicLong second = new AtomicLong(Instant.now().getEpochSecond());
                future = scheduler.scheduleAtFixedRate(() -> {
                    long instant = second.get();
                    instant += period;
                    second.set(instant);
                    notificationPublishService.publish(getAlarmsNotification(input, instant, count.getAndIncrement() % 2 == 0), AlarmsNotification.QNAME);
                }, 0, period, TimeUnit.SECONDS);
            }
        }
        executor.submit(() -> {
            StartNotificationOutput output = new StartNotificationOutputBuilder().build();
            RpcResult<StartNotificationOutput> rpcResult = RpcResultBuilder.success(output).build();
            result.set(rpcResult);

        });
        return result;
    }

    private AlarmsNotification getAlarmsNotification(StartNotificationInput input, @Nonnull Long timeStamp, boolean isUpdate) {
        Map<AlarmParameterKey, AlarmParameter> alarmParameter = input.getAlarmParameter();
        Preconditions.checkArgument(CollUtil.isNotEmpty(alarmParameter), "alarm-parameter不能为空");
        AlarmsNotificationBuilder builder = new AlarmsNotificationBuilder();
        AlarmsBuilder alarmsBuilder = new AlarmsBuilder();
        Map<AlarmKey, Alarm> map = new HashMap<>();
        alarmParameter.values().forEach(item -> {
            String uuid = String.valueOf(id.getAndIncrement());
            AlarmBuilder alarmBuilder = new AlarmBuilder();
            alarmBuilder.setId(uuid);
            var stateBuilder = new org.opendaylight.yang.gen.v1.http.openconfig.net.yang.alarms.rev200630.alarms.top.alarms.alarm.StateBuilder();
            stateBuilder.setId(uuid);
            stateBuilder.setAlarmAbbreviate(item.getAlarmCode());
            stateBuilder.setResource(item.getObjectName());
            stateBuilder.setSeverity(CRITICAL.VALUE);
            stateBuilder.setText("simulator alarm");
            stateBuilder.setTimeCreated(new Timeticks64(Uint64.valueOf(timeStamp * 1000_000_000)));
            stateBuilder.setTypeId(new AlarmState.TypeId(HARDWAREALARM.VALUE));
            alarmBuilder.setState(stateBuilder.build());
            map.put(new AlarmKey(uuid), alarmBuilder.build());
        });
        alarmsBuilder.setAlarm(map);
        if (isUpdate) {
            UpdateBuilder updateBuilder = new UpdateBuilder();
            updateBuilder.setAlarms(alarmsBuilder.build());
            builder.setUpdate(updateBuilder.build());
        } else {
            DeleteBuilder deleteBuilder = new DeleteBuilder();
            deleteBuilder.setAlarms(alarmsBuilder.build());
            builder.setDelete(deleteBuilder.build());
        }
        return builder.build();
    }

    private DateAndTime getTime() {
        return new DateAndTime(getTimeStr());
    }

    private String getTimeStr() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
        return now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"));
    }


    public ListenableFuture<RpcResult<StopNotificationOutput>> stopNotification(StopNotificationInput input) {
        if (future != null) {
            future.cancel(true);
        }
        count.set(0);
        SettableFuture<RpcResult<StopNotificationOutput>> result = SettableFuture.create();
        executor.submit(() -> {
            StopNotificationOutput output = new StopNotificationOutputBuilder().build();
            RpcResult<StopNotificationOutput> rpcResult = RpcResultBuilder.success(output).build();
            result.set(rpcResult);
        });
        return result;
    }

    public ListenableFuture<RpcResult<GetPmDataOutput>> getPmData(GetPmDataInput input) {
        DateAndTime time = PmRpcType.CURRENT.equals(input.getPmType()) ? getTime() : getOffTime(getTime(), input.getPmGranularity());
        SettableFuture<RpcResult<GetPmDataOutput>> result = SettableFuture.create();
        executor.submit(() -> {
            try (InputStream is = FileUtil.getFile("get-pm-data")) {
                SAXParserFactory spf = SAXParserFactory.newInstance();
                spf.setNamespaceAware(false);
                XMLReader xmlReader = spf.newSAXParser().getXMLReader();
                SAXSource source = new SAXSource(xmlReader, new InputSource(is));
                GetPmDataDto reply = (GetPmDataDto) JAXBContext.newInstance(GetPmDataDto.class).createUnmarshaller().unmarshal(source);
                var outputBuilder = new GetPmDataOutputBuilder();
                outputBuilder.setGetPmResult(PmRpcResult.forName(reply.getGetPmResult()));
                Map<PerformanceKey, Performance> map = new HashMap<>();
                outputBuilder.setPerformance(map);
                reply.getPerformances().forEach(item -> {
                    PmParameterType pmParameterType = PmParameterType.forName(item.getPmParameter());
                    PmPointRef pmPointRef = PmPointRef.getDefaultInstance(item.getPmPoint());
                    PerformanceKey key = new PerformanceKey(time, pmParameterType, pmPointRef);
                    PerformanceBuilder performanceBuilder = new PerformanceBuilder();
                    performanceBuilder.setMonitoringDateTime(time);
                    performanceBuilder.setPmParameter(pmParameterType);
                    performanceBuilder.setPmPoint(pmPointRef);
                    if (item.getDigitalPmValue() != null) {
                        InstantBuilder instantBuilder = new InstantBuilder();
                        instantBuilder.setDigitalPmValue(Optional.ofNullable(item.getDigitalPmValue()).map(Decimal64::valueOf).orElse(null));
                        performanceBuilder.setPmDataValue(instantBuilder.build());
                    } else {
                        InstantAvgMinMaxBuilder instantAvgMinMaxBuilder = new InstantAvgMinMaxBuilder();
                        instantAvgMinMaxBuilder.setAverageValue(Optional.ofNullable(item.getaAverageValue()).map(Decimal64::valueOf).orElse(null));
                        instantAvgMinMaxBuilder.setCurrentValue(Optional.ofNullable(item.getCurrentValue()).map(Decimal64::valueOf).orElse(null));
                        instantAvgMinMaxBuilder.setMaxValue(Optional.ofNullable(item.getMaxValue()).map(Decimal64::valueOf).orElse(null));
                        instantAvgMinMaxBuilder.setMinValue(Optional.ofNullable(item.getMinValue()).map(Decimal64::valueOf).orElse(null));
                        performanceBuilder.setPmDataValue(instantAvgMinMaxBuilder.build());
                    }
                    map.put(key, performanceBuilder.build());
                });
                RpcResult<GetPmDataOutput> rpcResult = RpcResultBuilder.success(outputBuilder.build()).build();
                result.set(rpcResult);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        return result;
    }

    private DateAndTime getOffTime(DateAndTime time, PmGranularityType pmGranularity) {
        OffsetDateTime dateTime = OffsetDateTime.parse(time.getValue());
        OffsetDateTime target;
        target = dateTime.minusMinutes(PmGranularityType._15MIN.equals(pmGranularity) ? dateTime.getMinute() % 15 : dateTime.getMinute()).withSecond(0).withNano(0);
        if (PmGranularityType._24H.equals(pmGranularity)) {
            target = target.minusHours(target.getHour());
        }
        return new DateAndTime(target.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")));
    }

}
