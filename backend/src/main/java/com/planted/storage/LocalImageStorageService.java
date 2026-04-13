package com.planted.storage;

import com.planted.entity.PlantImage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Service
@Profile("local")
public class LocalImageStorageService implements ImageStorageService {

    @Value("${planted.storage.local-path:./data/images}")
    private String basePath;

    @Value("${server.port:8080}")
    private int serverPort;

    @Override
    public String store(MultipartFile file, Long plantId) {
        try {
            String ext = getExtension(file.getOriginalFilename());
            String filename = UUID.randomUUID() + ext;
            String relativePath = "plants/" + plantId + "/" + filename;
            Path fullPath = Paths.get(basePath, relativePath);
            Files.createDirectories(fullPath.getParent());
            file.transferTo(fullPath.toFile());
            log.info("Stored image locally at {}", fullPath);
            return relativePath;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file locally", e);
        }
    }

    @Override
    public String storeBytes(byte[] data, String filename, String mimeType, Long plantId) {
        try {
            String relativePath = "plants/" + plantId + "/" + filename;
            Path fullPath = Paths.get(basePath, relativePath);
            Files.createDirectories(fullPath.getParent());
            Files.write(fullPath, data);
            log.info("Stored generated image locally at {}", fullPath);
            return relativePath;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store bytes locally", e);
        }
    }

    @Override
    public String getUrl(String storagePath, PlantImage.StorageType storageType) {
        return "http://localhost:" + serverPort + "/images/" + storagePath;
    }

    @Override
    public PlantImage.StorageType getStorageType() {
        return PlantImage.StorageType.LOCAL;
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf("."));
    }
}
