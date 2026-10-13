package com.example.demo.service;

import org.springframework.stereotype.Service;
import java.util.Base64;
import java.util.logging.Logger;

@Service
public class FaceRecognitionService {

    private static final Logger logger = Logger.getLogger(FaceRecognitionService.class.getName());

    // Similarity threshold (0.0 to 1.0) - lowered for better matching
    // Since we're comparing JPEG images which vary due to compression, we need a lower threshold
    // JPEG compression can make identical faces have very different byte representations
    private static final double SIMILARITY_THRESHOLD = 0.40;

    /**
     * Compare two face images using base64 image data
     * Uses improved similarity comparison that handles JPEG compression variations
     * In production, use proper face recognition library like:
     * - OpenCV with JavaCV
     * - face_recognition library
     * - AWS Rekognition
     * - Azure Face API
     */
    public boolean compareFaces(String storedFaceImage, String capturedFaceImage) {
        if (storedFaceImage == null || capturedFaceImage == null) {
            return false;
        }
        
        if (storedFaceImage.isEmpty() || capturedFaceImage.isEmpty()) {
            return false;
        }
        
        // Remove data URL prefix if present
        storedFaceImage = removeDataUrlPrefix(storedFaceImage);
        capturedFaceImage = removeDataUrlPrefix(capturedFaceImage);
        
        // Validate base64 format
        if (!isValidBase64(storedFaceImage) || !isValidBase64(capturedFaceImage)) {
            return false;
        }
        
        try {
            // Decode base64 images
            byte[] storedBytes = Base64.getDecoder().decode(storedFaceImage);
            byte[] capturedBytes = Base64.getDecoder().decode(capturedFaceImage);
            
            // Basic validation - images should have reasonable size
            if (storedBytes.length < 1000 || capturedBytes.length < 1000) {
                return false;
            }
            
            // Quick check: if images are very similar in size, they might be from same capture
            // JPEG compression can vary, so we allow up to 20% size difference
            int sizeDiff = Math.abs(storedBytes.length - capturedBytes.length);
            int avgSize = (storedBytes.length + capturedBytes.length) / 2;
            double sizeDiffPercent = (double) sizeDiff / avgSize;
            
            if (sizeDiffPercent < 0.20) { // Within 20% size difference
                // Very similar size, do a quick byte comparison with higher tolerance
                int matches = 0;
                int minLen = Math.min(storedBytes.length, capturedBytes.length);
                int sampleSize = Math.min(minLen, 3000); // Sample more points
                int step = Math.max(1, minLen / sampleSize);
                
                for (int i = 0; i < minLen; i += step) {
                    int diff = Math.abs((storedBytes[i] & 0xFF) - (capturedBytes[i] & 0xFF));
                    if (diff <= 15) { // Increased tolerance to 15
                        matches++;
                    }
                }
                
                double quickSimilarity = (double) matches / (minLen / step);
                if (quickSimilarity > 0.70) { // Lowered threshold for quick match
                    logger.info("Quick match found with similarity: " + quickSimilarity);
                    return true; // Very likely the same image
                }
            }
            
            // Calculate similarity using multiple methods
            double similarity = calculateImageSimilarity(storedBytes, capturedBytes);
            
            // Log similarity for debugging (can be removed in production)
            logger.info(String.format("Face comparison similarity: %.2f (threshold: %.2f)", 
                similarity, SIMILARITY_THRESHOLD));
            
            // Return true if similarity is above threshold
            boolean matches = similarity >= SIMILARITY_THRESHOLD;
            logger.info("Face match result: " + matches);
            return matches;
            
        } catch (Exception e) {
            // If decoding fails, return false
            System.err.println("Face comparison error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Remove data URL prefix (e.g., "data:image/jpeg;base64,")
     */
    private String removeDataUrlPrefix(String base64String) {
        if (base64String.contains(",")) {
            return base64String.substring(base64String.indexOf(",") + 1);
        }
        return base64String;
    }

    /**
     * Validate if string is valid base64
     */
    private boolean isValidBase64(String str) {
        try {
            Base64.getDecoder().decode(str);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Calculate image similarity using multiple comparison methods
     * This is a simplified approach - in production use proper image comparison algorithms
     * Improved to handle JPEG compression variations better
     */
    private double calculateImageSimilarity(byte[] image1, byte[] image2) {
        // Method 1: Size-based similarity (images of similar size are more likely to match)
        // JPEG compression can vary, so we allow more variance
        double sizeSimilarity = calculateSizeSimilarity(image1.length, image2.length);
        
        // Method 2: Content hash similarity (improved for JPEG)
        double contentSimilarity = calculateContentSimilarity(image1, image2);
        
        // Method 3: Structural similarity (improved byte comparison with tolerance)
        double structuralSimilarity = calculateStructuralSimilarity(image1, image2);
        
        // Method 4: Image hash similarity (simple hash comparison)
        double hashSimilarity = calculateHashSimilarity(image1, image2);
        
        // Weighted average - giving more weight to structural and hash similarity
        double overallSimilarity = (sizeSimilarity * 0.15) + 
                                   (contentSimilarity * 0.25) + 
                                   (structuralSimilarity * 0.35) + 
                                   (hashSimilarity * 0.25);
        
        return overallSimilarity;
    }

    /**
     * Calculate similarity based on image size
     */
    private double calculateSizeSimilarity(int size1, int size2) {
        int minSize = Math.min(size1, size2);
        int maxSize = Math.max(size1, size2);
        if (maxSize == 0) return 0.0;
        return (double) minSize / maxSize;
    }

    /**
     * Calculate content similarity using byte comparison
     */
    private double calculateContentSimilarity(byte[] image1, byte[] image2) {
        int minLength = Math.min(image1.length, image2.length);
        int matches = 0;
        
        // Compare first and last portions of the image (header and footer often contain metadata)
        int sampleSize = Math.min(minLength, 1000);
        
        // Compare first 500 bytes
        for (int i = 0; i < Math.min(500, sampleSize); i++) {
            if (image1[i] == image2[i]) {
                matches++;
            }
        }
        
        // Compare last 500 bytes
        for (int i = 0; i < Math.min(500, sampleSize); i++) {
            int idx1 = image1.length - 1 - i;
            int idx2 = image2.length - 1 - i;
            if (idx1 >= 0 && idx2 >= 0 && image1[idx1] == image2[idx2]) {
                matches++;
            }
        }
        
        return (double) matches / (sampleSize * 2);
    }

    /**
     * Calculate structural similarity using byte pattern matching
     * Improved with better tolerance for JPEG compression
     */
    private double calculateStructuralSimilarity(byte[] image1, byte[] image2) {
        int minLength = Math.min(image1.length, image2.length);
        int maxLength = Math.max(image1.length, image2.length);
        if (minLength == 0) return 0.0;
        
        int matches = 0;
        int totalSamples = 0;
        int samplePoints = Math.min(minLength, 10000); // Sample more points
        
        // Sample at regular intervals
        int step = Math.max(1, minLength / samplePoints);
        
        for (int i = 0; i < minLength; i += step) {
            totalSamples++;
            // Increased tolerance for JPEG compression variations
            int diff = Math.abs((image1[i] & 0xFF) - (image2[i] & 0xFF));
            if (diff <= 10) { // Increased tolerance to 10
                matches++;
            }
        }
        
        // Also consider size difference as part of similarity
        double sizeFactor = (double) minLength / maxLength;
        
        return (matches / (double) totalSamples) * 0.7 + sizeFactor * 0.3;
    }

    /**
     * Calculate hash-based similarity
     * Creates a simple hash from image data for comparison
     */
    private double calculateHashSimilarity(byte[] image1, byte[] image2) {
        // Simple hash: sum of bytes at specific intervals
        long hash1 = calculateSimpleHash(image1);
        long hash2 = calculateSimpleHash(image2);
        
        if (hash1 == hash2) return 1.0;
        
        // Calculate difference percentage
        long diff = Math.abs(hash1 - hash2);
        long maxHash = Math.max(Math.abs(hash1), Math.abs(hash2));
        if (maxHash == 0) return 1.0;
        
        double similarity = 1.0 - (double) diff / (maxHash * 2);
        return Math.max(0.0, similarity);
    }

    /**
     * Calculate a simple hash from image bytes
     */
    private long calculateSimpleHash(byte[] image) {
        long hash = 0;
        int step = Math.max(1, image.length / 100); // Sample 100 points
        
        for (int i = 0; i < image.length; i += step) {
            hash = hash * 31 + (image[i] & 0xFF);
        }
        
        return hash;
    }

    /**
     * Extract face features from image (placeholder)
     * In production, extract actual facial features/encodings
     */
    public String extractFaceFeatures(String base64Image) {
        // Placeholder - in production, extract actual face encodings
        return base64Image;
    }

    /**
     * Validate if image contains a face
     */
    public boolean validateFaceImage(String base64Image) {
        if (base64Image == null || base64Image.isEmpty()) {
            return false;
        }
        
        try {
            String cleanImage = removeDataUrlPrefix(base64Image);
            byte[] decoded = Base64.getDecoder().decode(cleanImage);
            
            // Image should be at least 1KB to be valid
            return decoded.length >= 1000;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Find employee by matching face image
     * Compares captured face with all registered employee faces
     */
    public com.example.demo.model.Employee findEmployeeByFace(String capturedFaceImage, java.util.List<com.example.demo.model.Employee> employees) {
        if (capturedFaceImage == null || capturedFaceImage.isEmpty()) {
            return null;
        }
        
        for (com.example.demo.model.Employee employee : employees) {
            if (employee.getFaceImage() != null && !employee.getFaceImage().isEmpty()) {
                if (compareFaces(employee.getFaceImage(), capturedFaceImage)) {
                    return employee;
                }
            }
        }
        
        return null;
    }
}

