package com.github.aeddddd.ae2enhanced.specialcrafting.lp;

import java.util.List;
import java.util.Map;

/**
 * 列压缩稀疏矩阵(CSC), 修正单纯形的约束矩阵存储. 配方矩阵是小整数系数的高度稀疏矩阵,
 * 密度低于 0.1%, CSC 按列存取直接服务定价 y·A_j 与 FTRAN 列展开.
 */
public final class SparseMatrix {

    /** 行数, 即约束数. */
    public final int rows;
    /** 列数, 即变量数. */
    public final int cols;
    /** 列指针, 长度 cols+1. */
    public final int[] colPtr;
    /** 非零元行号, 长度 nnz, 列内升序. */
    public final int[] rowIdx;
    /** 非零元值, 与 rowIdx 对齐. */
    public final double[] values;

    private SparseMatrix(int rows, int cols, int[] colPtr, int[] rowIdx, double[] values) {
        this.rows = rows;
        this.cols = cols;
        this.colPtr = colPtr;
        this.rowIdx = rowIdx;
        this.values = values;
    }

    /** 直接以既有 CSC 数组构建, colPtr/rowIdx 结构共享不拷贝, 供行列均衡等保结构变换使用,
     * 调用方必须保证传入数组不被后续修改. */
    public static SparseMatrix of(int rows, int cols, int[] colPtr, int[] rowIdx,
            double[] values) {
        return new SparseMatrix(rows, cols, colPtr, rowIdx, values);
    }

    /** 从逐列(行号 → 值)映射列表构建, 丢弃 |v| < 1e-15 的零元, 列内按行号升序. */
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
            // TreeMap 副本保证列内行号升序, 这是 CSC 约定
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

    /** 点积 y·A_j, 定价用, y 为逻辑行序稠密向量. */
    public double dotColumn(int j, double[] y) {
        double sum = 0;
        for (int p = this.colPtr[j]; p < this.colPtr[j + 1]; p++) {
            sum += y[this.rowIdx[p]] * this.values[p];
        }
        return sum;
    }

    /** 把列 j 展开进稠密工作向量 work, 累加语义, 调用方负责清零. */
    public void expandColumn(int j, double[] work) {
        for (int p = this.colPtr[j]; p < this.colPtr[j + 1]; p++) {
            work[this.rowIdx[p]] += this.values[p];
        }
    }

    /** 行无穷范数 ||A_i,||∞, 返回长度 rows 的新数组, Ruiz 均衡用. */
    public double[] rowInfNorms() {
        double[] norms = new double[this.rows];
        for (int p = 0; p < this.values.length; p++) {
            double a = Math.abs(this.values[p]);
            if (a > norms[this.rowIdx[p]]) {
                norms[this.rowIdx[p]] = a;
            }
        }
        return norms;
    }

    /** 列无穷范数 ||A_,j||∞, 返回长度 cols 的新数组, Ruiz 均衡用. */
    public double[] colInfNorms() {
        double[] norms = new double[this.cols];
        for (int j = 0; j < this.cols; j++) {
            for (int p = this.colPtr[j]; p < this.colPtr[j + 1]; p++) {
                double a = Math.abs(this.values[p]);
                if (a > norms[j]) {
                    norms[j] = a;
                }
            }
        }
        return norms;
    }

    /** 就地左乘对角行缩放, 逐非零元执行 values[p] *= rowScale[rowIdx[p]]. */
    public void scaleRowsInPlace(double[] rowScale) {
        for (int p = 0; p < this.values.length; p++) {
            this.values[p] *= rowScale[this.rowIdx[p]];
        }
    }

    /** 就地右乘对角列缩放, 列 j 全部非零元乘以 colScale[j]. */
    public void scaleColumnsInPlace(double[] colScale) {
        for (int j = 0; j < this.cols; j++) {
            for (int p = this.colPtr[j]; p < this.colPtr[j + 1]; p++) {
                this.values[p] *= colScale[j];
            }
        }
    }
}
