package com.github.aeddddd.ae2enhanced.specialcrafting.lp;

import java.util.ArrayList;
import java.util.List;

/**
 * 基矩阵因子化:稀疏 LU（部分主元）+ Forrest–Tomlin 秩一更新.
 * 因子布局:B = P·L·U·E₁·E₂···E_k（P 行置换,L 单位下三角按列存,
 * U 上三角按列存,E_i 为换基产生的 eta 矩阵）。存储约定:</p>
 * <ul>
 * <li><b>位置（position）</b>:消元顺序的逻辑行号 0..m-1;<b>矩阵行（row）</b>:
 * 原始约束行号。rowOfPos / posOfRow 双向映射随选主元交换;</li>
 * <li>L 列 t:位置 &gt; t 的元素（单位对角隐式）;</li>
 * <li>U 列 t:位置 ≤ t 的元素（对角主元为末元）;</li>
 * <li>eta:换基时进入列的 FTRAN 结果 α 与主元位置 p.</li>
 * </ul>
 * 数值策略:主元取剩余位置中最大模（部分主元）,低于 {@link #PIVOT_MIN}
 * 判数值失败;eta 数超 {@link #MAX_ETA} 由调用方触发重分解。
 */
final class Basis {

    /** 主元模的下限(低于即判基奇异/病态). */
    static final double PIVOT_MIN = 1e-11;
    /** eta 向量数上限(超限重分解,控制 FTRAN/BTRAN 成本与误差累积).
     * 取 30 而非经典 100:eta 链漂移 ∝ 链长,链越长导航用 xb 与真实顶点偏差越大,
     * 出口越界误判风险越高;m ≲ 数百时重分解廉价,短链更稳. */
    static final int MAX_ETA = 30;

    /** 数值失败异常(基奇异/病态),由 RevisedSimplex 转为 NUMERIC_FAILURE.
     * 携带失败位置与最佳候选行,供调用方做"单位列修补"恢复. */
    static final class NumericException extends Exception {
        /** 失败的列位置(factorize 中第几列).-1 = 无定位信息. */
        final int failPos;
        /** 剩余位置中 |work| 最大的矩阵行(修补单位列的候选行).-1 = 无. */
        final int bestRow;

        NumericException(String message) {
            this(message, -1, -1);
        }

        NumericException(String message, int failPos, int bestRow) {
            super(message);
            this.failPos = failPos;
            this.bestRow = bestRow;
        }
    }

    /** 稀疏列视图(LpModel 结构列 / eta 列的统一输入形态). */
    interface SparseCol {
        int nnz();

        int indexAt(int k);

        double valueAt(int k);
    }

    private final int m;
    // L:按列,位置 > t 的元素(行号为"位置")
    private final List<int[]> lIdx = new ArrayList<>();
    private final List<double[]> lVal = new ArrayList<>();
    // U:按行,位置 >= t 的元素(首元为主元)
    private final List<int[]> uIdx = new ArrayList<>();
    private final List<double[]> uVal = new ArrayList<>();
    // 位置 ↔ 矩阵行 映射
    private int[] rowOfPos;
    private int[] posOfRow;
    // Forrest–Tomlin eta 向量(位置语义)
    private final List<Integer> etaPivot = new ArrayList<>();
    private final List<int[]> etaIdx = new ArrayList<>();
    private final List<double[]> etaVal = new ArrayList<>();

    Basis(int m) {
        this.m = m;
    }

    int etaCount() {
        return this.etaPivot.size();
    }

