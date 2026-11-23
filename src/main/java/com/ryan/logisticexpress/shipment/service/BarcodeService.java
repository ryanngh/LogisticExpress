package com.ryan.logisticexpress.shipment.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.ryan.logisticexpress.common.exception.BadRequestException;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.Base64;

@Service
public class BarcodeService {

    public byte[] generateBarcodePng(String text, int width, int height) {
        if (text == null || text.isBlank()) {
            throw new BadRequestException("INVALID_BARCODE_TEXT", "Barcode text cannot be empty");
        }
        try {
            BitMatrix bitMatrix = new MultiFormatWriter().encode(text, BarcodeFormat.CODE_128, width, height);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BadRequestException("BARCODE_GENERATION_FAILED", "Failed to generate barcode: " + e.getMessage());
        }
    }

    public byte[] generateQrCodePng(String text, int width, int height) {
        if (text == null || text.isBlank()) {
            throw new BadRequestException("INVALID_QR_TEXT", "QR code text cannot be empty");
        }
        try {
            BitMatrix bitMatrix = new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, width, height);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new BadRequestException("QR_GENERATION_FAILED", "Failed to generate QR code: " + e.getMessage());
        }
    }

    public String generateBarcodeBase64(String text, int width, int height) {
        return Base64.getEncoder().encodeToString(generateBarcodePng(text, width, height));
    }

    public String generateQrCodeBase64(String text, int width, int height) {
        return Base64.getEncoder().encodeToString(generateQrCodePng(text, width, height));
    }
}
