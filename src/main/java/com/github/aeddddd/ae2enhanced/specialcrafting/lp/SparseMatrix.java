package com.github.aeddddd.ae2enhanced.specialcrafting.lp;

import java.util.List;
import java.util.Map;

/**
 * 列压缩稀疏矩阵（CSC）:修正单纯形的约束矩阵存储.
 * <p>合成计划 LP 的约束矩阵是小整数系数（配方计数）的高度稀疏矩阵
 * （密度 &lt; 0.1%）,CSC 按列存取直接服务定价（y·A_j）与 FTRAN 列展开.</p>
 */
public final class SparseMatrix {

    /** 行数（约束数）. */
    public final int rows;
    /** 列数（变量数）. */
    public final int cols;
    /** 列指针（长度 cols+1）. */
    public final int[] colPtr;
    /** 非零元行号（长度 nnz,列内升序）. */
    public final int[] rowIdx;
    /** 非零元值（与 rowIdx 对齐）. */
    public final double[] values;

    private SparseMatrix(int rows, int cols, int[] colPtr, int[] rowIdx, double[] values) {
        this.rows = rows;
        this.cols = cols;
        this.colPtr = colPtr;
        this.rowIdx = rowIdx;
        this.values = values;
    }

    /** 直接以既有 CSC 数组构建(colPtr/rowIdx 结构共享、不拷贝;供行列均衡等
     * 保结构变换使用——调用方必须保证传入数组不被后续修改). */
    public static SparseMatrix of(int rows, int cols, int[] colPtr, int[] rowIdx,
            double[] values) {
        return new SparseMatrix(rows, cols, colPtr, rowIdx, values);
    }

    /**
     * 从逐列(行号 → 值)映射列表构建;丢弃 |v| &lt; 1e-15 的零元,列内按行号升序.
     */
    public static SparseMatrix fromColumns(int rows, List<Map<Integer, Double>> columns) {
        int cols = columns.size();
        int[] colPtr = new int[cols + 1];
        for (int j = 0; j < cols; j++) {
            int count = 0;
            for (double v : columns.get(j).values()) {
                if (Math.abs(v) >= 1e-15) {
                    count++;
                }
            }
            colPtr[j + 1] = colPtr[j] + count;
        }
        int[] rowIdx = new int[colPtr[cols]];
        double[] values = new double[colPtr[cols]];
        for (int j = 0; j < cols; j++) {
            int p = colPtr[j];
            // TreeMap 副本保证列内行号升序(CSC 约定)
            for (Map.Entry<Integer, Double> e : new java.util.TreeMap<>(columns.get(j)).entrySet()) {
                if (Math.abs(e.getValue()) >= 1e-15) {
                    rowIdx[p] = e.getKey();
                    values[p] = e.getValue();
                    p++;
                }
            }
        }
        return new SparseMatrix(rows, cols, colPtr, rowIdx, values);
    }

    /** 列 j 的非零元数. */
    public int nnzOf(int j) {
        return this.colPtr[j + 1] - this.colPtr[j];
    }

    /** 点积 y·A_j（定价用,y 为逻辑行序稠密向量）. */
    public double dotColumn(int j, double[] y) {
        double sum = 0;
        for (int p = this.colPtr[j]; p < this.colPtr[j + 1]; p++) {
            sum += y[this.rowIdx[p]] * this.values[p];
        }
        return sum;
    }

    /** 把列 j 展开进稠密工作向量 work(累加语义,调用方负责清零). */
    public void expandColumn(int j, double[] work) {
        for (int p = this.colPtr[j]; p < this.colPtr[j + 1]; p++) {
            work[this.rowIdx[p]] += this.values[p];
        }
    }
}
