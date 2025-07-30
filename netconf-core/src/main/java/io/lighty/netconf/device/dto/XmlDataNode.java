package io.lighty.netconf.device.dto;

import java.util.HashMap;
import java.util.Map;

/**
 * @author dzm
 * @since 2025/2/10
 */
public class XmlDataNode {
    private String name;
    private String value;
    private String namespace;
    private Map<String, XmlDataNode> children = new HashMap<>();

    public XmlDataNode(String name) {
        this.name = name;
    }
    public XmlDataNode(String name, String value) {
        this.name = name;
        this.value = value;
    }
    public XmlDataNode(String name, String value, Map<String, XmlDataNode> children) {
        this.name = name;
        this.value = value;
        this.children = children;
    }
    public XmlDataNode(String name, String value, String namespace) {
        this.name = name;
        this.value = value;
        this.namespace = namespace;
    }
    public XmlDataNode(String name, String value, String namespace, Map<String, XmlDataNode> children) {
        this.name = name;
        this.value = value;
        this.namespace = namespace;
        this.children = children;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void addChild(XmlDataNode child) {
        this.children.put(child.name, child);
    }

    public Map<String, XmlDataNode> getChildren() {
        return children;
    }

    public String getValue() {
        return value;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNamespace() {
        return namespace;
    }
}
