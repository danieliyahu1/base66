package com.akatsuki.base66.opencode;

public class OpenCodePart {

    private String id;
    private String type;
    private String text;
    private String callID;
    private String tool;

    public OpenCodePart() {
    }

    public OpenCodePart(String id, String type, String text, String callID, String tool) {
        this.id = id;
        this.type = type;
        this.text = text;
        this.callID = callID;
        this.tool = tool;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getCallID() {
        return callID;
    }

    public void setCallID(String callID) {
        this.callID = callID;
    }

    public String getTool() {
        return tool;
    }

    public void setTool(String tool) {
        this.tool = tool;
    }
}