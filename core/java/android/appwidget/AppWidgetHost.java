/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.appwidget;

import java.util.ArrayList;
import java.util.HashMap;

import android.content.Context;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.widget.RemoteViews;
import android.widget.RemoteViews.OnClickHandler;

import com.android.internal.appwidget.IAppWidgetHost;
import com.android.internal.appwidget.IAppWidgetService;

/**
 * AppWidgetHost provides the interaction with the AppWidget service for apps,
 * like the home screen, that want to embed AppWidgets in their UI.
 */
public class AppWidgetHost {

    static final int HANDLE_UPDATE = 1;
    static final int HANDLE_PROVIDER_CHANGED = 2;
    static final int HANDLE_PROVIDERS_CHANGED = 3;
    static final int HANDLE_VIEW_DATA_CHANGED = 4;

    final static Object sServiceLock = new Object();
    static IAppWidgetService sService;
    private DisplayMetrics mDisplayMetrics;

    Context mContext;
    String mPackageName;
    Handler mHandler;
    int mHostId;
    Callbacks mCallbacks = new Callbacks();
    final HashMap<Integer,AppWidgetHostView> mViews = new HashMap<Integer, AppWidgetHostView>();
    private OnClickHandler mOnClickHandler;

    class Callbacks extends IAppWidgetHost.Stub {
        public void updateAppWidget(int appWidgetId, RemoteViews views, int userId) {
            if (isLocalBinder() && views != null) {
                views = views.clone();
                views.setUser(new UserHandle(userId));
            }
            Message msg = mHandler.obtainMessage(HANDLE_UPDATE, appWidgetId, userId, views);
            msg.sendToTarget();
        }

        public void providerChanged(int appWidgetId, AppWidgetProviderInfo info, int userId) {
            if (isLocalBinder() && info != null) {
                info = info.clone();
            }
            Message msg = mHandler.obtainMessage(HANDLE_PROVIDER_CHANGED,
                    appWidgetId, userId, info);
            msg.sendToTarget();
        }

        public void providersChanged(int userId) {
            Message msg = mHandler.obtainMessage(HANDLE_PROVIDERS_CHANGED, userId, 0);
            msg.sendToTarget();
        }

        public void viewDataChanged(int appWidgetId, int viewId, int userId) {
            Message msg = mHandler.obtainMessage(HANDLE_VIEW_DATA_CHANGED,
                    appWidgetId, viewId, userId);
            msg.sendToTarget();
        }
    }

    class UpdateHandler extends Handler {
        public UpdateHandler(Looper looper) {
            super(looper);
        }

        public void handleMessage(Message msg) {
            switch (msg.what) {
                case HANDLE_UPDATE: {
                    updateAppWidgetView(msg.arg1, (RemoteViews)msg.obj, msg.arg2);
                    break;
                }
                case HANDLE_PROVIDER_CHANGED: {
                    onProviderChanged(msg.arg1, (AppWidgetProviderInfo)msg.obj);
                    break;
                }
                case HANDLE_PROVIDERS_CHANGED: {
                    onProvidersChanged();
                    break;
                }
                case HANDLE_VIEW_DATA_CHANGED: {
                    viewDataChanged(msg.arg1, msg.arg2, (Integer) msg.obj);
                    break;
                }
            }
        }
    }

    public AppWidgetHost(Context context, int hostId) {
        this(context, hostId, null, context.getMainLooper());
    }

    /**
     * @hide
     */
    public AppWidgetHost(Context context, int hostId, OnClickHandler handler, Looper looper) {
        mContext = context;
        mHostId = hostId;
        mOnClickHandler = handler;
        mHandler = new UpdateHandler(looper);
        mDisplayMetrics = context.getResources().getDisplayMetrics();
        bindService();
    }


    private static void bindService() {
        synchronized (sServiceLock) {
            if (sService == null) {
                IBinder b = ServiceManager.getService(Context.APPWIDGET_SERVICE);
                sService = IAppWidgetService.Stub.asInterface(b);
            }
        }
    }

