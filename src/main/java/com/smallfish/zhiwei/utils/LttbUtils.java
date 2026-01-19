package com.smallfish.zhiwei.utils;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * LTTB (Largest-Triangle-Three-Buckets) 降采样算法工具类
 * 用于在保持波形特征（峰值、谷值、拐点）的前提下，大幅减少时序数据点数，
 * 从而降低 LLM 的 Context Window 占用。
 */
public class LttbUtils {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Point {
        private double x; // 时间戳
        private double y; // 值
    }

    /**
     * 对时序数据进行 LTTB 降采样
     *
     * @param data      原始数据点列表
     * @param threshold 目标点数 (例如 20 或 50)
     * @return 降采样后的数据点列表
     */
    public static List<Point> downsample(List<Point> data, int threshold) {
        if (data == null || data.isEmpty() || threshold >= data.size()) {
            return data; // 数据量太少，不需要降采样
        }

        List<Point> sampled = new ArrayList<>(threshold);

        // 1. 第一个点总是保留
        sampled.add(data.get(0));

        // 桶的大小：排除首尾两个点后，剩下的点分给 (threshold - 2) 个桶
        double bucketSize = (double) (data.size() - 2) / (threshold - 2);

        int a = 0; // 上一个被选中的点的索引
        Point maxAreaPoint = null;
        int nextA = 0;

        for (int i = 0; i < threshold - 2; i++) {
            // 当前桶的范围
            int rangeStart = (int) Math.floor((i + 1) * bucketSize) + 1;
            int rangeEnd = (int) Math.floor((i + 2) * bucketSize) + 1;
            rangeEnd = Math.min(rangeEnd, data.size());

            // 下一个桶的平均点 (用于构建三角形的第三个顶点)
            // 这里的 rangeStart 实际上是当前桶的结束，也是下一个桶的开始
            int nextRangeStart = rangeEnd;
            int nextRangeEnd = (int) Math.floor((i + 3) * bucketSize) + 1;
            nextRangeEnd = Math.min(nextRangeEnd, data.size());

            double avgX = 0;
            double avgY = 0;
            int count = 0;

            for (int j = nextRangeStart; j < nextRangeEnd; j++) {
                avgX += data.get(j).getX();
                avgY += data.get(j).getY();
                count++;
            }
            if (count > 0) {
                avgX /= count;
                avgY /= count;
            }

            // 在当前桶中寻找与点 A (data[a]) 和点 C (avgX, avgY) 构成最大三角形面积的点 B
            double maxArea = -1;
            Point pointA = data.get(a);

            for (int j = rangeStart; j < rangeEnd; j++) {
                Point pointB = data.get(j);
                // 计算三角形面积: 0.5 * |x1(y2 - y3) + x2(y3 - y1) + x3(y1 - y2)|
                double area = Math.abs(
                        (pointA.getX() - avgX) * (pointB.getY() - pointA.getY()) -
                                (pointA.getX() - pointB.getX()) * (avgY - pointA.getY())
                ) * 0.5;

                if (area > maxArea) {
                    maxArea = area;
                    maxAreaPoint = pointB;
                    nextA = j; // 记录这个点的索引，作为下一轮的点 A
                }
            }

            if (maxAreaPoint != null) {
                sampled.add(maxAreaPoint);
                a = nextA; // 更新点 A
            }
        }

        // 3. 最后一个点总是保留
        sampled.add(data.get(data.size() - 1));

        return sampled;
    }
}
