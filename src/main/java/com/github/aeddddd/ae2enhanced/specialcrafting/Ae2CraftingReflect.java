package com.github.aeddddd.ae2enhanced.specialcrafting;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingJob;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.MECraftingInventory;
import it.unimi.dsi.fastutil.objects.Object2LongArrayMap;

/**
 * AE2 合成计算内部成员的反射桥（1.12.2 版,对应 1.20.1 的 Ae2CraftingReflect）.
 * <p>1.12.2 的 {@code CraftingTreeNode.request} / {@code CraftingTreeProcess.request} /
 * {@code dive} 以及 {@code CraftingJob} 的多数成员均为包私有或私有,而本包不在
 * {@code appeng.crafting} 下,统一经本桥访问。所有成员名在初始化时一次性解析并校验,
 * AE2 升级导致签名变化时在首次调用即抛出明确异常（路由层捕获后回落原生行为）.</p>
 */
public final class Ae2CraftingReflect {

    private static final Field JOB_ORIGINAL;
    private static final Field JOB_CC;
    private static final Field JOB_ACTION_SRC;
    private static final Field JOB_AVAILABLE_CHECK;
    private static final Field JOB_SIMULATE;
    private static final Method JOB_SET_TREE;
    private static final Method JOB_HANDLE_PAUSING;
    private static final Method JOB_FINISH;
    private static final Method JOB_GET_WORLD;
    private static final Method NODE_REQUEST;
    private static final Method NODE_DIVE;
    private static final Field NODE_NODES;
    private static final Field NODE_MISSING;
    private static final Method PROCESS_REQUEST;
    private static final Field NODE_USED;
    private static final Field NODE_WHAT;
    private static final Field PROCESS_NODES;
    private static final Field PROCESS_CRAFTS;
    private static final Field PROCESS_DETAILS;
    private static final Field PROCESS_PARENT;
    private static final Field NODE_BYTES;
    private static final Method NODE_SET_SIMULATE;
    private static final Method INV_IGNORE;
    private static final Method PROCESS_ADD_PROCESS;
    private static final Field NODE_PARENT;
    private static final Field NODE_EMITTED;
    private static final Method NODE_ADD_NODE;
    private static final Field PROCESS_CONTAINERS;
    private static final Method PROCESS_ADD_CONTAINERS;
    private static final Method JOB_CHECK_USE;
    private static final Method JOB_REFUND;
    private static final Method NODE_GET_SLOT;