    /**
     * Start receiving onAppWidgetChanged calls for your AppWidgets.  Call this when your activity
     * becomes visible, i.e. from onStart() in your Activity.
     */
    public void startListening() {
        int[] updatedIds;
        ArrayList<RemoteViews> updatedViews = new ArrayList<RemoteViews>();

        final int userId = mContext.getUserId();
        try {
            if (mPackageName == null) {
                mPackageName = mContext.getPackageName();
            }
            updatedIds = sService.startListening(
                    mCallbacks, mPackageName, mHostId, updatedViews, userId);
        }
        catch (RemoteException e) {
            throw new RuntimeException("system server dead?", e);
        }

        final int N = updatedIds.length;
        for (int i=0; i<N; i++) {
            if (updatedViews.get(i) != null) {
                updatedViews.get(i).setUser(new UserHandle(userId));
            }
            updateAppWidgetView(updatedIds[i], updatedViews.get(i), userId);
        }
    }

    /**
     * Stop receiving onAppWidgetChanged calls for your AppWidgets.  Call this when your activity is
     * no longer visible, i.e. from onStop() in your Activity.
     */
    public void stopListening() {
        try {
            sService.stopListening(mHostId, mContext.getUserId());
        }
        catch (RemoteException e) {
            throw new RuntimeException("system server dead?", e);
        }

        // This is here because keyguard needs it since it'll be switching users after this call.
        // If it turns out other apps need to call this often, we should re-think how this works.
        clearViews();
    }

    /**
     * Get a appWidgetId for a host in the calling process.
     *
     * @return a appWidgetId
     */
    public int allocateAppWidgetId() {
        try {
            if (mPackageName == null) {
                mPackageName = mContext.getPackageName();
            }
            return sService.allocateAppWidgetId(mPackageName, mHostId, mContext.getUserId());
        }
        catch (RemoteException e) {
            throw new RuntimeException("system server dead?", e);
        }
    }

    /**
     * Get a appWidgetId for a host in the given package.
     *
     * @return a appWidgetId
     * @hide
     */
    public static int allocateAppWidgetIdForPackage(int hostId, int userId, String packageName) {
        checkCallerIsSystem();
        try {
            if (sService == null) {
                bindService();
            }
            return sService.allocateAppWidgetId(packageName, hostId, userId);
        } catch (RemoteException e) {
            throw new RuntimeException("system server dead?", e);
        }
    }

    /**
     * Gets a list of all the appWidgetIds that are bound to the current host
     *
     * @hide
     */
    public int[] getAppWidgetIds() {
        try {
            if (sService == null) {
                bindService();
            }
            return sService.getAppWidgetIdsForHost(mHostId, mContext.getUserId());
        } catch (RemoteException e) {
            throw new RuntimeException("system server dead?", e);
        }
    }

    private static void checkCallerIsSystem() {
        int uid = Process.myUid();
        if (UserHandle.getAppId(uid) == Process.SYSTEM_UID || uid == 0) {
            return;
        }
        throw new SecurityException("Disallowed call for uid " + uid);
    }

    private boolean isLocalBinder() {
        return Process.myPid() == Binder.getCallingPid();
    }

    /**
     * Stop listening to changes for this AppWidget.
     */
    public void deleteAppWidgetId(int appWidgetId) {
        synchronized (mViews) {
            mViews.remove(appWidgetId);
            try {
                sService.deleteAppWidgetId(appWidgetId, mContext.getUserId());
            }
            catch (RemoteException e) {
                throw new RuntimeException("system server dead?", e);
            }
        }
    }

    /**
     * Stop listening to changes for this AppWidget.
     * @hide
     */
    public static void deleteAppWidgetIdForSystem(int appWidgetId, int userId) {
        checkCallerIsSystem();
        try {
            if (sService == null) {
                bindService();
            }
            sService.deleteAppWidgetId(appWidgetId, userId);
        } catch (RemoteException e) {
            throw new RuntimeException("system server dead?", e);
        }
    }

    /**
     * Remove all records about this host from the AppWidget manager.
     * <ul>
     *   <li>Call this when initializing your database, as it might be because of a data wipe.</li>
     *   <li>Call this to have the AppWidget manager release all resources associated with your
     *   host.  Any future calls about this host will cause the records to be re-allocated.</li>
     * </ul>
     */
    public void deleteHost() {
        try {
            sService.deleteHost(mHostId, mContext.getUserId());
        }
        catch (RemoteException e) {
            throw new RuntimeException("system server dead?", e);
        }
    }

