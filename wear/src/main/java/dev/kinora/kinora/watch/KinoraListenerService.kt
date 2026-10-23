package dev.kinora.kinora.watch

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject

/** Receives touches ("/kinora/play") and reminder plans ("/kinora/reminders") from the phone. */
class KinoraListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == "/kinora/play") {
            val json = runCatching { JSONObject(String(event.data)) }.getOrNull() ?: return
            TouchPlayer.play(this, TouchPattern.fromJson(json))
        }
    }

    override fun onDataChanged(events: DataEventBuffer) {
        for (e in events) {
            if (e.type == DataEvent.TYPE_CHANGED && e.dataItem.uri.path == "/kinora/reminders") {
                val cues = DataMapItem.fromDataItem(e.dataItem).dataMap.getString("cues") ?: "[]"
                CueStore.save(this, cues)
            }
        }
    }
}
