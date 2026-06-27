package com.github.aeddddd.ae2enhanced.centralinterface;

/**
 * 绑定目标的状态机.
 */
public enum TargetState {
    IDLE,          // 空闲,可接收新任务
    PUSHING,       // 正在推送材料
    PROCESSING,    // 机器正在处理中
    COLLECTING,    // 正在回收产物
    UNAVAILABLE    // 目标不可用(区块未加载或方块被摧毁)
}
