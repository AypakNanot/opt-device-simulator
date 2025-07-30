package com.optel.dto;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

@XmlRootElement(name = "rpc-reply")
public class GetHistoryPerformanceMonitoringDataDto {
    @XmlElement(name = "performances")
    private Performances performances;

    public Performances getPerformances() {
        return performances;
    }

    @XmlRootElement(name = "performances")
    public static class Performances {
        @XmlElement(name = "performance")
        private List<Performance> performances;

        public List<Performance> getPerformances() {
            return performances;
        }

        @XmlRootElement(name = "performance")
        public static class Performance {
            @XmlElement(name = "bin-no")
            private int binNo;
            @XmlElement(name = "object-name")
            private String objectName;
            @XmlElement(name = "object-type")
            private String objectType;
            @XmlElement(name = "pm-parameter-name")
            private String pmParameterName;
            @XmlElement(name = "pm-granularity")
            private String pmGranularity;
            @XmlElement(name = "pm-start-time")
            private String pmStartTime;
            @XmlElement(name = "analog-pm-value")
            private AnalogPmValue analogPmValue;

            @XmlElement(name = "analog-pm-value")
            private String digitalPmValue;

            public int getBinNo() {
                return binNo;
            }

            public String getObjectName() {
                return objectName;
            }

            public String getObjectType() {
                return objectType;
            }

            public String getPmParameterName() {
                return pmParameterName;
            }

            public String getPmGranularity() {
                return pmGranularity;
            }

            public String getPmStartTime() {
                return pmStartTime;
            }

            public AnalogPmValue getAnalogPmValue() {
                return analogPmValue;
            }

            public String getDigitalPmValue() {
                return digitalPmValue;
            }

            @XmlRootElement(name = "analog-pm-value")
            public static class AnalogPmValue {
                @XmlElement(name = "max-value")
                private String maxValue;
                @XmlElement(name = "min-value")
                private String minValue;
                @XmlElement(name = "average-value")
                private String averageValue;
                @XmlElement(name = "current-value")
                private String currentValue;

                public String getMaxValue() {
                    return maxValue;
                }

                public String getMinValue() {
                    return minValue;
                }

                public String getAverageValue() {
                    return averageValue;
                }

                public String getCurrentValue() {
                    return currentValue;
                }

            }

        }

    }

}