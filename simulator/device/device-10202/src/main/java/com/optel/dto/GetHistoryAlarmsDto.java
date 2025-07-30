package com.optel.dto;

import javax.xml.bind.annotation.*;
import java.util.List;

@XmlRootElement(name = "rpc-reply")
public class GetHistoryAlarmsDto {
    @XmlElement(name = "alarms")
    private Alarms alarms;

    @XmlRootElement(name = "alarms")
    public static class Alarms {

        @XmlElement(name = "alarm")
        private List<Alarm> alarms;

        @XmlRootElement(name = "alarm")
        public static class Alarm {
            @XmlElement(name = "alarm-serial-no")
            private int alarmSerialNo;

            public int getAlarmSerialNo() {
                return alarmSerialNo;
            }

            @XmlElement(name = "alarm-code")
            private String alarmCode;

            public String getAlarmCode() {
                return alarmCode;
            }

            @XmlElement(name = "object-name")
            private String objectName;

            public String getObjectName() {
                return objectName;
            }

            @XmlElement(name = "object-type")
            private String objectType;

            public String getObjectType() {
                return objectType;
            }

            @XmlElement(name = "additional-text")
            private String additionalText;

            public String getAdditionalText() {
                return additionalText;
            }

            @XmlElement(name = "start-time")
            private String startTime;

            public String getStartTime() {
                return startTime;
            }

            @XmlElement(name = "end-time")
            private String endTime;

            public String getEndTime() {
                return endTime;
            }

            @XmlElement(name = "perceived-severity")
            private String perceivedSeverity;

            public String getPerceivedSeverity() {
                return perceivedSeverity;
            }

            @XmlElement(name = "alarm-state")
            private String alarmState;

            public String getAlarmState() {
                return alarmState;
            }

        }

        public List<Alarm> getAlarms() {
            return alarms;
        }

    }

    public Alarms getAlarms() {
        return alarms;
    }

    @XmlElement(name = "tcas")
    private Tcas tcas;

    @XmlRootElement(name = "tcas")
    public static class Tcas {

        @XmlElement(name = "tca")
        private List<Tca> tcas;

        @XmlRootElement(name = "tca")
        public static class Tca {
            @XmlElement(name = "tca-serial-no")
            private int tcaSerialNo;

            public int getTcaSerialNo() {
                return tcaSerialNo;
            }

            @XmlElement(name = "tca-state")
            private String tcaState;

            public String getTcaState() {
                return tcaState;
            }

            @XmlElement(name = "start-time")
            private String startTime;

            public String getStartTime() {
                return startTime;
            }

            @XmlElement(name = "end-time")
            private String endTime;

            public String getEndTime() {
                return endTime;
            }

            @XmlElement(name = "object-type")
            private String objectType;

            public String getObjectType() {
                return objectType;
            }

            @XmlElement(name = "object-name")
            private String objectName;

            public String getObjectName() {
                return objectName;
            }

            @XmlElement(name = "pm-parameter-name")
            private String pmParameterName;

            public String getPmParameterName() {
                return pmParameterName;
            }

            @XmlElement(name = "granularity")
            private String granularity;

            public String getGranularity() {
                return granularity;
            }

            @XmlElement(name = "threshold-type")
            private String thresholdType;

            public String getThresholdType() {
                return thresholdType;
            }

            @XmlElement(name = "threshold-value")
            private String thresholdValue;

            public String getThresholdValue() {
                return thresholdValue;
            }

            @XmlElement(name = "current-value")
            private String currentValue;

            public String getCurrentValue() {
                return currentValue;
            }

        }

        public List<Tca> getTcas() {
            return tcas;
        }

    }

    public Tcas getTcas() {
        return tcas;
    }

}






