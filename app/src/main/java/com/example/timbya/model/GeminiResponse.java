package com.example.timbya.model;

import java.util.List;

public class GeminiResponse {

    public List<Candidate> candidates;

    public static class Candidate {
        public Content content;
    }

    public static class Content {
        public List<Part> parts;
    }

    public static class Part {
        public String text;
    }
}