    /**
     * 对基列集合做稀疏 LU 分解(重置全部 eta).
     *
     * @param columns 基列(长度 m,矩阵行语义)
     */
    void factorize(List<? extends SparseCol> columns) throws NumericException {
        if (columns.size() != this.m) {
            throw new IllegalArgumentException("基列数与维数不符");
        }
        this.lIdx.clear();
        this.lVal.clear();
        this.uIdx.clear();
        this.uVal.clear();
        this.etaPivot.clear();
        this.etaIdx.clear();
        this.etaVal.clear();
        this.rowOfPos = new int[this.m];
        this.posOfRow = new int[this.m];
        for (int i = 0; i < this.m; i++) {
            this.rowOfPos[i] = i;
            this.posOfRow[i] = i;
        }
        double[] work = new double[this.m];
        for (int k = 0; k < this.m; k++) {
            // 列 k 展开到位置语义工作向量
            SparseCol col = columns.get(k);
            for (int t = 0; t < col.nnz(); t++) {
                work[this.posOfRow[col.indexAt(t)]] = col.valueAt(t);
            }
            // 已有 L 列前代(消去位置 < k;顺序读取保证先写后读正确)
            for (int t = 0; t < k; t++) {
                double coeff = work[t];
                if (coeff == 0) {
                    continue;
                }
                int[] idx = this.lIdx.get(t);
                double[] val = this.lVal.get(t);
                for (int q = 0; q < idx.length; q++) {
                    work[idx[q]] -= coeff * val[q];
                }
            }
            // 选主元:位置 >= k 中最大模
            int pivotPos = -1;
            double pivotAbs = PIVOT_MIN;
            int bestPos = k;
            double bestAbs = 0;
            for (int pos = k; pos < this.m; pos++) {
                double abs = Math.abs(work[pos]);
                if (abs > bestAbs) {
                    bestAbs = abs;
                    bestPos = pos;
                }
                if (abs > pivotAbs) {
                    pivotAbs = abs;
                    pivotPos = pos;
                }
            }
            if (pivotPos < 0) {
                // 携带失败列位置与最大剩余行,供 RevisedSimplex 单位列修补
                throw new NumericException(
                        "基奇异:列 " + k + " 无主元候选(最大剩余模 " + bestAbs + ")", k,
                        this.rowOfPos[bestPos]);
            }
            if (pivotPos != k) {
                // 交换位置映射、工作向量元素,以及先前 L 列在位置 k/pivotPos 的值
                // (选主元换行必须同步已存 L 因子,否则因子化被污染)
                int rowK = this.rowOfPos[k];
                int rowP = this.rowOfPos[pivotPos];
                this.rowOfPos[k] = rowP;
                this.rowOfPos[pivotPos] = rowK;
                this.posOfRow[rowP] = k;
                this.posOfRow[rowK] = pivotPos;
                double tmp = work[k];
                work[k] = work[pivotPos];
                work[pivotPos] = tmp;
                for (int t = 0; t < k; t++) {
                    swapAt(this.lIdx.get(t), this.lVal.get(t), k, pivotPos);
                }
            }
            double pivot = work[k];
            // U 列 k:位置 ≤ k 的非零元(乘子部分 + 对角主元,主元为末元)
            int uCount = 0;
            for (int pos = 0; pos <= k; pos++) {
                if (work[pos] != 0) {
                    uCount++;
                }
            }
            int[] uColIdx = new int[uCount];
            double[] uColVal = new double[uCount];
            int p = 0;
            for (int pos = 0; pos <= k; pos++) {
                if (work[pos] != 0) {
                    uColIdx[p] = pos;
                    uColVal[p] = work[pos];
                    p++;
                }
            }
            this.uIdx.add(uColIdx);
            this.uVal.add(uColVal);
            // L 列 k:位置 > k 的非零元 / 主元
            int lCount = 0;
            for (int pos = k + 1; pos < this.m; pos++) {
                if (work[pos] != 0) {
                    lCount++;
                }
            }
            int[] lColIdx = new int[lCount];
            double[] lColVal = new double[lCount];
            p = 0;
            for (int pos = k + 1; pos < this.m; pos++) {
                if (work[pos] != 0) {
                    lColIdx[p] = pos;
                    lColVal[p] = work[pos] / pivot;
                    p++;
                }
            }
            this.lIdx.add(lColIdx);
            this.lVal.add(lColVal);
            // 整行清理(L 前代会写入位置 < k 的元素,必须全部复位)
            java.util.Arrays.fill(work, 0);
        }
    }

    /** 在稀疏列中交换两个位置上的值(选主元换行时同步先前 L 列). */
    private static void swapAt(int[] idx, double[] val, int posA, int posB) {
        int atA = -1;
        int atB = -1;
        for (int q = 0; q < idx.length; q++) {
            if (idx[q] == posA) {
                atA = q;
            } else if (idx[q] == posB) {
                atB = q;
            }
        }
        if (atA >= 0 && atB >= 0) {
            double tmp = val[atA];
            val[atA] = val[atB];
            val[atB] = tmp;
        } else if (atA >= 0) {
            idx[atA] = posB;
        } else if (atB >= 0) {
            idx[atB] = posA;
        }
    }

    /**
     * Forrest–Tomlin 换基更新:进入列的 FTRAN 结果 α 在主元位置 pivotPos
     * 替换旧基列(eta 矩阵 E = I + (η − e_p)e_pᵀ,η = α).
     */
    void update(int pivotPos, int[] alphaIdx, double[] alphaVal) {
        this.etaPivot.add(pivotPos);
        this.etaIdx.add(alphaIdx);
        this.etaVal.add(alphaVal);
    }

