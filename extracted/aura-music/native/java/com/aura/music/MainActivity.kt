package com.aura.music

import android.os.Bundle
import com.getcapacitor.BridgeActivity

class MainActivity : BridgeActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        registerPlugin(AuraMediaPlugin::class.java) // must precede super.onCreate
        super.onCreate(savedInstanceState)
    }
}
