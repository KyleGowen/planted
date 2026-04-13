package com.planted.storage;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.planted.entity.PlantImage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;

@Slf4j
@Service
@Profile("prod")
@RequiredArgsConstructor
public class S3ImageStorageService implements ImageStorageService {

    private final AmazonS3 amazonS3;

    @Value("${planted.storage.s3-bucket}")
    private String bucket;

    @Override
    public String store(MultipartFile file, Long plantId) {
        try {
            String ext = getExtension(file.getOriginalFilename());
            String key = "plants/" + plantId + "/" + UUID.randomUUID() + ext;
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(file.getContentType());
            amazonS3.putObject(bucket, key, file.getInputStream(), metadata);
            log.info("Stored image to S3: {}/{}", bucket, key);
            return key;
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload file to S3", e);
        }
    }

    @Override
    public String storeBytes(byte[] data, String filename, String mimeType, Long plantId) {
        String key = "plants/" + plantId + "/" + filename;
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(data.length);
        metadata.setContentType(mimeType);
        amazonS3.putObject(bucket, key, new ByteArrayInputStream(data), metadata);
        log.info("Stored generated image to S3: {}/{}", bucket, key);
        return key;
    }

    @Override
    public String getUrl(String storagePath, PlantImage.StorageType storageType) {
        return amazonS3.getUrl(bucket, storagePath).toString();
    }

    @Override
    public PlantImage.StorageType getStorageType() {
        return PlantImage.StorageType.S3;
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf("."));
    }
}
