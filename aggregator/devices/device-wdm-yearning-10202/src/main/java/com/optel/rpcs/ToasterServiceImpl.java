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
import cn.hutool.core.text.CharSequenceUtil;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.optel.dto.AlarmNotificationKey;
import com.optel.dto.GetCurrentPerformanceMonitoringDataDto;
import com.optel.dto.GetHistoryAlarmsDto;
import com.optel.dto.GetHistoryPerformanceMonitoringDataDto;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.lighty.netconf.device.requests.notification.NotificationPublishService;
import io.lighty.netconf.device.utils.FileUtil;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.alarms.types.rev181121.*;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.notification.rev230426.EventNotification;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.notification.rev230426.EventNotificationBuilder;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.openconfig.types.rev230426.Granularity;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.openconfig.types.rev230426.ObjectType;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.openconfig.types.rev230426.Real;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.openconfig.types.rev230426.ThresholdType;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.alarms.rev230426.AlarmNotification;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.alarms.rev230426.AlarmNotificationBuilder;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.alarms.rev230426.alarms.top.Alarm;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.alarms.rev230426.alarms.top.AlarmBuilder;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.alarms.rev230426.alarms.top.AlarmKey;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.otdr.rev230426.*;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.performance.rev230426.*;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.performance.rev230426.get.current.performance.monitoring.data.output.PerformancesBuilder;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.performance.rev230426.gperformances.Performance;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.performance.rev230426.gperformances.PerformanceBuilder;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.performance.rev230426.gperformances.PerformanceKey;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.performance.rev230426.performance.performance.type.Analog;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.performance.rev230426.performance.performance.type.AnalogBuilder;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.performance.rev230426.performance.performance.type.DigitalBuilder;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.performance.rev230426.performance.performance.type.analog.AnalogPmValueBuilder;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.performance.types.rev230426.PmParameterType;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.rpc.rev230426.*;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.rpc.rev230426.get.history.alarms.output.AlarmsBuilder;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.rpc.rev230426.get.history.alarms.output.TcasBuilder;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.rpc.rev230426.get.oduk.delay.output.DelayDataValueBuilder;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.rpc.rev230426.start.notification.input.AlarmParameterKey;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.tcas.rev230426.TcaNotification;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.tcas.rev230426.TcaNotificationBuilder;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.tcas.rev230426.tcas.top.TcaBuilder;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.tcas.rev230426.tcas.top.TcaKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev200909.DateAndTime;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.bind.JAXBContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import java.io.IOException;
import java.io.InputStream;
import java.sql.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
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

    @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_BAD_PRACTICE")
    public ListenableFuture<RpcResult<GetCurrentPerformanceMonitoringDataOutput>> getCurrentPerformanceMonitoringData(GetCurrentPerformanceMonitoringDataInput input) {
        SettableFuture<RpcResult<GetCurrentPerformanceMonitoringDataOutput>> result = SettableFuture.create();
        DateAndTime time = getTime();
        executor.submit(() -> {
            try (InputStream is = FileUtil.getFile("get-current-performance-monitoring-data")) {
                SAXParserFactory spf = SAXParserFactory.newInstance();
                spf.setNamespaceAware(false);
                XMLReader xmlReader = spf.newSAXParser().getXMLReader();
                SAXSource source = new SAXSource(xmlReader, new InputSource(is));
                GetCurrentPerformanceMonitoringDataDto reply = (GetCurrentPerformanceMonitoringDataDto) JAXBContext.newInstance(GetCurrentPerformanceMonitoringDataDto.class).createUnmarshaller().unmarshal(source);
                var outputBuilder = new GetCurrentPerformanceMonitoringDataOutputBuilder();
                PerformancesBuilder performancesBuilder = new PerformancesBuilder();
                Map<PerformanceKey, Performance> map = new HashMap<>();
                reply.getPerformances().getPerformances().forEach(item -> {
                    Granularity granularity = Granularity.forName(item.getPmGranularity());
                    PmParameterType pmParameterType = PmParameterType.forName(item.getPmParameterName());
                    if (granularity == null || pmParameterType == null) {
                        return;
                    }
                    PerformanceKey key = new PerformanceKey(item.getObjectName(), granularity, pmParameterType, time);
                    PerformanceBuilder builder = new PerformanceBuilder();
                    builder.setBinNo(item.getBinNo());
                    builder.setObjectName(item.getObjectName());
                    builder.setObjectType(ObjectType.forName(item.getObjectType()));
                    if (item.getAnalogPmValue() != null) {
                        AnalogBuilder analogBuilder = new AnalogBuilder();
                        AnalogPmValueBuilder analogPmValueBuilder = new AnalogPmValueBuilder();
                        analogPmValueBuilder.setAverageValue(Optional.ofNullable(item.getAnalogPmValue().getAverageValue()).map(Decimal64::valueOf).map(Real::new).orElse(null));
                        analogPmValueBuilder.setCurrentValue(Optional.ofNullable(item.getAnalogPmValue().getCurrentValue()).map(Decimal64::valueOf).map(Real::new).orElse(null));
                        analogPmValueBuilder.setMaxValue(Optional.ofNullable(item.getAnalogPmValue().getMaxValue()).map(Decimal64::valueOf).map(Real::new).orElse(null));
                        analogPmValueBuilder.setMinValue(Optional.ofNullable(item.getAnalogPmValue().getMinValue()).map(Decimal64::valueOf).map(Real::new).orElse(null));
                        analogBuilder.setAnalogPmValue(analogPmValueBuilder.build());
                        Analog analog = analogBuilder.build();
                        builder.setPerformanceType(analog);
                    } else if (item.getDigitalPmValue() != null) {
                        DigitalBuilder digitalBuilder = new DigitalBuilder();
                        digitalBuilder.setDigitalPmValue(Optional.ofNullable(item.getDigitalPmValue()).map(Decimal64::valueOf).map(Real::new).orElse(null));
                        builder.setPerformanceType(digitalBuilder.build());
                    }
                    builder.setPmGranularity(input.getPmGranularity());
                    builder.setPmParameterName(pmParameterType);
                    builder.setPmStartTime(time);
                    map.put(key, builder.build());
                });
                performancesBuilder.setPerformance(map);
                outputBuilder.setPerformances(performancesBuilder.build());
                RpcResult<GetCurrentPerformanceMonitoringDataOutput> rpcResult = RpcResultBuilder.success(outputBuilder.build()).build();
                result.set(rpcResult);
                return rpcResult;
            } catch (Exception e) {
                LOG.error("Failed to get current performance monitoring data", e);
            }
            return null;
        });
        return result;
    }

    @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_BAD_PRACTICE")
    public ListenableFuture<RpcResult<GetHistoryPerformanceMonitoringDataOutput>> getHistoryPerformanceMonitoringData(GetHistoryPerformanceMonitoringDataInput input) {
        SettableFuture<RpcResult<GetHistoryPerformanceMonitoringDataOutput>> result = SettableFuture.create();
        DateAndTime time = getOffTime(input.getCollectStartTime(), input.getPmGranularity());
        executor.submit(() -> {
            try (InputStream is = FileUtil.getFile("get-history-performance-monitoring-data")) {
                SAXParserFactory spf = SAXParserFactory.newInstance();
                spf.setNamespaceAware(false);
                XMLReader xmlReader = spf.newSAXParser().getXMLReader();
                SAXSource source = new SAXSource(xmlReader, new InputSource(is));
                GetHistoryPerformanceMonitoringDataDto reply = (GetHistoryPerformanceMonitoringDataDto) JAXBContext.newInstance(GetHistoryPerformanceMonitoringDataDto.class).createUnmarshaller().unmarshal(source);
                var outputBuilder = new GetHistoryPerformanceMonitoringDataOutputBuilder();
                var performancesBuilder = new org.opendaylight.yang.gen.v1.urn.cmcc.yang.performance.rev230426.get.history.performance.monitoring.data.output.PerformancesBuilder();
                Map<PerformanceKey, Performance> map = new HashMap<>();
                reply.getPerformances().getPerformances().forEach(item -> {
                    Granularity granularity = Granularity.forName(item.getPmGranularity());
                    PmParameterType pmParameterType = PmParameterType.forName(item.getPmParameterName());
                    if (granularity == null || pmParameterType == null) {
                        return;
                    }
                    PerformanceKey key = new PerformanceKey(item.getObjectName(), granularity, pmParameterType, time);
                    PerformanceBuilder builder = new PerformanceBuilder();
                    builder.setBinNo(item.getBinNo());
                    builder.setObjectName(item.getObjectName());
                    builder.setObjectType(ObjectType.forName(item.getObjectType()));
                    if (item.getAnalogPmValue() != null) {
                        AnalogBuilder analogBuilder = new AnalogBuilder();
                        AnalogPmValueBuilder analogPmValueBuilder = new AnalogPmValueBuilder();
                        analogPmValueBuilder.setAverageValue(Optional.ofNullable(item.getAnalogPmValue().getAverageValue()).map(Decimal64::valueOf).map(Real::new).orElse(null));
                        analogPmValueBuilder.setCurrentValue(Optional.ofNullable(item.getAnalogPmValue().getCurrentValue()).map(Decimal64::valueOf).map(Real::new).orElse(null));
                        analogPmValueBuilder.setMaxValue(Optional.ofNullable(item.getAnalogPmValue().getMaxValue()).map(Decimal64::valueOf).map(Real::new).orElse(null));
                        analogPmValueBuilder.setMinValue(Optional.ofNullable(item.getAnalogPmValue().getMinValue()).map(Decimal64::valueOf).map(Real::new).orElse(null));
                        analogBuilder.setAnalogPmValue(analogPmValueBuilder.build());
                        Analog analog = analogBuilder.build();
                        builder.setPerformanceType(analog);
                    } else if (item.getDigitalPmValue() != null) {
                        DigitalBuilder digitalBuilder = new DigitalBuilder();
                        digitalBuilder.setDigitalPmValue(Optional.ofNullable(item.getDigitalPmValue()).map(Decimal64::valueOf).map(Real::new).orElse(null));
                        builder.setPerformanceType(digitalBuilder.build());
                    }
                    builder.setPmGranularity(input.getPmGranularity());
                    builder.setPmParameterName(pmParameterType);
                    builder.setPmStartTime(time);
                    map.put(key, builder.build());
                });
                performancesBuilder.setPerformance(map);
                outputBuilder.setPerformances(performancesBuilder.build());
                RpcResult<GetHistoryPerformanceMonitoringDataOutput> rpcResult = RpcResultBuilder.success(outputBuilder.build()).build();
                result.set(rpcResult);
                return rpcResult;
            } catch (Exception e) {
                LOG.error("Failed to get history performance monitoring data", e);
            }
            return null;
        });
        return result;
    }

    private DateAndTime getOffTime(DateAndTime time, Granularity pmGranularity) {
        OffsetDateTime dateTime = OffsetDateTime.parse(time.getValue());
        OffsetDateTime target;
        target = dateTime.minusMinutes(Granularity._15min.equals(pmGranularity) ? dateTime.getMinute() % 15 : dateTime.getMinute()).withSecond(0).withNano(0);
        if (Granularity._24h.equals(pmGranularity)) {
            target = target.minusHours(target.getHour());
        }
        return new DateAndTime(target.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")));
    }

    private String getTimeStr() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
        return now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"));
    }

    private DateAndTime getTime() {
        return new DateAndTime(getTimeStr());
    }

    @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_BAD_PRACTICE")
    public ListenableFuture<RpcResult<GetHistoryAlarmsOutput>> getHistoryAlarms(GetHistoryAlarmsInput input) {
        SettableFuture<RpcResult<GetHistoryAlarmsOutput>> result = SettableFuture.create();
        executor.submit(() -> {
            try (InputStream is = FileUtil.getFile("get-history-alarms")) {
                SAXParserFactory spf = SAXParserFactory.newInstance();
                spf.setNamespaceAware(false);
                XMLReader xmlReader = spf.newSAXParser().getXMLReader();
                SAXSource source = new SAXSource(xmlReader, new InputSource(is));
                GetHistoryAlarmsDto reply = (GetHistoryAlarmsDto) JAXBContext.newInstance(GetHistoryAlarmsDto.class).createUnmarshaller().unmarshal(source);
                var outputBuilder = new GetHistoryAlarmsOutputBuilder();
                AlarmsBuilder alarmsBuilder = new AlarmsBuilder();
                Map<AlarmKey, Alarm> alarmMap = new HashMap<>();
                reply.getAlarms().getAlarms().forEach(item -> {
                    AlarmKey key = new AlarmKey(Uint64.valueOf(item.getAlarmSerialNo()));
                    AlarmBuilder builder = new AlarmBuilder();
                    builder.setAdditionalText(item.getAdditionalText());
                    builder.setAlarmCode(item.getAlarmCode());
                    builder.setAlarmSerialNo(Uint64.valueOf(item.getAlarmSerialNo()));
                    if (item.getAlarmState() != null) {
                        builder.setAlarmState(AlarmState.forName(item.getAlarmState().substring(item.getAlarmState().indexOf(":") + 1)));
                    }
                    builder.setEndTime(Optional.ofNullable(item.getEndTime()).map(DateAndTime::new).orElse(null));
                    builder.setObjectName(item.getObjectName());
                    builder.setObjectType(ObjectType.forName(item.getObjectType()));
                    builder.setPerceivedSeverity(perceivedSeverityToDev(item.getPerceivedSeverity()));
                    builder.setStartTime(Optional.ofNullable(item.getStartTime()).map(DateAndTime::new).orElse(null));
                    alarmMap.put(key, builder.build());
                });
                alarmsBuilder.setAlarm(alarmMap);
                Map<TcaKey, org.opendaylight.yang.gen.v1.urn.cmcc.yang.tcas.rev230426.tcas.top.Tca> tcaMap = new HashMap<>();
                TcasBuilder tcasBuilder = new TcasBuilder();
                reply.getTcas().getTcas().forEach(item -> {
                    TcaKey key = new TcaKey(String.valueOf(item.getTcaSerialNo()));
                    var builder = new TcaBuilder();
                    builder.setCurrentValue(Optional.ofNullable(item.getCurrentValue()).map(Decimal64::valueOf).map(Real::new).orElse(null));
                    builder.setEndTime(Optional.ofNullable(item.getEndTime()).map(DateAndTime::new).orElse(null));
                    builder.setGranularity(Granularity.forName(item.getGranularity()));
                    builder.setObjectName(item.getObjectName());
                    builder.setObjectType(ObjectType.forName(item.getObjectType()));
                    builder.setPmParameterName(item.getPmParameterName());
                    builder.setStartTime(Optional.ofNullable(item.getStartTime()).map(DateAndTime::new).orElse(null));
                    builder.setTcaSerialNo(String.valueOf(item.getTcaSerialNo()));
                    builder.setTcaState(AlarmState.forName(item.getTcaState()));
                    builder.setThresholdType(ThresholdType.forName(item.getThresholdType()));
                    builder.setThresholdValue(Optional.ofNullable(item.getThresholdValue()).map(Decimal64::valueOf).map(Real::new).orElse(null));
                    tcaMap.put(key, builder.build());
                });
                tcasBuilder.setTca(tcaMap);
                outputBuilder.setAlarms(alarmsBuilder.build());
                outputBuilder.setTcas(tcasBuilder.build());
                RpcResult<GetHistoryAlarmsOutput> rpcResult = RpcResultBuilder.success(outputBuilder.build()).build();
                result.set(rpcResult);
                return rpcResult;
            } catch (Exception e) {
                LOG.error("Failed to get history alarms", e);
            }
            return null;
        });
        return result;
    }

    private OPENCONFIGALARMSEVERITY perceivedSeverityToDev(String source) {
        if ("CRITICAL".equals(source)) {
            return CRITICAL.VALUE;
        } else if ("MAJOR".equals(source)) {
            return MAJOR.VALUE;
        } else if ("MINOR".equals(source)) {
            return MINOR.VALUE;
        } else if ("WARNING".equals(source)) {
            return WARNING.VALUE;
        }
        return UNKNOWN.VALUE;

    }

    @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_BAD_PRACTICE")
    public ListenableFuture<RpcResult<StartNotificationOutput>> startNotification(StartNotificationInput input) {
        if (notificationPublishService != null) {
            int period = Integer.parseInt(input.getPeriod());
            Preconditions.checkArgument(period >= 0 && period <= 3600, "周期范围为1-3600");
            if (period == 0) {
                sendNotification(input, false);
            } else {
                if (future != null) {
                    future.cancel(true);
                }
                if (MapUtil.isNotEmpty(input.getAlarmParameter())) {
                    AlarmNotificationBuilder alarmNotificationBuilder = new AlarmNotificationBuilder();
                    var alarmBuilder = new org.opendaylight.yang.gen.v1.urn.cmcc.yang.alarms.rev230426.alarm.notification.AlarmBuilder();
                    alarmBuilder.setAdditionalText("SIMULATOR ALARM");
                    alarmBuilder.setAlarmSerialNo(Uint64.valueOf(244));
                    alarmBuilder.setObjectType(ObjectType.PORT);
                    alarmBuilder.setPerceivedSeverity(CRITICAL.VALUE);
                    AtomicLong second = new AtomicLong(Instant.now().getEpochSecond());
                    AtomicLong threshold = new AtomicLong(0);
                    EventNotification eventNotification = new EventNotificationBuilder().build();
                    future = scheduler.scheduleAtFixedRate(() -> {
                        long instant = second.get();
                        ZonedDateTime now = ZonedDateTime.ofInstant(Instant.ofEpochSecond(instant), ZoneId.of("Asia/Shanghai"));
                        instant += period;
                        second.set(instant);
                        String format = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX"));
                        DateAndTime time = new DateAndTime(format);
                        if (count.getAndIncrement() % 2 == 0) {
                            alarmBuilder.setAlarmState(AlarmState.Start);
                            alarmBuilder.setStartTime(time);
                        } else {
                            alarmBuilder.setAlarmState(AlarmState.End);
                            alarmBuilder.setEndTime(time);
                        }
                        long times = threshold.getAndIncrement();
                        boolean flag = times >= 10;
                        if (CharSequenceUtil.isNotBlank(input.getTimes()) &&times >= (10 + Long.parseLong(input.getTimes())) ) {
                            return;
                        }
                        for (AlarmParameterKey key : input.getAlarmParameter().keySet()) {
                            CompletableFuture.runAsync(() -> {
                                alarmBuilder.setObjectName(key.getObjectName());
                                alarmBuilder.setAlarmCode(key.getAlarmCode());
                                alarmNotificationBuilder.setAlarm(alarmBuilder.build());
                                if(flag) {
                                    notificationPublishService.publish(alarmNotificationBuilder.build(), AlarmNotification.QNAME);
                                } else {
                                    notificationPublishService.publish(eventNotification, EventNotification.QNAME);
                                }
                            });
                        }
                    }, 0, period, TimeUnit.SECONDS);
                } else if (CollUtil.isNotEmpty(input.getAlarmCode())) {
                    try (InputStream is = FileUtil.getFile("openconfig-platform.xml")) {
                        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                        factory.setNamespaceAware(true);
                        DocumentBuilder builder = factory.newDocumentBuilder();
                        Document document = builder.parse(is);
                        NodeList nodeList = document.getElementsByTagName("name");
                        Set<String> name = new HashSet<>();
                        for (int i = 0; i < nodeList.getLength(); i++) {
                            Node node = nodeList.item(i);
                            String nameValue = node.getTextContent();
                            name.add(nameValue);
                            if (name.size() == 100) {
                                break;
                            }
                        }
                        Queue<AlarmNotificationKey> keyQueue = new LinkedList<>();
                        name.forEach(item -> input.getAlarmCode().forEach(ele -> keyQueue.add(new AlarmNotificationKey(item, ele))));
                        AlarmNotificationBuilder alarmNotificationBuilder = new AlarmNotificationBuilder();
                        var alarmBuilder = new org.opendaylight.yang.gen.v1.urn.cmcc.yang.alarms.rev230426.alarm.notification.AlarmBuilder();
                        alarmBuilder.setAdditionalText("SIMULATOR ALARM");
                        alarmBuilder.setAlarmSerialNo(Uint64.valueOf(244));
                        alarmBuilder.setObjectType(ObjectType.PORT);
                        alarmBuilder.setPerceivedSeverity(CRITICAL.VALUE);
                        alarmBuilder.setAlarmState(AlarmState.Start);
                        alarmBuilder.setStartTime(getTime());
                        int count = Integer.parseInt(input.getCount());
                        future = scheduler.scheduleAtFixedRate(() -> {
                            for (int i = 0; i < count; i++) {
                                AlarmNotificationKey key = keyQueue.poll();
                                if (key != null) {
                                    alarmBuilder.setObjectName(key.getObjectName());
                                    alarmBuilder.setAlarmCode(key.getAlarmCode());
                                    alarmNotificationBuilder.setAlarm(alarmBuilder.build());
                                    notificationPublishService.publish(alarmNotificationBuilder.build(), AlarmNotification.QNAME);
                                }
                            }
                        }, 0, period, TimeUnit.SECONDS);
                    } catch (IOException | ParserConfigurationException | SAXException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        SettableFuture<RpcResult<StartNotificationOutput>> result = SettableFuture.create();
        executor.submit(() -> {
            StartNotificationOutput output = new StartNotificationOutputBuilder().build();
            RpcResult<StartNotificationOutput> rpcResult = RpcResultBuilder.success(output).build();
            result.set(rpcResult);
            return rpcResult;
        });
        return result;
    }

    private void sendNotification(StartNotificationInput input, boolean variable) {
        AlarmNotificationBuilder builder = new AlarmNotificationBuilder();
        var alarmBuilder = new org.opendaylight.yang.gen.v1.urn.cmcc.yang.alarms.rev230426.alarm.notification.AlarmBuilder();
        alarmBuilder.setAdditionalText("SIMULATOR ALARM");
        alarmBuilder.setAlarmSerialNo(Uint64.valueOf(244));
        alarmBuilder.setObjectType(ObjectType.PORT);
        alarmBuilder.setPerceivedSeverity(CRITICAL.VALUE);
        if (variable) {
            if (count.getAndIncrement() % 2 == 0) {
                alarmBuilder.setAlarmState(AlarmState.Start);
                alarmBuilder.setStartTime(getTime());
            } else {
                alarmBuilder.setAlarmState(AlarmState.End);
                alarmBuilder.setEndTime(getTime());
            }
        } else {
            alarmBuilder.setAlarmState(AlarmState.Start);
            alarmBuilder.setStartTime(getTime());
        }
        Preconditions.checkArgument(CollUtil.isNotEmpty(input.getAlarmParameter()), "alarm-parameter不能为空");
        input.getAlarmParameter().keySet().forEach(item -> {
            alarmBuilder.setObjectName(item.getObjectName());
            alarmBuilder.setAlarmCode(item.getAlarmCode());
            builder.setAlarm(alarmBuilder.build());
            notificationPublishService.publish(builder.build(), AlarmNotification.QNAME);
        });
    }


    public void setNotificationPublishService(NotificationPublishService notificationPublishService) {
        this.notificationPublishService = notificationPublishService;
    }

    @Override
    public void close() {
        executor.shutdown();
        scheduler.shutdownNow();
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
            return rpcResult;
        });
        return result;
    }

    public ListenableFuture<RpcResult<StartOtdrOutput>> startOtdr(StartOtdrInput input) {
        SettableFuture<RpcResult<StartOtdrOutput>> result = SettableFuture.create();
        executor.submit(() -> {
            StartOtdrOutput output = new StartOtdrOutputBuilder().setIndex(Uint32.valueOf(1)).setStartOtdrResult(ActionResult.SUCCESS).setOtdrFileType(OtdrFileFormatType.SR4731).build();
            RpcResult<StartOtdrOutput> rpcResult = RpcResultBuilder.success(output).build();
            result.set(rpcResult);
            return rpcResult;
        });
        return result;
    }

    public ListenableFuture<RpcResult<GetOdukDelayOutput>> getOdukDelay(GetOdukDelayInput input) {
        SettableFuture<RpcResult<GetOdukDelayOutput>> result = SettableFuture.create();
        executor.submit(() -> {
            GetOdukDelayOutputBuilder getOdukDelayOutputBuilder = new GetOdukDelayOutputBuilder();
            DelayDataValueBuilder delayDataValueBuilder = new DelayDataValueBuilder();
            delayDataValueBuilder.setAvg(Decimal64.valueOf("30.01"));
            delayDataValueBuilder.setInstant(Decimal64.valueOf("30.02"));
            delayDataValueBuilder.setMax(Decimal64.valueOf("30.03"));
            delayDataValueBuilder.setMin(Decimal64.valueOf("30.04"));
            getOdukDelayOutputBuilder.setDelayDataValue(delayDataValueBuilder.build());
            getOdukDelayOutputBuilder.setGetDelayResult(org.opendaylight.yang.gen.v1.urn.cmcc.yang.rpc.rev230426.RpcResult.SUCCESS);
            GetOdukDelayOutput output = getOdukDelayOutputBuilder.build();
            RpcResult<GetOdukDelayOutput> rpcResult = RpcResultBuilder.success(output).build();
            result.set(rpcResult);
            return rpcResult;
        });
        return result;
    }

    public ListenableFuture<RpcResult<GenCurrentAlarmOutput>> genCurrentAlarm(GenCurrentAlarmInput input) {
        SettableFuture<RpcResult<GenCurrentAlarmOutput>> result = SettableFuture.create();
        executor.submit(() -> {
            GenCurrentAlarmOutputBuilder builder = new GenCurrentAlarmOutputBuilder();
            String host = input.getHost().trim();
            String port = input.getPort().trim();
            String user = input.getUsername().trim();
            String password = input.getPassword().trim();
            String num = input.getNum().trim();

            if (host.isEmpty() || port.isEmpty() || user.isEmpty() || password.isEmpty() || num.isEmpty()) {
                throw new RuntimeException("input is uncompleted");
            }

            String url = String.format("jdbc:mysql://%s:%s/uniview?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false", host, port);

            try (Connection conn = DriverManager.getConnection(url, user, password);
                 Statement stmt = conn.createStatement()
            ) {

                StringBuilder sb = new StringBuilder();

                // 获取alarmOid
                ResultSet fmcurRs = stmt.executeQuery("SELECT * FROM uniview.fmcur");
                int alarmOid = 0;
                while (fmcurRs.next()) {
                    alarmOid = fmcurRs.getInt(1);
                }
                fmcurRs.close();
                // 获取dmeo的oid
                ResultSet dmeoRs = stmt.executeQuery("SELECT * FROM uniview.dmeo");
                List<String> oidList = new ArrayList<>();
                Map<String, String> nameMap = new HashMap<>();
                while (dmeoRs.next()) {
                    String oid = dmeoRs.getString(1);
                    int cid = dmeoRs.getInt(2);
                    int type = dmeoRs.getInt(3);
                    String name = dmeoRs.getString(4);
                    if (cid != 1 && (type < 40101 || type > 40126 ) && type != 40201 && oid != null && !oid.isEmpty()) {
                        oidList.add(oid);
                        nameMap.put(oid, name);
                    }
                }
                dmeoRs.close();
                Map<String, String> fullNameMap = getFullName(nameMap);
                // 获取acode
                ResultSet defRs = stmt.executeQuery("SELECT * FROM uniview.deffmattr");
                List<Integer> codeList = new ArrayList<>();
                while (defRs.next()) {
                    int code = defRs.getInt(2);
                    String objectType = defRs.getString(18);
                    if ("6900".equals(objectType)) {
                        codeList.add(code);
                    }
                }
                defRs.close();
                int count = 1;
                int alarmNum = Integer.parseInt(num) + 1;
                long now = Instant.now().getEpochSecond();
                for (String oid : oidList) {
                    for (Integer code : codeList) {
                        if (count == alarmNum) {
                            break;
                        }
                        alarmOid++;
                        if (count % 1000 == 1) {
                            sb.append("insert ignore into uniview.fmcur(alarmOid, eo, srcName, type, acode, grade, beginTime, acked, locked, eventType, emsBeginTime, frequency) values\n");
                        }
                        sb.append(String.format("(%d, \"%s\", \"%s\", 13, %d, 0, %d, 0, 0, 3, %d, %d),\n", alarmOid, oid, fullNameMap.get(oid), code, now, now, 1));
                        if (count % 1000 == 0) {
                            sb.setLength(sb.length() - 2);
                            sb.append(";\n\n");
                        }
                        count++;
                    }
                }
                builder.setResult(sb.toString());
            } catch (SQLException ex) {
                result.setException(new RuntimeException("gen sql error", ex));
            }
            RpcResult<GenCurrentAlarmOutput> rpcResult = RpcResultBuilder.success(builder.build()).build();
            result.set(rpcResult);
            return rpcResult;
        });
        return result;
    }

    public ListenableFuture<RpcResult<GenHistoryAlarmOutput>> genHistoryAlarm(GenHistoryAlarmInput input) {
        SettableFuture<RpcResult<GenHistoryAlarmOutput>> result = SettableFuture.create();
        executor.submit(() -> {
            GenHistoryAlarmOutputBuilder builder = new GenHistoryAlarmOutputBuilder();
            String host = input.getHost().trim();
            String port = input.getPort().trim();
            String user = input.getUsername().trim();
            String password = input.getPassword().trim();
            String num = input.getNum().trim();

            if (host.isEmpty() || port.isEmpty() || user.isEmpty() || password.isEmpty() || num.isEmpty()) {
                throw new RuntimeException("input is uncompleted");
            }

            String url = String.format("jdbc:mysql://%s:%s/uniview?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false", host, port);

            try (Connection conn = DriverManager.getConnection(url, user, password);
                 Statement stmt = conn.createStatement()
            ) {

                StringBuilder sb = new StringBuilder();

                // 获取alarmOid
                ResultSet fmcurRs = stmt.executeQuery("SELECT * FROM univiewbackup.fmhis");
                int alarmOid = 0;
                while (fmcurRs.next()) {
                    alarmOid = fmcurRs.getInt(1);
                }
                fmcurRs.close();
                // 获取dmeo的oid
                ResultSet dmeoRs = stmt.executeQuery("SELECT * FROM uniview.dmeo");
                List<String> oidList = new ArrayList<>();
                Map<String, String> nameMap = new HashMap<>();
                while (dmeoRs.next()) {
                    String oid = dmeoRs.getString(1);
                    int cid = dmeoRs.getInt(2);
                    int type = dmeoRs.getInt(3);
                    String name = dmeoRs.getString(4);
                    if (cid != 1 && (type < 40101 || type > 40126 ) && type != 40201 && oid != null && !oid.isEmpty()) {
                        oidList.add(oid);
                        nameMap.put(oid, name);
                    }
                }
                dmeoRs.close();
                Map<String, String> fullNameMap = getFullName(nameMap);
                // 获取acode
                ResultSet defRs = stmt.executeQuery("SELECT * FROM uniview.deffmattr");
                List<Integer> codeList = new ArrayList<>();
                while (defRs.next()) {
                    int code = defRs.getInt(2);
                    String objectType = defRs.getString(18);
                    if ("6900".equals(objectType)) {
                        codeList.add(code);
                    }
                }
                defRs.close();
                int count = 1;
                int alarmNum = Integer.parseInt(num) + 1;
                long now = Instant.now().getEpochSecond();
                for (String oid : oidList) {
                    for (Integer code : codeList) {
                        if (count == alarmNum) {
                            break;
                        }
                        alarmOid++;
                        if (count % 1000 == 1) {
                            sb.append("INSERT INTO `univiewbackup`.`fmhis` (`alarmOid`, `eo`, `srcName`, `type`, `acode`, `grade`, `beginTime`, `endTime`, `addInfo`, `acked`, `emsBeginTime`, `eventType`) VALUES\n");
                        }
                        sb.append(String.format("(%d, \"%s\", \"%s\", 13, %d, 0, %d, %d, \"EQPT_MISMATCH\", 0, %d, 13),\n", alarmOid, oid, fullNameMap.get(oid), code, now, now + 3600, now, 1));
                        if (count % 1000 == 0) {
                            sb.setLength(sb.length() - 2);
                            sb.append(";\n\n");
                        }
                        count++;
                    }
                }
                builder.setResult(sb.toString());
            } catch (SQLException ex) {
                result.setException(new RuntimeException("gen sql error", ex));
            }
            RpcResult<GenHistoryAlarmOutput> rpcResult = RpcResultBuilder.success(builder.build()).build();
            result.set(rpcResult);
            return rpcResult;
        });
        return result;
    }

    private Map<String, String> getFullName(Map<String, String> nameMap) {
        if (nameMap == null || nameMap.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> fullNameMap = new HashMap<>();
        for (String oid : nameMap.keySet()) {
            injectFullName(oid, nameMap, fullNameMap);
        }
        return fullNameMap;
    }

    private void injectFullName(String oid, Map<String, String> nameMap, Map<String, String> fullNameMap) {
        if (oid == null || oid.isEmpty()) {
            return;
        }
        String[] arr = oid.split(":");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            StringBuilder cutSb = new StringBuilder();
            for (int j = 0; j <= i; j++) {
                cutSb.append(arr[j]).append(":");
            }
            cutSb.setLength(cutSb.length() - 1);
            sb.append(nameMap.get(cutSb.toString())).append(":");
        }
        sb.setLength(sb.length() - 1);
        fullNameMap.put(oid, sb.toString());
    }

    public ListenableFuture<RpcResult<StartTcaNotificationOutput>> startTcaNotification(StartTcaNotificationInput input) {
        SettableFuture<RpcResult<StartTcaNotificationOutput>> result = SettableFuture.create();
        executor.submit(() -> {
            TcaNotificationBuilder tcaNotificationBuilder = new TcaNotificationBuilder();
            var tcaBuilder = new org.opendaylight.yang.gen.v1.urn.cmcc.yang.tcas.rev230426.tca.notification.TcaBuilder();
            tcaBuilder.setTcaSerialNo(input.getTcaSerialNo());
            tcaBuilder.setTcaState(AlarmState.forName(input.getTcaState()));
            tcaBuilder.setStartTime(input.getStartTime());
            tcaBuilder.setEndTime(input.getEndTime());
            tcaBuilder.setObjectType(input.getObjectType());
            tcaBuilder.setObjectName(input.getObjectName());
            tcaBuilder.setPmParameterName(input.getPmParameterName());
            tcaBuilder.setGranularity(input.getGranularity());
            tcaBuilder.setThresholdType(input.getThresholdType());
            tcaBuilder.setThresholdValue(input.getThresholdValue());
            tcaBuilder.setCurrentValue(input.getCurrentValue());
            tcaNotificationBuilder.setTca(tcaBuilder.build());

            notificationPublishService.publish(tcaNotificationBuilder.build(), TcaNotification.QNAME);
            StartTcaNotificationOutputBuilder builder = new StartTcaNotificationOutputBuilder();
            RpcResult<StartTcaNotificationOutput> rpcResult = RpcResultBuilder.success(builder.build()).build();
            result.set(rpcResult);
            return rpcResult;
        });
        return result;
    }
}
