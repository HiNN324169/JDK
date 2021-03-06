/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */
package jdk.vm.ci.hotspot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.SpeculationLog;

public class HotSpotSpeculationLog implements SpeculationLog {
    public static final class HotSpotSpeculation extends Speculation {
        private JavaConstant encoding;

        HotSpotSpeculation(SpeculationReason reason, JavaConstant encoding) {
            super(reason);
            this.encoding = encoding;
        }

        public JavaConstant getEncoding() {
            return encoding;
        }
    }

    /** Written by the C++ code that performs deoptimization. */
    private volatile long lastFailed;

    /** All speculations that have caused a deoptimization. */
    private Set<SpeculationReason> failedSpeculations;

    /** Strong references to all reasons embedded in the current nmethod. */
    private HashMap<SpeculationReason, JavaConstant> speculations;

    private long currentSpeculationID;

    @Override
    public synchronized void collectFailedSpeculations() {
        if (lastFailed != 0) {
            if (failedSpeculations == null) {
                failedSpeculations = new HashSet<>(2);
            }
            if (speculations != null) {
                SpeculationReason lastFailedSpeculation = lookupSpeculation(this.lastFailed);
                if (lastFailedSpeculation != null) {
                    failedSpeculations.add(lastFailedSpeculation);
                }
                lastFailed = 0;
                speculations = null;
            }
        }
    }

    private SpeculationReason lookupSpeculation(long value) {
        for (Map.Entry<SpeculationReason, JavaConstant> entry : speculations.entrySet()) {
            if (value == entry.getValue().asLong()) {
                return entry.getKey();
            }
        }
        return null;
    }

    @Override
    public synchronized boolean maySpeculate(SpeculationReason reason) {
        if (failedSpeculations != null && failedSpeculations.contains(reason)) {
            return false;
        }
        return true;
    }

    @Override
    public synchronized Speculation speculate(SpeculationReason reason) {
        if (speculations == null) {
            speculations = new HashMap<>();
        }
        JavaConstant id = speculations.get(reason);
        if (id == null) {
            id = JavaConstant.forLong(++currentSpeculationID);
            speculations.put(reason, id);
        }
        return new HotSpotSpeculation(reason, id);
    }

    @Override
    public synchronized boolean hasSpeculations() {
        return speculations != null && !speculations.isEmpty();
    }

    @Override
    public synchronized Speculation lookupSpeculation(JavaConstant constant) {
        if (constant.isDefaultForKind()) {
            return NO_SPECULATION;
        }
        SpeculationReason reason = lookupSpeculation(constant.asLong());
        assert reason != null : "Speculation should have been registered";
        return new HotSpotSpeculation(reason, constant);
    }
}
