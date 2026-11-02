package com.github.aeddddd.ae2enhanced.diag.check;

import com.github.aeddddd.ae2enhanced.observation.CheckObservation;
import com.github.aeddddd.ae2enhanced.observation.ObservationCheck;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

/** 将中性业务检查投影到诊断结果模型。 */
final class ObservedSystemCheck implements SystemCheck {

    private final ObservationCheck delegate;

    ObservedSystemCheck(ObservationCheck delegate) {
        this.delegate = delegate;
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public String displayName() {
        return delegate.displayName();
    }

    @Override
    public void run(MinecraftServer server, List<CheckResult> out) {
        List<CheckObservation> observations = new ArrayList<>();
        delegate.run(server, observations);
        for (CheckObservation observation : observations) {
            CheckResult.Level level = CheckResult.Level.valueOf(observation.level.name());
            out.add(CheckResult.create(level, observation.key, observation.args));
        }
    }
}
