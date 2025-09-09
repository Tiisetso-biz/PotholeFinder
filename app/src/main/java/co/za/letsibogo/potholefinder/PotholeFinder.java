/*
package co.za.letsibogo.potholefinder;

import android.app.Application;
import android.content.Context;

import androidx.lifecycle.MutableLiveData;
import androidx.multidex.MultiDex;
import androidx.multidex.MultiDexApplication;

import dji.v5.common.error.IDJIError;
import dji.v5.common.register.DJISDKInitEvent;
import dji.v5.manager.SDKManager;
import dji.v5.manager.interfaces.SDKManagerCallback;
import dji.v5.network.DJINetworkManager;

public class PotholeFinder extends MultiDexApplication {

    public MutableLiveData<Boolean> isRegistered = new MutableLiveData<>();
    public MutableLiveData<Boolean> isProductConnected = new MutableLiveData<>();
    public MutableLiveData<DJISDKInitEvent> initEvent = new MutableLiveData<>();
    private boolean isInit = false;

    @Override
    public void onCreate() {
        super.onCreate();
        initMobileSDK(this);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    private void initMobileSDK(Context context) {
        SDKManager.getInstance().init(context, new SDKManagerCallback() {
            @Override
            public void onRegisterSuccess() {
                isRegistered.postValue(true);
            }

            @Override
            public void onRegisterFailure(IDJIError error) {
                isRegistered.postValue(false);
            }

            @Override
            public void onProductDisconnect(int productId) {
                isProductConnected.postValue(false);
            }

            @Override
            public void onProductConnect(int productId) {
                isProductConnected.postValue(true);
            }

            @Override
            public void onProductChanged(int productId) {
                // Handle product change if needed
            }

            @Override
            public void onInitProcess(DJISDKInitEvent event, int totalProcess) {
                initEvent.postValue(event);

                if (event == DJISDKInitEvent.INITIALIZE_COMPLETE) {
                    isInit = true;
                    SDKManager.getInstance().registerApp();
                }
            }

            @Override
            public void onDatabaseDownloadProgress(long current, long total) {
                // Optional: track database download
            }
        });

        // Ensure re-registration if network status changes
        DJINetworkManager.getInstance().addNetworkStatusListener(isAvailable -> {
            if (isInit && isAvailable && !SDKManager.getInstance().isRegistered()) {
                SDKManager.getInstance().registerApp();
            }
        });
    }

    public void destroyMobileSDK() {
        SDKManager.getInstance().destroy();
    }
}
*/

package co.za.letsibogo.potholefinder;

import android.app.Application;
import android.content.Context;

import androidx.multidex.MultiDex;
import androidx.multidex.MultiDexApplication;

public class PotholeFinder extends MultiDexApplication {

    @Override
    public void onCreate() {
        super.onCreate();
        // No DJI SDK init for now, focus only on UI and video feed.
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }
}

