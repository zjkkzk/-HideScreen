package ac.no.screenshot;

import android.view.View;
import android.view.WindowManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 通用防截屏模块
 * - 保护 WindowManager 添加的窗口
 * - 软件截图/录屏等为透明
 * - 极致性能，资源安全，缓存失效自愈
 * - 修复低级反射错误，增强异常边界处理
 */
public class MainHook implements IXposedHookLoadPackage {

    private final Set<View> protectedViews =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private final Set<Object> secureApplied =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private Field fView, fWindowAttributes, fSurfaceControl, fLeash, fSurfaceControlLocked, fSurface;
    private volatile Field validSurfaceField;
    private Method mScIsValid, mTransactionApply, mTransactionClose;
    private Method mSetSkipScreenshot, mSetSkipScreenshotLegacy, mSetSecure;
    private Constructor<?> consTransaction;
    private Class<?> scClass;  // 用于类型安全判断

    private volatile boolean cacheReady;
    private final Object cacheLock = new Object();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        String pkg = lpparam.packageName;
        if (pkg == null || pkg.startsWith("android") || pkg.startsWith("com.android.systemui")) return;

        ClassLoader cl = lpparam.classLoader;
        try {
            Class<?> vri = XposedHelpers.findClass("android.view.ViewRootImpl", cl);
            ensureCache(vri, vri.getClassLoader());
        } catch (Throwable ignored) {}

