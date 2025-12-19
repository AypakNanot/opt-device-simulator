package com.optel.dto;

import java.util.Objects;

/**
 * @author dzm
 * @since 2025/9/29
 */
public class AlarmNotificationKey {
    private String alarmCode;
    private String objectName;

    public AlarmNotificationKey() {
    }

    public AlarmNotificationKey(String alarmCode, String objectName) {
        this.alarmCode = alarmCode;
        this.objectName = objectName;
    }

    public String getAlarmCode() {
        return alarmCode;
    }

    public String getObjectName() {
        return objectName;
    }

    public void setAlarmCode(String alarmCode) {
        this.alarmCode = alarmCode;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AlarmNotificationKey alarmNotificationKey = (AlarmNotificationKey) o;
        return Objects.equals(alarmCode, alarmNotificationKey.alarmCode) && Objects.equals(objectName, alarmNotificationKey.objectName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(alarmCode, objectName);
    }
}
