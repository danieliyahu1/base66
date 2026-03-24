package com.akatsuki.base66.opencode;

public class TextPartInput {

    private String type;
    private String text;

    public TextPartInput() {
    }

    public TextPartInput(String type, String text) {
        this.type = type;
        this.text = text;
    }

    public TextPartInput(String text) {
        this("text", text);
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
}