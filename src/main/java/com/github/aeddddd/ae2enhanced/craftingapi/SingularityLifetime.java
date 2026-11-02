package com.github.aeddddd.ae2enhanced.craftingapi;

/** 被约束奇点恢复方块时写入寿命的中性端口。 */
public interface SingularityLifetime {
    void setLifetimeTicks(int ticks);
    void setPermanent(boolean permanent);
}
