/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All Rights Reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v10.html
 */
package com.optel.rpcs;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.optel.dto.GetCurrentPerformanceMonitoringDataDto;
import com.optel.dto.GetHistoryAlarmsDto;
import com.optel.dto.GetHistoryPerformanceMonitoringDataDto;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.lighty.netconf.device.requests.notification.NotificationPublishService;
import io.lighty.netconf.device.utils.FileUtil;
import io.lighty.netconf.device.utils.YangUtil;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.alarms.types.rev181121.*;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.notification.rev230426.EventNotification;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.notification.rev230426.EventNotificationBuilder;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.notification.rev230426.EventType;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.notification.rev230426.event.notification.EventBuilder;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.openconfig.types.rev230426.*;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.alarms.rev230426.AlarmNotification;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.alarms.rev230426.AlarmNotificationBuilder;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.alarms.rev230426.alarms.top.Alarm;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.alarms.rev230426.alarms.top.AlarmBuilder;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.alarms.rev230426.alarms.top.AlarmKey;
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
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.tcas.rev230426.tcas.top.Tca;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.tcas.rev230426.tcas.top.TcaBuilder;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.tcas.rev230426.tcas.top.TcaKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev200909.DateAndTime;
import org.opendaylight.yangtools.yang.common.Decimal64;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.bind.JAXBContext;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
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

public class ToasterServiceImpl implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ToasterServiceImpl.class);

    private final ExecutorService executor;
    private NotificationPublishService notificationPublishService;

    public ToasterServiceImpl() {
        executor = Executors.newFixedThreadPool(1);
    }

    @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_BAD_PRACTICE")
    public ListenableFuture<RpcResult<GetCurrentPerformanceMonitoringDataOutput>> getCurrentPerformanceMonitoringData(GetCurrentPerformanceMonitoringDataInput input) {
        SettableFuture<RpcResult<GetCurrentPerformanceMonitoringDataOutput>> result = SettableFuture.create();
        DateAndTime time = getTime();
        executor.submit(() -> {
            try (InputStream is = FileUtil.isPackage() ? Files.newInputStream(Paths.get(System.getProperty("user.dir"), "rpc", "get-current-performance-monitoring-data.xml")) : YangUtil.class.getClassLoader().getResourceAsStream("rpc/get-current-performance-monitoring-data.xml")) {
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
                    builder.setPmGranularity(granularity);
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
                e.printStackTrace();
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
            try (InputStream is = FileUtil.isPackage() ? Files.newInputStream(Paths.get(System.getProperty("user.dir"), "rpc", "get-history-performance-monitoring-data.xml")) : YangUtil.class.getClassLoader().getResourceAsStream("rpc/get-history-performance-monitoring-data.xml")) {
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
                    builder.setPmGranularity(granularity);
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
                e.printStackTrace();
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
            try (InputStream is = FileUtil.isPackage() ? Files.newInputStream(Paths.get(System.getProperty("user.dir"), "rpc", "get-history-alarms.xml")) : YangUtil.class.getClassLoader().getResourceAsStream("rpc/get-history-alarms.xml")) {
                SAXParserFactory spf = SAXParserFactory.newInstance();
                spf.setNamespaceAware(false);
                XMLReader xmlReader = spf.newSAXParser().getXMLReader();
                SAXSource source = new SAXSource(xmlReader, new InputSource(is));
                GetHistoryAlarmsDto reply = (GetHistoryAlarmsDto) JAXBContext.newInstance(GetCurrentPerformanceMonitoringDataDto.class).createUnmarshaller().unmarshal(source);
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
                Map<TcaKey, Tca> tcaMap = new HashMap<>();
                TcasBuilder tcasBuilder = new TcasBuilder();
                reply.getTcas().getTcas().forEach(item -> {
                    TcaKey key = new TcaKey(String.valueOf(item.getTcaSerialNo()));
                    TcaBuilder builder = new TcaBuilder();
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
                e.printStackTrace();
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
            if ("alarm-notification".equals(input.getName())) {
                AlarmNotificationBuilder builder = new AlarmNotificationBuilder();
                var alarmBuilder = new org.opendaylight.yang.gen.v1.urn.cmcc.yang.alarms.rev230426.alarm.notification.AlarmBuilder();
                alarmBuilder.setAdditionalText("EQPT_TRANSCEIVER_REMOVE");
                alarmBuilder.setAlarmCode("EQPT_TRANSCEIVER_REMOVE");
                alarmBuilder.setAlarmSerialNo(Uint64.valueOf(244));
                alarmBuilder.setAlarmState(AlarmState.Start);
//                alarmBuilder.setEndTime(getTime());
                alarmBuilder.setObjectName("PORT-1-3-L1");
                alarmBuilder.setObjectType(ObjectType.PORT);
                alarmBuilder.setPerceivedSeverity(CRITICAL.VALUE);
                alarmBuilder.setStartTime(getTime());
                builder.setAlarm(alarmBuilder.build());
                notificationPublishService.publish(builder.build(), AlarmNotification.QNAME);
            } else if ("event-notification".equals(input.getName())) {
                EventNotificationBuilder builder = new EventNotificationBuilder();
                EventBuilder eventBuilder = new EventBuilder();
                eventBuilder.setEventAbbr("force-to-port");
                eventBuilder.setEventSerialNo(Uint64.valueOf(1));
                eventBuilder.setEventType(EventType.AttributeValueChange);
                eventBuilder.setObjectName("APS-1-6-2");
                eventBuilder.setObjectType(ObjectType.APS);
                eventBuilder.setTimeCreated(new Timeticks64(Uint64.valueOf(Instant.now().getEpochSecond() * 1000 * 1000 * 1000)));
                builder.setEvent(eventBuilder.build());
                notificationPublishService.publish(builder.build(), EventNotification.QNAME);
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


    public void setNotificationPublishService(NotificationPublishService notificationPublishService) {
        this.notificationPublishService = notificationPublishService;
    }

    @Override
    public void close() {
        executor.shutdown();
    }

}