    static {
        try {
            JOB_ORIGINAL = CraftingJob.class.getDeclaredField("original");
            JOB_ORIGINAL.setAccessible(true);
            JOB_CC = CraftingJob.class.getDeclaredField("cc");
            JOB_CC.setAccessible(true);
            JOB_ACTION_SRC = CraftingJob.class.getDeclaredField("actionSrc");
            JOB_ACTION_SRC.setAccessible(true);
            JOB_AVAILABLE_CHECK = CraftingJob.class.getDeclaredField("availableCheck");
            JOB_AVAILABLE_CHECK.setAccessible(true);
            JOB_SIMULATE = CraftingJob.class.getDeclaredField("simulate");
            JOB_SIMULATE.setAccessible(true);
            JOB_SET_TREE = CraftingJob.class.getDeclaredMethod("setTree", CraftingTreeNode.class);
            JOB_SET_TREE.setAccessible(true);
            JOB_HANDLE_PAUSING = CraftingJob.class.getDeclaredMethod("handlePausing");
            JOB_HANDLE_PAUSING.setAccessible(true);
            JOB_FINISH = CraftingJob.class.getDeclaredMethod("finish");
            JOB_FINISH.setAccessible(true);
            JOB_GET_WORLD = CraftingJob.class.getDeclaredMethod("getWorld");
            JOB_GET_WORLD.setAccessible(true);
            NODE_REQUEST = CraftingTreeNode.class.getDeclaredMethod("request",
                    MECraftingInventory.class, long.class, IActionSource.class);
            NODE_REQUEST.setAccessible(true);
            NODE_DIVE = CraftingTreeNode.class.getDeclaredMethod("dive", CraftingJob.class);
            NODE_DIVE.setAccessible(true);
            NODE_NODES = CraftingTreeNode.class.getDeclaredField("nodes");
            NODE_NODES.setAccessible(true);
            NODE_MISSING = CraftingTreeNode.class.getDeclaredField("missing");
            NODE_MISSING.setAccessible(true);
            PROCESS_REQUEST = CraftingTreeProcess.class.getDeclaredMethod("request",
                    MECraftingInventory.class, long.class, IActionSource.class);
            PROCESS_REQUEST.setAccessible(true);
            NODE_USED = CraftingTreeNode.class.getDeclaredField("used");
            NODE_USED.setAccessible(true);
            PROCESS_NODES = CraftingTreeProcess.class.getDeclaredField("nodes");
            PROCESS_NODES.setAccessible(true);
            PROCESS_CRAFTS = CraftingTreeProcess.class.getDeclaredField("crafts");
            PROCESS_CRAFTS.setAccessible(true);
            PROCESS_DETAILS = CraftingTreeProcess.class.getDeclaredField("details");
            PROCESS_DETAILS.setAccessible(true);
            NODE_WHAT = CraftingTreeNode.class.getDeclaredField("what");
            NODE_WHAT.setAccessible(true);
            PROCESS_PARENT = CraftingTreeProcess.class.getDeclaredField("parent");
            PROCESS_PARENT.setAccessible(true);
            NODE_BYTES = CraftingTreeNode.class.getDeclaredField("bytes");
            NODE_BYTES.setAccessible(true);
            NODE_SET_SIMULATE = CraftingTreeNode.class.getDeclaredMethod("setSimulate");
            NODE_SET_SIMULATE.setAccessible(true);
            INV_IGNORE = MECraftingInventory.class.getDeclaredMethod("ignore", IAEItemStack.class);
            INV_IGNORE.setAccessible(true);
            PROCESS_ADD_PROCESS = CraftingTreeProcess.class.getDeclaredMethod("addProcess");
            PROCESS_ADD_PROCESS.setAccessible(true);
            NODE_PARENT = CraftingTreeNode.class.getDeclaredField("parent");
            NODE_PARENT.setAccessible(true);
            NODE_EMITTED = CraftingTreeNode.class.getDeclaredField("howManyEmitted");
            NODE_EMITTED.setAccessible(true);
            NODE_ADD_NODE = CraftingTreeNode.class.getDeclaredMethod("addNode");
            NODE_ADD_NODE.setAccessible(true);
            PROCESS_CONTAINERS = CraftingTreeProcess.class.getDeclaredField("containers");
            PROCESS_CONTAINERS.setAccessible(true);
            PROCESS_ADD_CONTAINERS = CraftingTreeProcess.class.getDeclaredMethod("addContainers",
                    IAEItemStack.class);
            PROCESS_ADD_CONTAINERS.setAccessible(true);
            JOB_CHECK_USE = CraftingJob.class.getDeclaredMethod("checkUse", IAEItemStack.class);
            JOB_CHECK_USE.setAccessible(true);
            JOB_REFUND = CraftingJob.class.getDeclaredMethod("refund", IAEItemStack.class);
            JOB_REFUND.setAccessible(true);
            NODE_GET_SLOT = CraftingTreeNode.class.getDeclaredMethod("getSlot");
            NODE_GET_SLOT.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private Ae2CraftingReflect() {
    }

    public static MECraftingInventory getOriginal(CraftingJob job) {
        try {
            return (MECraftingInventory) JOB_ORIGINAL.get(job);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("访问 CraftingJob.original 失败", e);
        }
    }

    public static ICraftingGrid getCc(CraftingJob job) {
        try {
            return (ICraftingGrid) JOB_CC.get(job);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("访问 CraftingJob.cc 失败", e);
        }
    }

    public static IActionSource getActionSrc(CraftingJob job) {
        try {
            return (IActionSource) JOB_ACTION_SRC.get(job);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("访问 CraftingJob.actionSrc 失败", e);
        }
    }

    public static net.minecraft.world.World getWorld(CraftingJob job) {
        try {
            return (net.minecraft.world.World) JOB_GET_WORLD.invoke(job);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("调用 CraftingJob.getWorld 失败", e);
        }
    }

    public static void setAvailableCheck(CraftingJob job, MECraftingInventory inv) {
        try {
            JOB_AVAILABLE_CHECK.set(job, inv);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("写入 CraftingJob.availableCheck 失败", e);
        }
    }

    public static void setSimulate(CraftingJob job, boolean simulate) {
        try {
            JOB_SIMULATE.setBoolean(job, simulate);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("写入 CraftingJob.simulate 失败", e);
        }
    }

    public static void setTree(CraftingJob job, CraftingTreeNode tree) {
        try {
            JOB_SET_TREE.invoke(job, tree);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("调用 CraftingJob.setTree 失败", e);
        }
    }

    public static void handlePausing(CraftingJob job) throws InterruptedException {
        try {
            JOB_HANDLE_PAUSING.invoke(job);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof InterruptedException) {
                throw (InterruptedException) e.getCause();
            }
            throw new IllegalStateException("调用 CraftingJob.handlePausing 失败", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("调用 CraftingJob.handlePausing 失败", e);
        }
    }

    public static void finish(CraftingJob job) {
        try {
            JOB_FINISH.invoke(job);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("调用 CraftingJob.finish 失败", e);
        }
    }

    public static IAEItemStack nodeRequest(CraftingTreeNode node, MECraftingInventory inv, long amount,
            IActionSource src) throws CraftBranchFailure, InterruptedException {
        try {
            return (IAEItemStack) NODE_REQUEST.invoke(node, inv, amount, src);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof CraftBranchFailure) {
                throw (CraftBranchFailure) e.getCause();
            }
            if (e.getCause() instanceof InterruptedException) {
                throw (InterruptedException) e.getCause();
            }
            throw new IllegalStateException("CraftingTreeNode.request 执行异常", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("调用 CraftingTreeNode.request 失败", e);
        }
    }

    public static void nodeDive(CraftingTreeNode node, CraftingJob job) {
        try {
            NODE_DIVE.invoke(node, job);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("调用 CraftingTreeNode.dive 失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static void addProcessToNode(CraftingTreeNode node, CraftingTreeProcess process) {
        try {
            ((ArrayList<CraftingTreeProcess>) NODE_NODES.get(node)).add(process);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("写入 CraftingTreeNode.nodes 失败", e);
        }
    }

    public static void setNodeMissing(CraftingTreeNode node, long missing) {
        try {
            NODE_MISSING.setLong(node, missing);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("写入 CraftingTreeNode.missing 失败", e);
        }
    }

    public static long getNodeMissing(CraftingTreeNode node) {
        try {
            return NODE_MISSING.getLong(node);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("读取 CraftingTreeNode.missing 失败", e);
        }
    }

    public static void treeProcessRequest(CraftingTreeProcess pro, MECraftingInventory inv, long times,
            IActionSource src) throws CraftBranchFailure, InterruptedException {
        try {
            PROCESS_REQUEST.invoke(pro, inv, times, src);
        } catch (java.lang.reflect.InvocationTargetException e) {
            if (e.getCause() instanceof CraftBranchFailure) {
                throw (CraftBranchFailure) e.getCause();
            }
            if (e.getCause() instanceof InterruptedException) {
                throw (InterruptedException) e.getCause();
            }
            throw new IllegalStateException("CraftingTreeProcess.request 执行异常", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("调用 CraftingTreeProcess.request 失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static IItemList<IAEItemStack> getNodeUsed(CraftingTreeNode node) {
        try {
            return (IItemList<IAEItemStack>) NODE_USED.get(node);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("访问 CraftingTreeNode.used 失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static ArrayList<CraftingTreeProcess> getNodeProcesses(CraftingTreeNode node) {
        try {
            return (ArrayList<CraftingTreeProcess>) NODE_NODES.get(node);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("访问 CraftingTreeNode.nodes 失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Object2LongArrayMap<CraftingTreeNode> getProcessNodes(CraftingTreeProcess pro) {
        try {
            return (Object2LongArrayMap<CraftingTreeNode>) PROCESS_NODES.get(pro);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("访问 CraftingTreeProcess.nodes 失败", e);
        }
    }

    public static long getProcessCrafts(CraftingTreeProcess pro) {
        try {
            return PROCESS_CRAFTS.getLong(pro);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("访问 CraftingTreeProcess.crafts 失败", e);
        }
    }

    public static ICraftingPatternDetails getProcessDetails(CraftingTreeProcess pro) {
        try {
            return (ICraftingPatternDetails) PROCESS_DETAILS.get(pro);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("访问 CraftingTreeProcess.details 失败", e);
        }
    }

    public static IAEItemStack getNodeWhat(CraftingTreeNode node) {
        try {
            return (IAEItemStack) NODE_WHAT.get(node);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("访问 CraftingTreeNode.what 失败", e);
        }
    }

    public static void setProcessParent(CraftingTreeProcess pro, CraftingTreeNode parent) {
        try {
            PROCESS_PARENT.set(pro, parent);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("写入 CraftingTreeProcess.parent 失败", e);
        }
    }

    public static void setProcessCrafts(CraftingTreeProcess pro, long crafts) {
        try {
            PROCESS_CRAFTS.setLong(pro, crafts);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("写入 CraftingTreeProcess.crafts 失败", e);
        }
    }

    /** CraftingTreeNode.bytes 为 int 字段（原生如此）,超出时钳制. */
    public static void setNodeBytes(CraftingTreeNode node, long bytes) {
        try {
            NODE_BYTES.setInt(node, bytes > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) bytes);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("写入 CraftingTreeNode.bytes 失败", e);
        }
    }

    public static void treeSetSimulate(CraftingTreeNode node) {
        try {
            NODE_SET_SIMULATE.invoke(node);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("调用 CraftingTreeNode.setSimulate 失败", e);
        }
    }

    /** MECraftingInventory.ignore(包私有):把某物在模拟库存中清零,防止"用存量合成自己". */
    public static void invIgnore(MECraftingInventory inv, IAEItemStack what) {
        try {
            INV_IGNORE.invoke(inv, what);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("调用 MECraftingInventory.ignore 失败", e);
        }
    }

    /**
     * CraftingTreeProcess.addProcess(包私有,惰性):构造函数不建输入子节点,
     * 仅 request() 时惰性调用——需要提前访问子节点(如 DAG 物化)时显式触发.
     * <p>注意:子节点的 wantedSize 会按当前 availableCheck 库存被钳制/拆分,
     * 各项 value 之和仍等于单次消耗的 per-craft 数量.</p>
     */
    public static void processAddProcess(CraftingTreeProcess pro) {
        try {
            PROCESS_ADD_PROCESS.invoke(pro);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("调用 CraftingTreeProcess.addProcess 失败", e);
        }
    }

    /** CraftingTreeNode.parent(包私有):该输入节点所属的样板 process,根请求节点为 null. */
    @javax.annotation.Nullable
    public static CraftingTreeProcess getNodeParent(CraftingTreeNode node) {
        try {
            return (CraftingTreeProcess) NODE_PARENT.get(node);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("访问 CraftingTreeNode.parent 失败", e);
        }
    }

    /** CraftingTreeNode.howManyEmitted(私有):发射台免费满足的数量,populatePlan/setJob 读取. */
    public static void setNodeEmitted(CraftingTreeNode node, long emitted) {
        try {
            NODE_EMITTED.setLong(node, emitted);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("写入 CraftingTreeNode.howManyEmitted 失败", e);
        }
    }

    /** CraftingTreeNode.addNode(包私有,惰性):按 notRecursive 过滤构建候选 process 列表. */
    public static void nodeAddNode(CraftingTreeNode node) {
        try {
            NODE_ADD_NODE.invoke(node);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("调用 CraftingTreeNode.addNode 失败", e);
        }
    }

    /** CraftingTreeNode.bytes 为 int 字段(原生如此),读取当前值. */
    public static long getNodeBytes(CraftingTreeNode node) {
        try {
            return NODE_BYTES.getInt(node);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("访问 CraftingTreeNode.bytes 失败", e);
        }
    }

    /**
     * CraftingTreeProcess.containers(私有)取出并清空:复刻原生 request 尾部
     * "注入累积容器物并置 null"的语义.
     */
    @SuppressWarnings("unchecked")
    public static ArrayList<IAEItemStack> processDrainContainers(CraftingTreeProcess pro) {
        try {
            ArrayList<IAEItemStack> containers = (ArrayList<IAEItemStack>) PROCESS_CONTAINERS.get(pro);
            PROCESS_CONTAINERS.set(pro, null);
            return containers;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("访问 CraftingTreeProcess.containers 失败", e);
        }
    }

    /** CraftingTreeProcess.addContainers(包私有):子请求提取到带容器物时回记容器. */
    public static void processAddContainer(CraftingTreeProcess pro, IAEItemStack container) {
        try {
            PROCESS_ADD_CONTAINERS.invoke(pro, container);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("调用 CraftingTreeProcess.addContainers 失败", e);
        }
    }

    /**
     * CraftingJob.checkUse(包私有):从 availableCheck 实取并返回记账用堆叠
     *(不可用时返回 null,调用方不记 used).
     */
    @javax.annotation.Nullable
    public static IAEItemStack jobCheckUse(CraftingJob job, IAEItemStack available) {
        try {
            return (IAEItemStack) JOB_CHECK_USE.invoke(job, available);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("调用 CraftingJob.checkUse 失败", e);
        }
    }

    /** CraftingJob.refund(包私有):把堆叠退回 availableCheck(分支失败退款). */
    public static void jobRefund(CraftingJob job, IAEItemStack stack) {
        try {
            JOB_REFUND.invoke(job, stack);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("调用 CraftingJob.refund 失败", e);
        }
    }

    /** CraftingTreeNode.getSlot(包私有):该输入节点在父样板中的槽位(根节点为 -1). */
    public static int getNodeSlot(CraftingTreeNode node) {
        try {
            return (Integer) NODE_GET_SLOT.invoke(node);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("调用 CraftingTreeNode.getSlot 失败", e);
        }
    }
}
