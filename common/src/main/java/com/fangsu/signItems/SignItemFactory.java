package com.fangsu.signItems;

import java.util.HashMap;
import java.util.Map;

public final class SignItemFactory {

    private static final Map<String, SignItem> REGISTRY = new HashMap<>();

    private SignItemFactory() {
    }

    public static void register(SignItem item) {
        REGISTRY.put(item.getType(), item);
    }

    public static SignItem get(String type) {
        SignItem item = REGISTRY.get(type);
        if (item == null) {
            throw new IllegalArgumentException(
                    "Unknown SignItem type: " + type
            );
        }
        return item;
    }

    public static boolean has(String type) {
        return REGISTRY.containsKey(type);
    }
}
