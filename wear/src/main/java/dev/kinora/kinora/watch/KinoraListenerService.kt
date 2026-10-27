package dev.kinora.kinora.watch

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject

/**
 * Receives from the Kinnora phone app, even when this app isn't open:
 * - "/kinora/play": a touch to play now;
 * - "/kinora/sync": experiences and the reminder plan (data item, kept in sync by the Data Layer);
 * - "/kinora/reminders": the reminder plan alone (older phone builds).
 */
class KinoraListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == "/kinora/play") {
            val json = runCatching { JSONObject(String(event.data)) }.getOrNull() ?: return
            TouchPlayer.play(this, TouchPattern.fromJson(json))
        }
    }

    override fun onDataChanged(events: DataEventBuffer) {
        for (e in events) {
            if (e.type != DataEvent.TYPE_CHANGED) continue
            val map = DataMapItem.fromDataItem(e.dataItem).dataMap
            when (e.dataItem.uri.path) {
                "/kinora/sync" -> WatchStore.apply(this, map.getString("json") ?: continue)
                "/kinora/reminders" -> CueStore.save(this, map.getString("cues") ?: "[]")
            }
        }
    }
}
