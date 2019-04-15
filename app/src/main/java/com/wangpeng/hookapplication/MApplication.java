package com.wangpeng.hookapplication;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static com.wangpeng.hookapplication.MainActivity.TARGET_INTENT;

public class MApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        hookActivityThreadInstrumentation();
    }

    private void hookActivityThreadInstrumentation() {
        try {
            Class<?> activityThreadClazz = Class.forName("android.app.ActivityThread");
            Field currActivityThreadField = activityThreadClazz.getDeclaredField("sCurrentActivityThread");
            currActivityThreadField.setAccessible(true);
            Object currActivityThreadObj = currActivityThreadField.get(null);
            Field mInstrumentationField = activityThreadClazz.getDeclaredField("mInstrumentation");
            mInstrumentationField.setAccessible(true);
            Instrumentation instrumentation = (Instrumentation) mInstrumentationField.get(currActivityThreadObj);
            //创建代理对象InstrumentationProxy
            InstrumentationProxy proxy = new InstrumentationProxy(instrumentation, getPackageManager());
            mInstrumentationField.set(currActivityThreadObj, proxy);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    class InstrumentationProxy extends Instrumentation {
        private Instrumentation mInstrumentation;
        private PackageManager mPackageManager;

        InstrumentationProxy(Instrumentation mInstrumentation, PackageManager mPackageManager) {
            this.mInstrumentation = mInstrumentation;
            this.mPackageManager = mPackageManager;
        }

        public ActivityResult execStartActivity(Context who, IBinder contextThread, IBinder token, Activity target,
                                                Intent intent, int requestCode, Bundle options) {
            // 查找要启动的Activity是否在注册表中
            List<ResolveInfo> mResolveInfoLists = mPackageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL);
            if (mResolveInfoLists == null || mResolveInfoLists.isEmpty()) {
                intent.putExtra(TARGET_INTENT, intent.getComponent().getClassName());
                intent.setClassName(who, StubActivity.class.getName());
            }
            try {
                Method execStartActivity = Instrumentation.class.getDeclaredMethod("execStartActivity", Context.class,
                        IBinder.class, IBinder.class, Activity.class,
                        Intent.class, int.class, Bundle.class);
                return (ActivityResult) execStartActivity.invoke(mInstrumentation, who, contextThread, token, target, intent, requestCode, options);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
            return null;
        }

        public Activity newActivity(ClassLoader classLoader, String className, Intent intent) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
            String intentName = intent.getStringExtra(TARGET_INTENT);
            if (!TextUtils.isEmpty(intentName)) {
                Log.i("newActivity", "intentName:" + intentName);
                return super.newActivity(classLoader, intentName, intent);
            }
            return super.newActivity(classLoader, className, intent);
        }
    }
}