        hookWindowManagerGlobal(cl);
        hookViewRootImpl(cl);
    }

    private void hookWindowManagerGlobal(ClassLoader cl) {
        try {
            Class<?> wmg = XposedHelpers.findClass("android.view.WindowManagerGlobal", cl);
            XposedBridge.hookAllMethods(wmg, "addView", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args.length > 0 && param.args[0] instanceof View) {
                        protectedViews.add((View) param.args[0]);
                    }
                }
            });
        } catch (Throwable ignored) {}
    }

    private void hookViewRootImpl(ClassLoader cl) {
        try {
            Class<?> vri = XposedHelpers.findClass("android.view.ViewRootImpl", cl);
            ensureCache(vri, vri.getClassLoader());

            XposedBridge.hookAllMethods(vri, "setView", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    removeDim(param.thisObject);
                    applySecure(param.thisObject);
                }
            });

            XposedBridge.hookAllMethods(vri, "relayoutWindow", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    removeDim(param.thisObject);
                }
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    secureApplied.remove(param.thisObject);
                    applySecure(param.thisObject);
                }
            });

            XposedBridge.hookAllMethods(vri, "performTraversals", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    applySecure(param.thisObject);
                }
            });
        } catch (Throwable ignored) {}
    }

    private void ensureCache(Class<?> vriClass, ClassLoader systemCl) {
        if (cacheReady) return;
        synchronized (cacheLock) {
            if (cacheReady) return;

            // ViewRootImpl 字段
            try { fView = vriClass.getDeclaredField("mView"); fView.setAccessible(true); } catch (Throwable ignored) {}
            try { fWindowAttributes = vriClass.getDeclaredField("mWindowAttributes"); fWindowAttributes.setAccessible(true); } catch (Throwable ignored) {}
            try { fSurfaceControl = vriClass.getDeclaredField("mSurfaceControl"); fSurfaceControl.setAccessible(true); } catch (Throwable ignored) {}
            try { fLeash = vriClass.getDeclaredField("mLeash"); fLeash.setAccessible(true); } catch (Throwable ignored) {}
            try { fSurfaceControlLocked = vriClass.getDeclaredField("mSurfaceControlLocked"); fSurfaceControlLocked.setAccessible(true); } catch (Throwable ignored) {}
            try { fSurface = vriClass.getDeclaredField("mSurface"); fSurface.setAccessible(true); } catch (Throwable ignored) {}

            // SurfaceControl 及相关事务方法
            try {
                scClass = Class.forName("android.view.SurfaceControl", false, systemCl);
                Class<?> txnClass = Class.forName("android.view.SurfaceControl$Transaction", false, systemCl);
                mScIsValid = scClass.getDeclaredMethod("isValid");
                mScIsValid.setAccessible(true);
                consTransaction = txnClass.getDeclaredConstructor();
                consTransaction.setAccessible(true);
                mTransactionApply = txnClass.getDeclaredMethod("apply");
                mTransactionApply.setAccessible(true);
                mTransactionClose = txnClass.getDeclaredMethod("close");
                mTransactionClose.setAccessible(true);

                // 安全截图标记方法，按兼容性降级
                try { mSetSkipScreenshot = txnClass.getDeclaredMethod("setSkipScreenshot", scClass, boolean.class); mSetSkipScreenshot.setAccessible(true); } catch (Throwable ignored) {}
                try { mSetSkipScreenshotLegacy = txnClass.getDeclaredMethod("setSkipScreenshot", boolean.class); mSetSkipScreenshotLegacy.setAccessible(true); } catch (Throwable ignored) {}
                try { mSetSecure = txnClass.getDeclaredMethod("setSecure", scClass, boolean.class); mSetSecure.setAccessible(true); } catch (Throwable ignored) {}
            } catch (Throwable ignored) {
                // 极端情况：连 SurfaceControl 类都加载失败，保证不会后续 NPE
            }

            cacheReady = true;
        }
    }

    private void removeDim(Object vri) {
        if (fWindowAttributes == null) return;
        try {
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) fWindowAttributes.get(vri);
            if (lp != null && (lp.flags & WindowManager.LayoutParams.FLAG_DIM_BEHIND) != 0) {
                lp.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
                lp.dimAmount = 0f;
            }
        } catch (Throwable ignored) {}
    }

    private void applySecure(Object vri) {
        if (!cacheReady) return;
        if (secureApplied.contains(vri)) return;

        View view = null;
        try { if (fView != null) view = (View) fView.get(vri); } catch (Throwable ignored) {}
        if (view == null || !protectedViews.contains(view)) return;

        Object sc = findValidSurface(vri);
        if (sc == null) {
            // 当前无有效 SurfaceControl，清除记录以便后续重试
            secureApplied.remove(vri);
            return;
        }

        Object txn = null;
        try {
            txn = consTransaction.newInstance();
            boolean applied = false;

            if (mSetSkipScreenshot != null) {
                try { mSetSkipScreenshot.invoke(txn, sc, true); applied = true; } catch (Throwable ignored) {}
            }
            if (!applied && mSetSkipScreenshotLegacy != null) {
                try { mSetSkipScreenshotLegacy.invoke(txn, true); applied = true; } catch (Throwable ignored) {}
            }
            if (!applied && mSetSecure != null) {
                try { mSetSecure.invoke(txn, sc, true); applied = true; } catch (Throwable ignored) {}
            }

            if (applied) {
                mTransactionApply.invoke(txn);
                secureApplied.add(vri);
            }
        } catch (Throwable ignored) {
        } finally {
            if (txn != null) {
                try {
                    if (mTransactionClose != null) {
                        mTransactionClose.invoke(txn);
                    } else {
                        txn.getClass().getMethod("close").invoke(txn);
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    private Object findValidSurface(Object vri) {
        // 防止极端情况下 scClass 未初始化造成高频 NPE
        if (scClass == null) return null;

        if (validSurfaceField != null) {
            try {
                Object sc = validSurfaceField.get(vri);
                if (sc != null && scClass.isInstance(sc) && Boolean.TRUE.equals(mScIsValid.invoke(sc))) {
                    return sc;
                }
            } catch (Throwable ignored) {}
            // 缓存失效，清空并重新探测
            validSurfaceField = null;
        }

        Field[] candidates = {fSurfaceControl, fLeash, fSurfaceControlLocked, fSurface};
        for (Field f : candidates) {
            if (f == null) continue;
            try {
                Object sc = f.get(vri);
                if (sc != null && scClass.isInstance(sc) && Boolean.TRUE.equals(mScIsValid.invoke(sc))) {
                    validSurfaceField = f;
                    return sc;
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }
}
