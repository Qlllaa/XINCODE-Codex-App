package com.codex.app

import android.app.Application
import android.util.Log

class CodexApplication : Application() {
    companion object {
        private const val TAG = "CodexApp"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Codex Application created")
        
        // Initialize global components here if needed
        // For example: database, security provider, etc.
    }
}