package com.smallfish.zhiwei.service.storage.impl;

import com.smallfish.zhiwei.config.FileUploadConfig;
import com.smallfish.zhiwei.dto.req.FileUploadReqDTO;
import com.smallfish.zhiwei.service.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocalFileStorageService implements FileStorageService {

    private final FileUploadConfig fileUploadConfig;

    // 最大 100 MB
    private static final int FILE_SIZE_MAX = 100 * 1024 * 1024;

    @Override
    public FileUploadReqDTO upload(MultipartFile file) {
        // 1. 基础校验
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }

        // 2. 安全的文件名获取
        String originalFilename = StringUtils.cleanPath(Objects.requireNonNull(file.getOriginalFilename()));
        if (originalFilename.contains("..")) {
            throw new IllegalArgumentException("非法的文件名");
        }

        // 3. 扩展名校验
        String extension = StringUtils.getFilenameExtension(originalFilename);
        if (!fileUploadConfig.isAllowed(extension)) {
            throw new IllegalArgumentException("不支持的文件格式");
        }

        // 4. 大小校验
        if (file.getSize() > FILE_SIZE_MAX) {
            throw new IllegalArgumentException("文件过大，当前限制 100MB");
        }

        try {
            // 目录准备
            Path uploadDir = Paths.get(fileUploadConfig.getPath()).normalize();
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            // 使用原始文件名
            Path filePath = uploadDir.resolve(originalFilename).normalize();

            // 原子性文件写入
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            log.info("文件存储成功: {}", filePath);

            return new FileUploadReqDTO(
                    originalFilename,
                    filePath.toString(),
                    file.getSize()
            );

        } catch (IOException e) {
            log.error("文件存储失败", e);
            throw new RuntimeException("文件存储失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<FileUploadReqDTO> listFiles() {
        List<FileUploadReqDTO> files = new ArrayList<>();
        Path uploadDir = Paths.get(fileUploadConfig.getPath()).normalize();

        if (!Files.exists(uploadDir)) {
            return files;
        }

        try (Stream<Path> pathStream = Files.walk(uploadDir, 1)) {
            pathStream.filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            files.add(new FileUploadReqDTO(
                                    path.getFileName().toString(),
                                    path.toAbsolutePath().toString(),
                                    Files.size(path)
                            ));
                        } catch (IOException e) {
                            log.warn("无法获取文件信息: {}", path, e);
                        }
                    });
        } catch (IOException e) {
            log.error("遍历文件列表失败", e);
            throw new RuntimeException("遍历文件列表失败", e);
        }

        return files;
    }

    @Override
    public String readFileContent(String pathStr) {
        try {
            Path path = Paths.get(pathStr);
            return Files.readString(path);
        } catch (IOException e) {
            throw new RuntimeException("读取文件内容失败: " + e.getMessage(), e);
        }
    }

    /**
     * 读取文件内容 (Overload for File object)
     */
    public String readFileContent(File file) throws IOException {
        return Files.readString(file.toPath());
    }

    @Override
    public boolean delete(String fileName) {
        // 1. 获取配置的基础目录
        Path uploadDir = Paths.get(fileUploadConfig.getPath()).normalize();
        // 2. 拼接完整路径 (basePath + fileName)
        // 注意：这里传入的 fileName 必须是纯文件名 (如 "Spring.md")，不能包含路径
        Path targetPath = uploadDir.resolve(fileName).normalize();

        log.info("尝试删除本地文件，计算路径: {}", targetPath.toAbsolutePath());

        try {
            // 3. 执行删除
            boolean deleted = Files.deleteIfExists(targetPath);
            if (deleted) {
                log.info("文件删除成功: {}", fileName);
            } else {
                log.warn("文件删除失败（文件不存在）: {}", targetPath.toAbsolutePath());
            }
            return deleted;
        } catch (IOException e) {
            log.error("删除文件时发生异常", e);
            return false;
        }
    }

    /**
     * 扫描目录，获取符合配置扩展名的文件列表
     * (Moved from LocalFileService)
     */
    public List<File> scanDirectory(String pathStr) {
        File directory = new File(pathStr);
        if (!directory.exists() || !directory.isDirectory()) {
            throw new IllegalArgumentException("目录不存在: " + pathStr);
        }

        // 简单过滤
        File[] files = directory.listFiles((dir, name) -> {
            String lowerName = name.toLowerCase();
            // 使用配置中的扩展名列表进行过滤
            return fileUploadConfig.getAllowedExtensions().stream()
                    .anyMatch(ext -> lowerName.endsWith(ext.toLowerCase()));
        });

        if (files == null) return java.util.Collections.emptyList();
        return List.of(files);
    }

}
