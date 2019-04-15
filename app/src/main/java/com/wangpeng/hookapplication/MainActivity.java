package com.wangpeng.hookapplication;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    static final String TARGET_INTENT = "target_intent";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
//        hookActivityManager();
//        hookHandler();
//        hookActivityThreadInstrumentation();
    }


    public void startTarget(View view) {
        Intent target = new Intent(this, TargetActivity.class);
        startActivity(target);
    }


    public void hookActivityManager() {
        try {
            Object mIActivityManagerObj = null;
            if (Build.VERSION.SDK_INT >= 26) {
                Class<?> activityManagerClazz = Class.forName("android.app.ActivityManager");
                Field IActivityManagerSingletonField = activityManagerClazz.getDeclaredField("IActivityManagerSingleton");
                IActivityManagerSingletonField.setAccessible(true);
                mIActivityManagerObj = IActivityManagerSingletonField.get(null);
            } else {
                Class<?> activityManagerClazz = Class.forName("android.app.ActivityManagerNative");
                Field defaultField = activityManagerClazz.getDeclaredField("gDefault");
                defaultField.setAccessible(true);
                mIActivityManagerObj = defaultField.get(null);
            }
            Class<?> singletonClass = Class.forName("android.util.Singleton");
            Field mInstanceField = singletonClass.getDeclaredField("mInstance");
            mInstanceField.setAccessible(true);
            // ActivityManagerNative中的IActivityManager对象
            Object rawIActivityManager = mInstanceField.get(mIActivityManagerObj);
            // 获取IActivityManager代理对象
            Class<?> iActivityManagerInterface = Class.forName("android.app.IActivityManager");
            Object proxy = Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader()
                    , new Class<?>[]{iActivityManagerInterface}, new AsmHookBinderInvocationHandler(rawIActivityManager));
            // 将IActivityManager代理对象赋值给Singleton中mInstance字段
            mInstanceField.set(mIActivityManagerObj, proxy);
        } catch (
                ClassNotFoundException e) {
            e.printStackTrace();
        } catch (
                NoSuchFieldException e) {
            e.printStackTrace();
        } catch (
                IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Hook ActivityThread中Handler成员变量mH
     */
    public void hookHandler() {
        try {
            Class<?> activityThreadClazz = Class.forName("android.app.ActivityThread");
            Field currActivityThreadField = activityThreadClazz.getDeclaredField("sCurrentActivityThread");
            currActivityThreadField.setAccessible(true);
            // 获取ActivityThread主线程对象
            Object currentActivityThread = currActivityThreadField.get(null);
            // 再去获取Handler
            Field mHField = activityThreadClazz.getDeclaredField("mH");
            mHField.setAccessible(true);
            Handler mHandler = (Handler) mHField.get(currentActivityThread);
            Field mCallBackField = Handler.class.getDeclaredField("mCallback");
            mCallBackField.setAccessible(true);
            mCallBackField.set(mHandler, new HCallBack(mHandler));
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    class AsmHookBinderInvocationHandler implements InvocationHandler {
        private static final String TAG = "AsmHookHandler";
        private Object mBase;

        AsmHookBinderInvocationHandler(Object base) {
            this.mBase = base;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Log.d(TAG, "hey, baby; you are hooked!!");
            Log.d(TAG, "method:" + method.getName() + " called with args:" + Arrays.toString(args));
            Intent raw = null;
            int index = 0;
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Intent) {
                    index = i;
                    break;
                }
            }
            Log.d(TAG, "index:" + index);
            if (index > 0) {
                raw = (Intent) args[index];
                Intent targetIntent = new Intent();
                String targetPackage = "com.wangpeng.hookapplication";
                // 启动占坑Activity,将真正的TargetActivity存起来
                ComponentName componentName = new ComponentName(targetPackage, StubActivity.class.getName());
                targetIntent.setComponent(componentName);
                targetIntent.putExtra(TARGET_INTENT, raw);
                args[index] = targetIntent;
            }
            return method.invoke(mBase, args);
        }
    }


    class HCallBack implements Handler.Callback {
        private static final int LAUNCH_ACTIVITY = 100;
        Handler mHandler;

        HCallBack(Handler handler) {
            this.mHandler = handler;
        }

        @Override
        public boolean handleMessage(Message msg) {
            if (msg.what == LAUNCH_ACTIVITY) {
                Object obj = msg.obj;
                try {
                    // 获取StubActivity（占坑）的Intent
                    Class<?> clazz = obj.getClass();
                    Field intentField = clazz.getDeclaredField("intent");
                    intentField.setAccessible(true);
                    Object mIntentObj = intentField.get(obj);
                    Intent stubIntent = (Intent) mIntentObj;
                    Log.i("HCallBack", "stubIntent:" + stubIntent.getDataString());
                    Intent targetIntent = stubIntent.getParcelableExtra(TARGET_INTENT);
                    Log.i("HCallBack", "targetIntent getComponent:" + targetIntent.getComponent());
                    // 将StubActivity（占坑）的Intent替换成刚才存入进去的TargetActivity的Intent
                    stubIntent.setComponent(targetIntent.getComponent());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            mHandler.handleMessage(msg);
            return true;
        }
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
//            InstrumentationProxy proxy = new InstrumentationProxy(instrumentation, getPackageManager());
            com.wangpeng.hookapplication.InstrumentationProxy proxy = new com.wangpeng.hookapplication.InstrumentationProxy(instrumentation);
            mInstrumentationField.set(currActivityThreadObj, proxy);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
