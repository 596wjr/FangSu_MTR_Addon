/*
This file contains code from mtr-nte

Original project:https://github.com/zbx1425/mtr-nte

The original license (MIT) is attached below, and applies to all code contained in this directory/package.

The code has been modified for compatibility purposes only.

MIT License

Copyright (c) 2022-present Zbx1425

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

*/

package com.fangsu.render;

import com.fangsu.render.sowcer.ContextCapability;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;

public final class ShadersModHandler {

    private static InternalHandler internalHandler;

    public static void init() {
        internalHandler = new InternalHandler() {
        };

        try {
            Class<?> ignored = Class.forName("optifine.Installer");
            internalHandler = new Optifine();
        } catch (Exception ignored) {
        }

        try {
            Class<?> ignored = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            internalHandler = new Oculus();
        } catch (Exception ignored) {
        }
    }

    public static boolean canInstance() {
        return canUseCustomShader() && ContextCapability.supportVertexAttribDivisor;
    }

    public static boolean canUseCustomShader() {
        return !internalHandler.isShaderPackInUse() && !ContextCapability.isGL4ES;
    }

    public static boolean canDrawWithBuffer() {
        return !(internalHandler instanceof Optifine) || canUseCustomShader();
    }

    private interface InternalHandler {
        default boolean isShaderPackInUse() {
            return false;
        }
    }

    private static class Oculus implements InternalHandler {
        private final BooleanSupplier shadersEnabledSupplier;

        Oculus() {
            shadersEnabledSupplier = createShadersEnabledSupplier();
        }

        @Override
        public boolean isShaderPackInUse() {
            return shadersEnabledSupplier.getAsBoolean();
        }

        private static BooleanSupplier createShadersEnabledSupplier() {
            try {
                Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                Object irisApiInstance = irisApiClass.getMethod("getInstance").invoke(null);
                Method fnIsShaderPackInUse = irisApiClass.getMethod("isShaderPackInUse");
                return () -> {
                    try {
                        return (Boolean) fnIsShaderPackInUse.invoke(irisApiInstance);
                    } catch (Exception ignored) {
                        return false;
                    }
                };
            } catch (Exception ignored) {
                return () -> false;
            }
        }
    }

    private static class Optifine implements InternalHandler {
        private final BooleanSupplier shadersEnabledSupplier;

        Optifine() {
            shadersEnabledSupplier = createShadersEnabledSupplier();
        }

        @Override
        public boolean isShaderPackInUse() {
            return shadersEnabledSupplier.getAsBoolean();
        }

        private static BooleanSupplier createShadersEnabledSupplier() {
            try {
                Class<?> ofShaders = Class.forName("net.optifine.shaders.Shaders");
                Field field = ofShaders.getDeclaredField("activeProgramID");
                // field.setAccessible(true);
                return () -> {
                    try {
                        return (int) field.get(null) != 0;
                    } catch (IllegalAccessException ignored) {
                        return false;
                    }
                };
            } catch (Exception ignored) {
                return () -> false;
            }
        }
    }
}