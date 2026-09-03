package com.github.aeddddd.ae2enhanced.specialcrafting.lp;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LP 模型失败转储/回放（数值缺陷离线复现用）.
 * <p>求解器判 NUMERIC_FAILURE 等异常时把完整模型(m/n、CSC 矩阵、b、cost、界)
 * 序列化到 run 目录 {@code lp-dumps/};测试侧 {@code LpModelReplayTest} 可逐字节
 * 回放同一份模型,无需真实游戏环境即可定位数值缺陷.</p>
 */
public final class LpModelDump {

    private static final int MAGIC = 0x4C504D31; // "LPM1"
    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final SimpleDateFormat TS = new SimpleDateFormat("yyyyMMdd-HHmmss");

    private LpModelDump() {
    }

    /**
     * 把模型转储到 {@code <workdir>/lp-dumps/},返回文件;IO 失败静默返回 null
     * (求解路径不可因诊断写盘失败而崩).
     */
    public static File dump(LpModel model, String reason) {
        try {
            File dir = new File("lp-dumps");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                return null;
            }
            File file = new File(dir, TS.format(new Date()) + "-" + SEQ.incrementAndGet() + ".lpm");
            try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
                out.writeInt(MAGIC);
                out.writeUTF(reason == null ? "" : reason);
                writeModel(out, model);
            }
            return file;
        } catch (Throwable t) {
            return null;
        }
    }

    public static void writeModel(DataOutputStream out, LpModel model) throws IOException {
        out.writeInt(model.a.rows);
        out.writeInt(model.a.cols);
        int nnz = model.a.rowIdx.length;
        out.writeInt(nnz);
        for (int v : model.a.colPtr) {
            out.writeInt(v);
        }
        for (int v : model.a.rowIdx) {
            out.writeInt(v);
        }
        for (double v : model.a.values) {
            out.writeDouble(v);
        }
        writeDoubles(out, model.b);
        writeDoubles(out, model.cost);
        writeDoubles(out, model.lower);
        writeDoubles(out, model.upper);
    }

    public static LpModel read(File file) throws IOException {
        try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
            if (in.readInt() != MAGIC) {
                throw new IOException("非 LP 模型转储文件: " + file);
            }
            in.readUTF(); // reason
            int rows = in.readInt();
            int cols = in.readInt();
            int nnz = in.readInt();
            int[] colPtr = new int[cols + 1];
            for (int i = 0; i <= cols; i++) {
                colPtr[i] = in.readInt();
            }
            int[] rowIdx = new int[nnz];
            for (int i = 0; i < nnz; i++) {
                rowIdx[i] = in.readInt();
            }
            double[] values = new double[nnz];
            for (int i = 0; i < nnz; i++) {
                values[i] = in.readDouble();
            }
            SparseMatrix a = SparseMatrix.fromColumns(rows, columnsOf(colPtr, rowIdx, values, cols));
            double[] b = readDoubles(in);
            double[] cost = readDoubles(in);
            double[] lower = readDoubles(in);
            double[] upper = readDoubles(in);
            return new LpModel(a, b, cost, lower, upper);
        }
    }

    /** CSC 三元组还原为逐列映射(fromColumns 公开构造入口). */
    private static java.util.List<java.util.Map<Integer, Double>> columnsOf(int[] colPtr,
            int[] rowIdx, double[] values, int cols) {
        java.util.List<java.util.Map<Integer, Double>> columns = new java.util.ArrayList<>(cols);
        for (int j = 0; j < cols; j++) {
            java.util.Map<Integer, Double> col = new java.util.HashMap<>();
            for (int p = colPtr[j]; p < colPtr[j + 1]; p++) {
                col.put(rowIdx[p], values[p]);
            }
            columns.add(col);
        }
        return columns;
    }

    private static void writeDoubles(DataOutputStream out, double[] arr) throws IOException {
        out.writeInt(arr.length);
        for (double v : arr) {
            out.writeDouble(v);
        }
    }

    private static double[] readDoubles(DataInputStream in) throws IOException {
        double[] arr = new double[in.readInt()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = in.readDouble();
        }
        return arr;
    }
}