    /**
     * Remove all records about all hosts for your package.
     * <ul>
     *   <li>Call this when initializing your database, as it might be because of a data wipe.</li>
     *   <li>Call this to have the AppWidget manager release all resources associated with your
     *   host.  Any future calls about this host will cause the records to be re-allocated.</li>
     * </ul>
     */
    public static void deleteAllHosts() {
        deleteAllHosts(UserHandle.myUserId());
    }

    /**
     * Private method containing a userId
     * @hide
     */
    public static void deleteAllHosts(int userId) {
        try {
            sService.deleteAllHosts(userId);
        }
        catch (RemoteException e) {
            throw new RuntimeException("system server dead?", e);
        }
    }

    /**
     * Create the AppWidgetHostView for the given widget.
     * The AppWidgetHost retains a pointer to the newly-created View.
     */
    public final AppWidgetHostView createView(Context context, int appWidgetId,
            AppWidgetProviderInfo appWidget) {
        final int userId = mContext.getUserId();
        AppWidgetHostView view = onCreateView(mContext, appWidgetId, appWidget);
        view.setUserId(userId);
        view.setOnClickHandler(mOnClickHandler);
        view.setAppWidget(appWidgetId, appWidget);
        synchronized (mViews) {
            mViews.put(appWidgetId, view);
        }
        RemoteViews views;
        try {
            views = sService.getAppWidgetViews(appWidgetId, userId);
            if (views != null) {
                views.setUser(new UserHandle(mContext.getUserId()));
            }
        } catch (RemoteException e) {
            throw new RuntimeException("system server dead?", e);
        }
        view.updateAppWidget(views);

        return view;
    }

    /**
     * Called to create the AppWidgetHostView.  Override to return a custom subclass if you
     * need it.  {@more}
     */
    protected AppWidgetHostView onCreateView(Context context, int appWidgetId,
            AppWidgetProviderInfo appWidget) {
        return new AppWidgetHostView(context, mOnClickHandler);
    }

    /**
     * Called when the AppWidget provider for a AppWidget has been upgraded to a new apk.
     */
    protected void onProviderChanged(int appWidgetId, AppWidgetProviderInfo appWidget) {
        AppWidgetHostView v;

        // Convert complex to dp -- we are getting the AppWidgetProviderInfo from the
        // AppWidgetService, which doesn't have our context, hence we need to do the
        // conversion here.
        appWidget.minWidth =
            TypedValue.complexToDimensionPixelSize(appWidget.minWidth, mDisplayMetrics);
        appWidget.minHeight =
            TypedValue.complexToDimensionPixelSize(appWidget.minHeight, mDisplayMetrics);
        appWidget.minResizeWidth =
            TypedValue.complexToDimensionPixelSize(appWidget.minResizeWidth, mDisplayMetrics);
        appWidget.minResizeHeight =
            TypedValue.complexToDimensionPixelSize(appWidget.minResizeHeight, mDisplayMetrics);

        synchronized (mViews) {
            v = mViews.get(appWidgetId);
        }
        if (v != null) {
            v.resetAppWidget(appWidget);
        }
    }

    /**
     * Called when the set of available widgets changes (ie. widget containing packages
     * are added, updated or removed, or widget components are enabled or disabled.)
     */
    protected void onProvidersChanged() {
        // Does nothing
    }

    void updateAppWidgetView(int appWidgetId, RemoteViews views, int userId) {
        AppWidgetHostView v;
        synchronized (mViews) {
            v = mViews.get(appWidgetId);
        }
        if (v != null) {
            v.updateAppWidget(views);
        }
    }

    void viewDataChanged(int appWidgetId, int viewId, int userId) {
        AppWidgetHostView v;
        synchronized (mViews) {
            v = mViews.get(appWidgetId);
        }
        if (v != null) {
            v.viewDataChanged(viewId);
        }
    }

    /**
     * Clear the list of Views that have been created by this AppWidgetHost.
     */
    protected void clearViews() {
        mViews.clear();
    }
}