    /**
     * FTRAN:就地解 B x = b(输入矩阵行语义,输出位置语义无差别——
     * 全程在同一向量上按位置/行映射变换,调用方视为"基序"向量即可).
     * 序列:P → L 前代 → U 回代 → eta 依次 E₁⁻¹…E_k⁻¹.
     */
    void ftran(double[] x) {
        // P:按位置重排(逻辑位置 i 取矩阵行 rowOfPos[i])
        double[] tmp = new double[this.m];
        for (int i = 0; i < this.m; i++) {
            tmp[i] = x[this.rowOfPos[i]];
        }
        System.arraycopy(tmp, 0, x, 0, this.m);
        // L 前代(单位对角,按列)
        for (int t = 0; t < this.m; t++) {
            double coeff = x[t];
            if (coeff == 0) {
                continue;
            }
            int[] idx = this.lIdx.get(t);
            double[] val = this.lVal.get(t);
            for (int q = 0; q < idx.length; q++) {
                x[idx[q]] -= coeff * val[q];
            }
        }
        // U 回代(按列:对角为末元,先除主元再向上消除)
        for (int t = this.m - 1; t >= 0; t--) {
            int[] idx = this.uIdx.get(t);
            double[] val = this.uVal.get(t);
            double pivot = val[idx.length - 1];
            double xt = x[t] / pivot;
            x[t] = xt;
            for (int q = 0; q < idx.length - 1; q++) {
                x[idx[q]] -= xt * val[q];
            }
        }
        // eta:E₁⁻¹ … E_k⁻¹(顺序)
        for (int e = 0; e < this.etaPivot.size(); e++) {
            int p = this.etaPivot.get(e);
            int[] idx = this.etaIdx.get(e);
            double[] val = this.etaVal.get(e);
            double pivotVal = 0;
            for (int q = 0; q < idx.length; q++) {
                if (idx[q] == p) {
                    pivotVal = val[q];
                    break;
                }
            }
            double coeff = x[p] / pivotVal;
            for (int q = 0; q < idx.length; q++) {
                if (idx[q] != p) {
                    x[idx[q]] -= coeff * val[q];
                }
            }
            x[p] = coeff;
        }
    }

    /**
     * BTRAN:就地解 Bᵀ y = c(对偶/定价用).
     * 序列:eta 逆序 E_k⁻ᵀ…E₁⁻ᵀ → Uᵀ 前代 → Lᵀ 回代 → Pᵀ.
     */
    void btran(double[] x) {
        // eta 逆序:E⁻ᵀ 仅改主元行
        for (int e = this.etaPivot.size() - 1; e >= 0; e--) {
            int p = this.etaPivot.get(e);
            int[] idx = this.etaIdx.get(e);
            double[] val = this.etaVal.get(e);
            double pivotVal = 0;
            for (int q = 0; q < idx.length; q++) {
                if (idx[q] == p) {
                    pivotVal = val[q];
                    break;
                }
            }
            double sum = x[p];
            for (int q = 0; q < idx.length; q++) {
                if (idx[q] != p) {
                    sum -= x[idx[q]] * val[q];
                }
            }
            x[p] = sum / pivotVal;
        }
        // Uᵀ 前代(按列:t 升序,对角上方条目先消再除主元)
        for (int t = 0; t < this.m; t++) {
            int[] idx = this.uIdx.get(t);
            double[] val = this.uVal.get(t);
            double sum = x[t];
            for (int q = 0; q < idx.length - 1; q++) {
                sum -= x[idx[q]] * val[q];
            }
            x[t] = sum / val[idx.length - 1];
        }
        // Lᵀ 回代(单位对角,逆序)
        for (int t = this.m - 1; t >= 0; t--) {
            int[] idx = this.lIdx.get(t);
            double[] val = this.lVal.get(t);
            double sum = x[t];
            for (int q = 0; q < idx.length; q++) {
                sum -= x[idx[q]] * val[q];
            }
            x[t] = sum;
        }
        // Pᵀ:散回矩阵行语义
        double[] tmp = new double[this.m];
        for (int i = 0; i < this.m; i++) {
            tmp[this.rowOfPos[i]] = x[i];
        }
        System.arraycopy(tmp, 0, x, 0, this.m);
    }
}
