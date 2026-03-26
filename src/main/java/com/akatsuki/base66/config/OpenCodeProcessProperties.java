package com.akatsuki.base66.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.opencode")
public class OpenCodeProcessProperties {

    private boolean autostart = true;
    private String command = "opencode";
    private String args = "serve --host 127.0.0.1 --port 4096";
    private String workingDirectory;
    private int startupTimeoutSeconds = 30;
    private boolean stopOnShutdown = true;

    public boolean isAutostart() {
        return autostart;
    }

    public void setAutostart(boolean autostart) {
        this.autostart = autostart;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String getArgs() {
        return args;
    }

    public void setArgs(String args) {
        this.args = args;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public void setWorkingDirectory(String workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    public int getStartupTimeoutSeconds() {
        return startupTimeoutSeconds;
    }

    public void setStartupTimeoutSeconds(int startupTimeoutSeconds) {
        this.startupTimeoutSeconds = startupTimeoutSeconds;
    }

    public boolean isStopOnShutdown() {
        return stopOnShutdown;
    }

    public void setStopOnShutdown(boolean stopOnShutdown) {
        this.stopOnShutdown = stopOnShutdown;
    }
}