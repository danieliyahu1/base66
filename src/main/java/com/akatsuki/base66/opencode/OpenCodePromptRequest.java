package com.akatsuki.base66.opencode;

import java.util.List;

public class OpenCodePromptRequest {

    private List<TextPartInput> parts;
    private String agent;

    public OpenCodePromptRequest() {
    }

    public OpenCodePromptRequest(List<TextPartInput> parts) {
        this.parts = parts;
    }

    public OpenCodePromptRequest(List<TextPartInput> parts, String agent) {
        this.parts = parts;
        this.agent = agent;
    }

    public List<TextPartInput> getParts() {
        return parts;
    }

    public void setParts(List<TextPartInput> parts) {
        this.parts = parts;
    }

    public String getAgent() {
        return agent;
    }

    public void setAgent(String agent) {
        this.agent = agent;
    }
}