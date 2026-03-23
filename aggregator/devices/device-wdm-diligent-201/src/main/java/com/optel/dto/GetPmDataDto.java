package com.optel.dto;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.List;

/**
 * @author dzm
 * @since 2025/12/15
 */
@XmlRootElement(name = "rpc-reply")
public class GetPmDataDto {
    @XmlElement(name = "get-pm-result")
    private String getPmResult;

    public String getGetPmResult() {
        return getPmResult;
    }

    @XmlElement(name = "performance")
    private List<Performance> performances;

    @XmlRootElement(name = "performance")
    public static class Performance {
        @XmlElement(name = "pm-point")
        private String pmPoint;

        public String getPmPoint() {
            return pmPoint;
        }

        @XmlElement(name = "pm-parameter")
        private String pmParameter;

        public String getPmParameter() {
            return pmParameter;
        }

        @XmlElement(name = "monitoring-date-time")
        private String monitoringDateTime;

        public String getMonitoringDateTime() {
            return monitoringDateTime;
        }

        @XmlElement(name = "digital-pm-value")
        private String digitalPmValue;

        public String getDigitalPmValue() {
            return digitalPmValue;
        }

        @XmlElement(name = "max-value")
        private String maxValue;

        public String getMaxValue() {
            return maxValue;
        }

        @XmlElement(name = "min-value")
        private String minValue;

        public String getMinValue() {
            return minValue;
        }

        @XmlElement(name = "average-value")
        private String averageValue;

        public String getaAverageValue() {
            return averageValue;
        }

        @XmlElement(name = "current-value")
        private String currentValue;

        public String getCurrentValue() {
            return currentValue;
        }

    }

    public List<Performance> getPerformances() {
        return performances;
    }

}


