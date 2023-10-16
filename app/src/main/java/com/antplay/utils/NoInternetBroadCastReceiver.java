package com.antplay.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.greenrobot.eventbus.EventBus;

/**
 * Created by JitendraSingh on 11,Jun 2016.
 * Email jitendra@live.in
 */
public class NoInternetBroadCastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        EventBus.getDefault().post(AppUtils.isOnline(context));
    }
}
