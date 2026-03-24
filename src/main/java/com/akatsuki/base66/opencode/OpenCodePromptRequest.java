package com.akatsuki.base66.opencode;

import java.util.List;

public class OpenCodePromptRequest {

    private List<TextPartInput> parts;

    public OpenCodePromptRequest() {
    }

    public OpenCodePromptRequest(List<TextPartInput> parts) {
        this.parts = parts;
    }

    public List<TextPartInput> getParts() {
        return parts;
    }

    public void setParts(List<TextPartInput> parts) {
        this.parts = parts;
    }
}