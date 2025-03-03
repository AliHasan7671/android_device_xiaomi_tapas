/*
 * Copyright (C) 2023 The PixelOS Project
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

package org.pixelexperience.xiaomi.voipfix;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.AudioSystem;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserHandle;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;

/**
 * VoIPFixService - automatically triggers volume adjustments during VoIP calls
 * to resolve muted audio issues on Xiaomi SM8350 devices
 */
public class VoIPFixService extends Service {
    private static final String TAG = "VoIPFixService";
    private static final boolean DEBUG = true;

    private AudioManager mAudioManager;
    private TelephonyManager mTelephonyManager;
    private Handler mHandler;
    
    // Track active VoIP state
    private boolean mVoIPCallActive = false;
    private boolean mSpeakerActive = false;
    private boolean mIsFixApplied = false;
    private long mLastSpeakerChange = 0;
    private boolean mPendingSpeakerFix = false;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            
            if (AudioManager.STREAM_DEVICES_CHANGED_ACTION.equals(action)) {
                int streamType = intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1);
                
                // When any audio routing changes during a VoIP call, prepare to apply fix
                if (mVoIPCallActive) {
                    boolean currentSpeakerState = mAudioManager.isSpeakerphoneOn();
                    
                    // Check if speaker state has changed
                    if (mSpeakerActive != currentSpeakerState) {
                        mSpeakerActive = currentSpeakerState;
                        log("Speaker mode changed to: " + mSpeakerActive);
                        
                        // Set flag for pending speaker fix
                        mPendingSpeakerFix = true;
                        mLastSpeakerChange = System.currentTimeMillis();
                        mIsFixApplied = false;
                        
                        // Schedule multiple fix attempts to ensure it catches
                        scheduleMultipleFixes();
                    }
                }
            } else if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action)) {
                // Check for incoming call state changes
                String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
                if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
                    // Call was just answered
                    mVoIPCallActive = true;
                    mSpeakerActive = mAudioManager.isSpeakerphoneOn();
                    log("Call is active, monitoring for VoIP streams");
                    // Apply fix with slight delay to let audio streams initialize
                    mHandler.postDelayed(() -> applyVolumeButtonFix(), 1000);
                } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
                    // Call ended
                    mVoIPCallActive = false;
                    mIsFixApplied = false;
                    mPendingSpeakerFix = false;
                    log("Call ended, resetting VoIP fix state");
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        log("VoIPFix Service starting");
        
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        mHandler = new Handler(Looper.getMainLooper());
        
        // Register for broadcasts related to call state and audio routing changes
        IntentFilter filter = new IntentFilter();
        filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        filter.addAction(AudioManager.STREAM_DEVICES_CHANGED_ACTION);
        filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        filter.addAction(AudioManager.STREAM_MUTE_CHANGED_ACTION);
        registerReceiver(mReceiver, filter);
        
        // Start a background monitoring task to detect VoIP streams and speaker changes
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                checkVoIPAndSpeakerState();
                mHandler.postDelayed(this, 500); // Check every 500ms
            }
        }, 500);
    }
    
    private void checkVoIPAndSpeakerState() {
        // Check audio mode to detect VoIP calls
        int mode = mAudioManager.getMode();
        if (mode == AudioManager.MODE_IN_COMMUNICATION) {
            if (!mVoIPCallActive) {
                log("VoIP activity detected via audio mode");
                mVoIPCallActive = true;
                mSpeakerActive = mAudioManager.isSpeakerphoneOn();
                // Apply fix with slight delay
                mHandler.postDelayed(() -> applyVolumeButtonFix(), 1000);
            } else {
                // During active call, continuously check speaker state
                boolean currentSpeakerState = mAudioManager.isSpeakerphoneOn();
                if (mSpeakerActive != currentSpeakerState) {
                    log("Speaker change detected in polling: " + currentSpeakerState);
                    mSpeakerActive = currentSpeakerState;
                    mIsFixApplied = false;
                    mPendingSpeakerFix = true;
                    mLastSpeakerChange = System.currentTimeMillis();
                    scheduleMultipleFixes();
                }
                
                // If we have a pending speaker fix and enough time has passed, apply it
                if (mPendingSpeakerFix && 
                    System.currentTimeMillis() - mLastSpeakerChange > 300 &&
                    !mIsFixApplied) {
                    applyVolumeButtonFix();
                }
            }
        } else if (mVoIPCallActive && mode != AudioManager.MODE_IN_CALL) {
            // Call ended
            mVoIPCallActive = false;
            mIsFixApplied = false;
            mPendingSpeakerFix = false;
            log("VoIP activity ended, resetting fix state");
        }
    }
    
    private void scheduleMultipleFixes() {
        // Schedule multiple volume adjustment attempts to ensure it works
        mHandler.postDelayed(() -> {
            if (mPendingSpeakerFix && !mIsFixApplied) {
                log("Applying first scheduled fix after speaker change");
                applyVolumeButtonFix();
            }
        }, 300);
        
        mHandler.postDelayed(() -> {
            if (mPendingSpeakerFix && !mIsFixApplied) {
                log("Applying second scheduled fix after speaker change");
                applyVolumeButtonFix();
            }
        }, 600);
        
        mHandler.postDelayed(() -> {
            if (mPendingSpeakerFix && !mIsFixApplied) {
                log("Applying third scheduled fix after speaker change");
                applyVolumeButtonFix();
            }
        }, 1000);
    }
    
    private void applyVolumeButtonFix() {
        if (!mVoIPCallActive) {
            return;
        }
        
        log("Applying volume button fix for VoIP audio");
        
        // Get current volume
        int currentVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL);
        int maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL);
        
        // Store the current value so we can restore it
        final int originalVolume = currentVolume;
        
        // Determine adjustment direction: if near max, decrease then increase
        if (currentVolume > maxVolume / 2) {
            // We're above half volume, so decrease then increase
            log("Current volume: " + currentVolume + ", decreasing then restoring");
            mAudioManager.adjustStreamVolume(
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.ADJUST_LOWER,
                    0);
            
            // Wait a moment before restoring
            mHandler.postDelayed(() -> {
                mAudioManager.setStreamVolume(
                        AudioManager.STREAM_VOICE_CALL,
                        originalVolume,
                        0);
                mIsFixApplied = true;
                mPendingSpeakerFix = false;
                log("Volume fix applied and restored to: " + originalVolume);
            }, 300);
        } else {
            // We're at or below half volume, so increase then decrease
            log("Current volume: " + currentVolume + ", increasing then restoring");
            mAudioManager.adjustStreamVolume(
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.ADJUST_RAISE,
                    0);
            
            // Wait a moment before restoring
            mHandler.postDelayed(() -> {
                mAudioManager.setStreamVolume(
                        AudioManager.STREAM_VOICE_CALL,
                        originalVolume,
                        0);
                mIsFixApplied = true;
                mPendingSpeakerFix = false;
                log("Volume fix applied and restored to: " + originalVolume);
            }, 300);
        }
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mReceiver);
        super.onDestroy();
        log("VoIPFix Service destroyed");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            log("Received action: " + intent.getAction());
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void log(String msg) {
        if (DEBUG) {
            Log.d(TAG, msg);
        }
    }
}
