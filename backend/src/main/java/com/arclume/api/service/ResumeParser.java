package com.arclume.api.service;

import java.io.InputStream;

public interface ResumeParser {
    String extractText(InputStream inputStream, String contentType) throws Exception;
}
