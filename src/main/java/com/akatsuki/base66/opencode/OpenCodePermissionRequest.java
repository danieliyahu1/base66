package com.akatsuki.base66.opencode;

import java.util.List;
import java.util.Map;

public class OpenCodePermissionRequest {

    private String id;
    private String sessionID;
    private String permission;
    private List<String> patterns;
    private List<String> always;
    private Map<String, Object> metadata;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSessionID() {
        return sessionID;
    }

    public void setSessionID(String sessionID) {
        this.sessionID = sessionID;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public List<String> getPatterns() {
        return patterns;
    }

    public void setPatterns(List<String> patterns) {
        this.patterns = patterns;
    }

    public List<String> getAlways() {
        return always;
    }

    public void setAlways(List<String> always) {
        this.always = always;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }
}
