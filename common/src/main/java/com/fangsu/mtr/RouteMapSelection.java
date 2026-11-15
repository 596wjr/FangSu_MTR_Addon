package com.fangsu.mtr;

import java.util.function.Predicate;

public interface RouteMapSelection {
    void fangsu$selectBypass(Predicate<ServiceStop.Bypass> selection);
}
