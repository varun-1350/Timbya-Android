package com.example.timbya.actions;

public class ActionResult {

    private boolean handled;
    private String reply;

    public ActionResult(boolean handled, String reply) {
        this.handled = handled;
        this.reply = reply;
    }

    public boolean isHandled() {
        return handled;
    }

    public String getReply() {
        return reply;
    }
}