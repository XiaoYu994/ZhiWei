package com.smallfish.zhiwei.service.ingestion;

import com.smallfish.zhiwei.config.FileUploadConfig;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

/**
 * 子系统：文件服务
 * 职责：只负责扫描磁盘、过滤文件、读取内容
 */
@Slf4j
@Service
public class LocalFileService {

    @Resource
    private FileUploadConfig fileUploadConfig;

    public List<File> scanDirectory(String pathStr) {
        File directory = new File(pathStr);
        if (!directory.exists() || !directory.isDirectory()) {
            throw new IllegalArgumentException("目录不存在: " + pathStr);
        }

        // 简单过滤 txt 和 md
        File[] files = directory.listFiles((dir, name) -> {
            String lowerName = name.toLowerCase();
            // 使用配置中的扩展名列表进行过滤
            return fileUploadConfig.getAllowedExtensions().stream()
                    .anyMatch(ext -> lowerName.endsWith(ext.toLowerCase()));
        });

        if (files == null) return Collections.emptyList();
        return List.of(files);
    }

    public String readFileContent(File file) throws IOException {
        return Files.readString(file.toPath());
    }
}
