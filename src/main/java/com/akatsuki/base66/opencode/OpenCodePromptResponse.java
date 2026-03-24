package com.akatsuki.base66.opencode;

import java.util.List;

public class OpenCodePromptResponse {

    private OpenCodeMessageInfo info;
    private List<OpenCodePart> parts;

    public OpenCodePromptResponse() {
    }

    public OpenCodePromptResponse(OpenCodeMessageInfo info, List<OpenCodePart> parts) {
        this.info = info;
        this.parts = parts;
    }

    public OpenCodeMessageInfo getInfo() {
        return info;
    }

    public void setInfo(OpenCodeMessageInfo info) {
        this.info = info;
    }

    public List<OpenCodePart> getParts() {
        return parts;
    }

    public void setParts(List<OpenCodePart> parts) {
        this.parts = parts;
    }
}