package com.example.timbya.model;

import java.util.Collections;
import java.util.List;

public class GeminiRequest {

    public List<Content> contents;

    public GeminiRequest(String text) {
        this.contents = Collections.singletonList(new Content(text));
    }

    static class Content {
        public List<Part> parts;

        Content(String text) {
            parts = Collections.singletonList(new Part(text));
        }
    }

    static class Part {
        public String text;

        Part(String text) {
            this.text = text;
        }
    }
}