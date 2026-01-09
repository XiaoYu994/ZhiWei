package com.smallfish.zhiwei.service;

import com.smallfish.zhiwei.config.DocumentChunkConfig;
import com.smallfish.zhiwei.dto.DocumentChunk;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档分片服务
 * 负责将长文档切分为多个有语义完整性的小片段
 */
@Slf4j
@Service
public class DocumentChunkService {

    @Resource
    private DocumentChunkConfig chunkConfig;

    /**
     * 智能分片文档
     * 优先按照标题、段落边界进行分割，保持语义完整性
     *
     * @param content 文档内容
     * @param filePath 文件路径（用于日志）
     * @return 文档分片列表
     */
    public List<DocumentChunk> chunkDocument(String content, String filePath) {
        List<DocumentChunk> chunks = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return chunks;
        }

        // 1. 按标题分割 (保留原始偏移量)
        List<Section> sections = splitByHeadings(content);

        // 2. 进一步分片
        int globalChunkIndex = 0;
        for (Section section : sections) {
            List<DocumentChunk> sectionChunks = chunkSection(section, globalChunkIndex);
            chunks.addAll(sectionChunks);
            globalChunkIndex += sectionChunks.size();
        }

        log.info("文档分片完成: {} -> {} 个分片", filePath, chunks.size());
        return chunks;
    }

    /**
     * 按照 Markdown 标题分割文档
     */
    private List<Section> splitByHeadings(String content) {
        List<Section> sections = new ArrayList<>();
        // 匹配 Markdown 标题
        Pattern headingPattern = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
        Matcher matcher = headingPattern.matcher(content);

        int lastEnd = 0;
        String currentTitle = null; // 初始为无标题

        while (matcher.find()) {
            int start = matcher.start();
            // 提取上一段内容
            if (start > lastEnd) {
                String sectionContent = content.substring(lastEnd, start);
                // 只有非空才添加 (注意：这里不要 trim，否则会丢失偏移量准确性，除非你不关心高亮)
                if (!sectionContent.trim().isEmpty()) {
                    sections.add(new Section(currentTitle, sectionContent, lastEnd));
                }
            }
            // 标题本身也应该作为下一段内容的开头，或者作为独立的语义信息
            // 你的原逻辑是将标题文本包含在内容里，这里保持一致
            currentTitle = matcher.group(2).trim();
            lastEnd = start;
        }

        if (lastEnd < content.length()) {
            sections.add(new Section(currentTitle, content.substring(lastEnd), lastEnd));
        }

        return sections;
    }

    /**
     * 对单个章节进行分片
     */
    private List<DocumentChunk> chunkSection(Section section, int startChunkIndex) {
        List<DocumentChunk> chunks = new ArrayList<>();
        String content = section.content;

        // 如果整体小于最大长度，直接返回
        if (content.length() <= chunkConfig.getMaxSize()) {
            chunks.add(new DocumentChunk(content.trim(), section.startIndex, section.startIndex + content.length(), startChunkIndex, section.title));
            return chunks;
        }

        // 核心修改：使用递归或循环处理超长文本
        // 这里使用简单的基于字符窗口的滑动处理，这是最稳健的方式
        int pointer = 0;
        int chunkIndex = startChunkIndex;
        int contentLength = content.length();
        int maxSize = chunkConfig.getMaxSize();
        int overlap = chunkConfig.getOverlap();

        // 边界保护：overlap 绝不能大于等于 maxSize，否则必然死循环
        if (overlap >= maxSize) {
            overlap = maxSize / 2;
        }

        while (pointer < contentLength) {
            // 1. 确定当前分片的建议结束位置
            int end = Math.min(pointer + maxSize, contentLength);

            // 2. 尝试寻找最佳分割点（换行符 > 句号 > 逗号 > 强制截断）
            if (end < contentLength) {
                int splitPoint = findBestSplitPoint(content, pointer, end);
                if (splitPoint > pointer) {
                    end = splitPoint;
                }
            }

            // 3. 提取分片内容
            String chunkText = content.substring(pointer, end);

            // 4. 构建分片对象 (注意：这里的 startIndex 是相对于原文件的绝对偏移)
            // 只有在保存时才 trim，但要小心 trim 会导致高亮偏移，
            // 建议：如果需要前端高亮，不要存 trim 后的 text，或者记录 trim 掉的 offset
            String savedText = chunkText.trim();
            if (!savedText.isEmpty()) {
                chunks.add(new DocumentChunk(
                        savedText,
                        section.startIndex + pointer, // 绝对开始位置
                        section.startIndex + end,     // 绝对结束位置
                        chunkIndex++,
                        section.title
                ));
            }

            // 5. 移动指针
            if (end >= contentLength) {
                break;
            }

            // 6. 处理重叠 (Overlap)
            // 下一个分片的开始位置应该回退 overlap 个字符
            int nextPointer = end - overlap;
            // 强制前进规则：
            // 如果回退后的位置(nextPointer) 不比当前起点(pointer) 大，
            // 说明这个分片太短了，overlap 把整个分片都盖住了，或者切分点就在起点附近。
            // 此时必须强制向前移动，至少移动到 end 的位置（即放弃 overlap），或者移动 1 格。
            if (nextPointer <= pointer) {
                nextPointer = Math.max(pointer + 1, end);
            }

            pointer = nextPointer;
        }

        return chunks;
    }

    /**
     * 寻找最佳分割点，零拷贝优化版
     * 在 [start, limit] 范围内从后往前找
     */
    private int findBestSplitPoint(String content, int start, int limit) {
        // 1. 优先级: 双换行
        int idx = content.lastIndexOf("\n\n", limit);
        if (idx >= start) return idx + 2;
        // 2. 优先级: 单换行
        idx = content.lastIndexOf("\n", limit);
        if (idx >= start) return idx + 1;

        // 3. 优先级: 句子结束符 (。！？)
        // 这里的逻辑稍微复杂点，因为要找三个标点中最后出现的一个
        int p1 = content.lastIndexOf("。", limit);
        int p2 = content.lastIndexOf("！", limit);
        int p3 = content.lastIndexOf("？", limit);
        int maxP = Math.max(p1, Math.max(p2, p3));
        if (maxP >= start) return maxP + 1;

        // 4. 优先级: 逗号分号
        int c1 = content.lastIndexOf("，", limit);
        int c2 = content.lastIndexOf("；", limit);
        int maxC = Math.max(c1, c2);
        if (maxC >= start) return maxC + 1;

        // 没找到，强制截断
        return limit;
    }

    @AllArgsConstructor
    private static class Section {
        String title;
        String content;
        int startIndex;
    }
}