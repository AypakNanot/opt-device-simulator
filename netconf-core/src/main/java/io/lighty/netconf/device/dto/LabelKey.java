package io.lighty.netconf.device.dto;

import java.util.Objects;

/**
 * @author dzm
 * @since 2025/2/11
 */
public class LabelKey {
    private String localName;
    private String namespace;

    public String getLocalName() {
        return localName;
    }

    public void setLocalName(String localName) {
        this.localName = localName;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        LabelKey labelKey = (LabelKey) o;
        return Objects.equals(localName, labelKey.localName) && Objects.equals(namespace, labelKey.namespace);
    }

    @Override
    public int hashCode() {
        return Objects.hash(localName, namespace);
    }

    public LabelKey(String localName, String namespace) {
        this.namespace = namespace;
        this.localName = localName;
    }

}

