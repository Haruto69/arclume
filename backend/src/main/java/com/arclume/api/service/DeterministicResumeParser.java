package com.arclume.api.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class DeterministicResumeParser implements ResumeParser {

    @Override
    public String extractText(InputStream inputStream, String contentType) throws Exception {
        if (contentType == null) {
            throw new IllegalArgumentException("Content type cannot be null");
        }

        switch (contentType.toLowerCase()) {
            case "application/pdf":
                byte[] pdfBytes = inputStream.readAllBytes();
                try (PDDocument document = Loader.loadPDF(pdfBytes)) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    String text = stripper.getText(document);
                    if (text == null || text.trim().isEmpty()) {
                        throw new Exception("PDF document is empty or contains unreadable content");
                    }
                    return text;
                }

            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
                try (XWPFDocument document = new XWPFDocument(inputStream);
                     XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                    String text = extractor.getText();
                    if (text == null || text.trim().isEmpty()) {
                        throw new Exception("DOCX document is empty or contains unreadable content");
                    }
                    return text;
                }

            case "text/plain":
                String text = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                if (text.trim().isEmpty()) {
                    throw new Exception("TXT document is empty");
                }
                return text;

            default:
                throw new IllegalArgumentException("Unsupported media type: " + contentType);
        }
    }
}
