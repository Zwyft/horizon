package com.horizon.coparentinglog.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SmsBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SyncScheduler.requestSync(context)
    }
}
