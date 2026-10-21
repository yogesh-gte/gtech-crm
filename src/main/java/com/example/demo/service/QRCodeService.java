package com.example.demo.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

@Service
public class QRCodeService {

    @PostConstruct
    public void init() {
        try {
            // Disable ImageIO disk caching globally so operations use memory instead of constrained C: drive
            ImageIO.setUseCache(false);
        } catch (Throwable ignored) {}
    }

    public String generateQRCodeToken() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public String generateQRCodeImage(String data) {
        try {
            // Ensure ImageIO disk caching is disabled
            try {
                ImageIO.setUseCache(false);
            } catch (Throwable ignored) {}

            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, 300, 300);

            try {
                ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
                MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
                byte[] pngData = pngOutputStream.toByteArray();
                return "data:image/png;base64," + Base64.getEncoder().encodeToString(pngData);
            } catch (Throwable t) {
                // High-efficiency in-memory SVG fallback: 0 disk I/O, 0 temp files, crystal-clear vector graphics
                return generateQRCodeSvg(bitMatrix);
            }
        } catch (Exception e) {
            // Safe fallback: never crash with 500 error
            return "";
        }
    }

    public String generateQRCodeSvg(BitMatrix matrix) {
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        StringBuilder sb = new StringBuilder(4096);
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
          .append(width).append(" ").append(height).append("\" shape-rendering=\"crispEdges\">")
          .append("<rect width=\"100%\" height=\"100%\" fill=\"#ffffff\"/>")
          .append("<path fill=\"#0f172a\" d=\"");
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (matrix.get(x, y)) {
                    sb.append("M").append(x).append(" ").append(y).append("h1v1h-1z ");
                }
            }
        }
        sb.append("\"/></svg>");
        String base64Svg = Base64.getEncoder().encodeToString(sb.toString().getBytes(StandardCharsets.UTF_8));
        return "data:image/svg+xml;base64," + base64Svg;
    }

    public String generateQRCodeData(String baseUrl, String employeeId, String token) {
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + "/attendance/mobile?empId=" + employeeId + "&token=" + token;
    }
}
