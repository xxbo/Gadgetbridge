/*  Copyright (C) 2026

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.
*/
package nodomain.freeyourgadget.gadgetbridge.devices.jbl;

import android.os.Parcel;

import androidx.annotation.NonNull;
import android.content.SharedPreferences;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;

import java.util.Collections;
import java.util.Set;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsCustomizer;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsHandler;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;

import static nodomain.freeyourgadget.gadgetbridge.service.devices.jbl.JblTuneBuds2Support.PREF_SOUND_MODE;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.jbl.JblTuneBuds2Support.PREF_ADAPTIVE_ANC;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.jbl.JblTuneBuds2Support.PREF_AUTO_COMPENSATION;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.jbl.JblTuneBuds2Support.PREF_ANC_LEVEL;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.jbl.JblTuneBuds2Support.PREF_AMBIENT_LEVEL;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.jbl.JblTuneBuds2Support.PREF_EQ_PRESET;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.jbl.JblTuneBuds2Support.PREF_EQ_BAND_32;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.jbl.JblTuneBuds2Support.PREF_EQ_BAND_64;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.jbl.JblTuneBuds2Support.PREF_EQ_BAND_125;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.jbl.JblTuneBuds2Support.PREF_EQ_BAND_250;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.jbl.JblTuneBuds2Support.PREF_EQ_BAND_500;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.jbl.JblTuneBuds2Support.PREF_EQ_BAND_1K;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.jbl.JblTuneBuds2Support.PREF_EQ_BAND_2K;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.jbl.JblTuneBuds2Support.PREF_EQ_BAND_4K;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.jbl.JblTuneBuds2Support.PREF_EQ_BAND_8K;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.jbl.JblTuneBuds2Support.PREF_EQ_BAND_16K;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.jbl.JblTuneBuds2Support.PREF_SPATIAL_AUDIO;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.jbl.JblTuneBuds2Support.PREF_LATENCY_MODE;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.jbl.JblTuneBuds2Support.PREF_CALL_SOUND_EFFECT;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.jbl.JblTuneBuds2Support.PREF_CALL_VOLUME_OPTIMIZER;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.jbl.JblTuneBuds2Support.PREF_SIDETONE;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.jbl.JblTuneBuds2Support.PREF_AUTO_POWER_OFF;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.jbl.JblTuneBuds2Support.PREF_SAFE_SOUND;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.jbl.JblTuneBuds2Support.PREF_CHANNEL_BALANCE;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.jbl.JblTuneBuds2Support.PREF_GESTURE_LEFT;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.jbl.JblTuneBuds2Support.PREF_GESTURE_RIGHT;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.jbl.JblTuneBuds2Support.PREF_ASC_CYCLE;
import static nodomain.freeyourgadget.gadgetbridge.service.devices.jbl.JblTuneBuds2Support.PREF_VOICE_PROMPTS;

public class JblTuneBuds2SettingsCustomizer implements DeviceSpecificSettingsCustomizer {
    private static final String[] EQ_BAND_KEYS = {
            PREF_EQ_BAND_32, PREF_EQ_BAND_64, PREF_EQ_BAND_125, PREF_EQ_BAND_250, PREF_EQ_BAND_500,
            PREF_EQ_BAND_1K, PREF_EQ_BAND_2K, PREF_EQ_BAND_4K, PREF_EQ_BAND_8K, PREF_EQ_BAND_16K
    };

    private final GBDevice device;

    public JblTuneBuds2SettingsCustomizer(final GBDevice device) {
        this.device = device;
    }

    protected JblTuneBuds2SettingsCustomizer(final Parcel in) {
        device = in.readParcelable(JblTuneBuds2SettingsCustomizer.class.getClassLoader());
    }

    @Override
    public void onPreferenceChange(final Preference preference, final DeviceSpecificSettingsHandler handler) {
        if (preference == null || preference.getKey() == null) {
            return;
        }
        final String key = preference.getKey();
        if (PREF_SOUND_MODE.equals(key) || PREF_ADAPTIVE_ANC.equals(key) || PREF_ANC_LEVEL.equals(key)
                || PREF_EQ_PRESET.equals(key) || PREF_GESTURE_LEFT.equals(key) || PREF_GESTURE_RIGHT.equals(key)) {
            final ListPreference soundModePref = handler.findPreference(PREF_SOUND_MODE);
            final String currentMode = soundModePref != null ? soundModePref.getValue() : "0";
            final SwitchPreferenceCompat adaptivePref = handler.findPreference(PREF_ADAPTIVE_ANC);
            final boolean currentAdaptive = adaptivePref != null && adaptivePref.isChecked();
            final ListPreference eqPref = handler.findPreference(PREF_EQ_PRESET);
            final String currentEq = eqPref != null ? eqPref.getValue() : "0";
            updateVisibility(handler, currentMode, currentAdaptive, currentEq);
        }
    }

    @Override
    public void onDeviceChanged(final DeviceSpecificSettingsHandler handler) {
        if (device == null) {
            return;
        }
        final ListPreference soundModePref = handler.findPreference(PREF_SOUND_MODE);
        final SwitchPreferenceCompat adaptivePref = handler.findPreference(PREF_ADAPTIVE_ANC);
        final SwitchPreferenceCompat autoCompPref = handler.findPreference(PREF_AUTO_COMPENSATION);
        final SeekBarPreference ancLevelPref = handler.findPreference(PREF_ANC_LEVEL);
        final SeekBarPreference ambientLevelPref = handler.findPreference(PREF_AMBIENT_LEVEL);
        final ListPreference eqPref = handler.findPreference(PREF_EQ_PRESET);
        final ListPreference spatialPref = handler.findPreference(PREF_SPATIAL_AUDIO);
        final ListPreference latencyPref = handler.findPreference(PREF_LATENCY_MODE);
        final ListPreference callEffectPref = handler.findPreference(PREF_CALL_SOUND_EFFECT);
        final SwitchPreferenceCompat callOptPref = handler.findPreference(PREF_CALL_VOLUME_OPTIMIZER);
        final ListPreference sidetonePref = handler.findPreference(PREF_SIDETONE);
        final ListPreference autoOffPref = handler.findPreference(PREF_AUTO_POWER_OFF);
        final SwitchPreferenceCompat safeSoundPref = handler.findPreference(PREF_SAFE_SOUND);
        final SeekBarPreference balancePref = handler.findPreference(PREF_CHANNEL_BALANCE);
        final ListPreference gestureLeftPref = handler.findPreference(PREF_GESTURE_LEFT);
        final ListPreference gestureRightPref = handler.findPreference(PREF_GESTURE_RIGHT);
        final ListPreference ascCyclePref = handler.findPreference(PREF_ASC_CYCLE);
        final ListPreference voicePromptsPref = handler.findPreference(PREF_VOICE_PROMPTS);

        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(device.getAddress());
        final String currentMode = prefs.getString(PREF_SOUND_MODE, "0");
        final boolean currentAdaptive = prefs.getBoolean(PREF_ADAPTIVE_ANC, false);
        final boolean currentAutoComp = prefs.getBoolean(PREF_AUTO_COMPENSATION, true);
        final int ancLevel = prefs.getInt(PREF_ANC_LEVEL, 4);
        final int ambientLevel = prefs.getInt(PREF_AMBIENT_LEVEL, 4);
        final String currentEq = prefs.getString(PREF_EQ_PRESET, "0");
        final String currentSpatial = prefs.getString(PREF_SPATIAL_AUDIO, "0");
        final String currentLatency = prefs.getString(PREF_LATENCY_MODE, "0");
        final String currentCallEffect = prefs.getString(PREF_CALL_SOUND_EFFECT, "0");
        final boolean currentCallOpt = prefs.getBoolean(PREF_CALL_VOLUME_OPTIMIZER, false);
        final String currentSidetone = prefs.getString(PREF_SIDETONE, "0");
        final String currentAutoOff = prefs.getString(PREF_AUTO_POWER_OFF, "0");
        final boolean currentSafeSound = prefs.getBoolean(PREF_SAFE_SOUND, false);
        final int currentBalance = prefs.getInt(PREF_CHANNEL_BALANCE, 0);
        final String currentGestureLeft = prefs.getString(PREF_GESTURE_LEFT, "1");
        final String currentGestureRight = prefs.getString(PREF_GESTURE_RIGHT, "2");
        final String currentAscCycle = prefs.getString(PREF_ASC_CYCLE, "9");
        final String currentVoicePrompts = prefs.getString(PREF_VOICE_PROMPTS, "0");

        if (soundModePref != null && !currentMode.equals(soundModePref.getValue())) {
            soundModePref.setValue(currentMode);
        }
        if (adaptivePref != null && adaptivePref.isChecked() != currentAdaptive) {
            adaptivePref.setChecked(currentAdaptive);
        }
        if (autoCompPref != null && autoCompPref.isChecked() != currentAutoComp) {
            autoCompPref.setChecked(currentAutoComp);
        }
        if (ancLevelPref != null && ancLevelPref.getValue() != ancLevel) {
            ancLevelPref.setValue(ancLevel);
        }
        if (ambientLevelPref != null && ambientLevelPref.getValue() != ambientLevel) {
            ambientLevelPref.setValue(ambientLevel);
        }
        if (eqPref != null && !currentEq.equals(eqPref.getValue())) {
            eqPref.setValue(currentEq);
        }
        for (String key : EQ_BAND_KEYS) {
            final SeekBarPreference bandPref = handler.findPreference(key);
            final int bandVal = prefs.getInt(key, 0);
            if (bandPref != null && bandPref.getValue() != bandVal) {
                bandPref.setValue(bandVal);
            }
        }
        if (spatialPref != null && !currentSpatial.equals(spatialPref.getValue())) {
            spatialPref.setValue(currentSpatial);
        }
        if (latencyPref != null && !currentLatency.equals(latencyPref.getValue())) {
            latencyPref.setValue(currentLatency);
        }
        if (callEffectPref != null && !currentCallEffect.equals(callEffectPref.getValue())) {
            callEffectPref.setValue(currentCallEffect);
        }
        if (callOptPref != null && callOptPref.isChecked() != currentCallOpt) {
            callOptPref.setChecked(currentCallOpt);
        }
        if (sidetonePref != null && !currentSidetone.equals(sidetonePref.getValue())) {
            sidetonePref.setValue(currentSidetone);
        }
        if (autoOffPref != null && !currentAutoOff.equals(autoOffPref.getValue())) {
            autoOffPref.setValue(currentAutoOff);
        }
        if (safeSoundPref != null && safeSoundPref.isChecked() != currentSafeSound) {
            safeSoundPref.setChecked(currentSafeSound);
        }
        if (balancePref != null && balancePref.getValue() != currentBalance) {
            balancePref.setValue(currentBalance);
        }
        if (gestureLeftPref != null && !currentGestureLeft.equals(gestureLeftPref.getValue())) {
            gestureLeftPref.setValue(currentGestureLeft);
        }
        if (gestureRightPref != null && !currentGestureRight.equals(gestureRightPref.getValue())) {
            gestureRightPref.setValue(currentGestureRight);
        }
        if (ascCyclePref != null && !currentAscCycle.equals(ascCyclePref.getValue())) {
            ascCyclePref.setValue(currentAscCycle);
        }
        if (voicePromptsPref != null && !currentVoicePrompts.equals(voicePromptsPref.getValue())) {
            voicePromptsPref.setValue(currentVoicePrompts);
        }

        updateVisibility(handler, currentMode, currentAdaptive, currentEq);
    }

    @Override
    public void customizeSettings(final DeviceSpecificSettingsHandler handler,
                                  final Prefs prefs, final String rootKey) {
        handler.addPreferenceHandlerFor(PREF_SOUND_MODE, (preference, newValue) -> {
            final SwitchPreferenceCompat adaptivePref = handler.findPreference(PREF_ADAPTIVE_ANC);
            final boolean currentAdaptive = adaptivePref != null && adaptivePref.isChecked();
            final ListPreference eqPref = handler.findPreference(PREF_EQ_PRESET);
            final String currentEq = eqPref != null ? eqPref.getValue() : "0";
            updateVisibility(handler, newValue != null ? newValue.toString() : "0", currentAdaptive, currentEq);
            return true;
        });
        handler.addPreferenceHandlerFor(PREF_ADAPTIVE_ANC, (preference, newValue) -> {
            final ListPreference soundModePref = handler.findPreference(PREF_SOUND_MODE);
            final String currentMode = soundModePref != null ? soundModePref.getValue() : "0";
            final ListPreference eqPref = handler.findPreference(PREF_EQ_PRESET);
            final String currentEq = eqPref != null ? eqPref.getValue() : "0";
            updateVisibility(handler, currentMode, Boolean.TRUE.equals(newValue), currentEq);
            return true;
        });
        handler.addPreferenceHandlerFor(PREF_ANC_LEVEL, (preference, newValue) -> {
            final SwitchPreferenceCompat adaptivePref = handler.findPreference(PREF_ADAPTIVE_ANC);
            if (adaptivePref != null && adaptivePref.isChecked()) {
                adaptivePref.setChecked(false);
            }
            final ListPreference soundModePref = handler.findPreference(PREF_SOUND_MODE);
            final String currentMode = soundModePref != null ? soundModePref.getValue() : "0";
            final ListPreference eqPref = handler.findPreference(PREF_EQ_PRESET);
            final String currentEq = eqPref != null ? eqPref.getValue() : "0";
            updateVisibility(handler, currentMode, false, currentEq);
            return true;
        });
        handler.addPreferenceHandlerFor(PREF_AUTO_COMPENSATION);
        handler.addPreferenceHandlerFor(PREF_AMBIENT_LEVEL);
        handler.addPreferenceHandlerFor(PREF_EQ_PRESET, (preference, newValue) -> {
            final ListPreference soundModePref = handler.findPreference(PREF_SOUND_MODE);
            final String currentMode = soundModePref != null ? soundModePref.getValue() : "0";
            final SwitchPreferenceCompat adaptivePref = handler.findPreference(PREF_ADAPTIVE_ANC);
            final boolean currentAdaptive = adaptivePref != null && adaptivePref.isChecked();
            updateVisibility(handler, currentMode, currentAdaptive, newValue != null ? newValue.toString() : "0");
            return true;
        });
        for (String key : EQ_BAND_KEYS) {
            handler.addPreferenceHandlerFor(key);
        }
        handler.addPreferenceHandlerFor(PREF_SPATIAL_AUDIO);
        handler.addPreferenceHandlerFor(PREF_LATENCY_MODE);
        handler.addPreferenceHandlerFor(PREF_CALL_SOUND_EFFECT);
        handler.addPreferenceHandlerFor(PREF_CALL_VOLUME_OPTIMIZER);
        handler.addPreferenceHandlerFor(PREF_SIDETONE);
        handler.addPreferenceHandlerFor(PREF_AUTO_POWER_OFF);
        handler.addPreferenceHandlerFor(PREF_SAFE_SOUND);
        handler.addPreferenceHandlerFor(PREF_CHANNEL_BALANCE);
        handler.addPreferenceHandlerFor(PREF_GESTURE_LEFT);
        handler.addPreferenceHandlerFor(PREF_GESTURE_RIGHT);
        handler.addPreferenceHandlerFor(PREF_ASC_CYCLE);
        handler.addPreferenceHandlerFor(PREF_VOICE_PROMPTS);

        final ListPreference soundModePref = handler.findPreference(PREF_SOUND_MODE);
        final String currentMode = soundModePref != null ? soundModePref.getValue() : "0";
        final SwitchPreferenceCompat adaptivePref = handler.findPreference(PREF_ADAPTIVE_ANC);
        final boolean currentAdaptive = adaptivePref != null && adaptivePref.isChecked();
        final ListPreference eqPref = handler.findPreference(PREF_EQ_PRESET);
        final String currentEq = eqPref != null ? eqPref.getValue() : "0";
        updateVisibility(handler, currentMode, currentAdaptive, currentEq);
    }

    private void updateVisibility(final DeviceSpecificSettingsHandler handler,
                                  final String modeStr, final boolean adaptiveAnc,
                                  final String eqPreset) {
        final boolean isConnected = device != null && device.isInitialized();
        final Preference adaptivePref = handler.findPreference(PREF_ADAPTIVE_ANC);
        final Preference autoCompPref = handler.findPreference(PREF_AUTO_COMPENSATION);
        final Preference ancLevelPref = handler.findPreference(PREF_ANC_LEVEL);
        final Preference ambientLevelPref = handler.findPreference(PREF_AMBIENT_LEVEL);
        final Preference eqPref = handler.findPreference(PREF_EQ_PRESET);
        final Preference spatialPref = handler.findPreference(PREF_SPATIAL_AUDIO);

        final boolean isAncMode = "1".equals(modeStr);
        final boolean isTalkThru = "2".equals(modeStr);
        final boolean isAmbientMode = "3".equals(modeStr);
        final boolean isCustomEq = "255".equals(eqPreset);

        if (adaptivePref != null) {
            adaptivePref.setEnabled(isConnected && isAncMode);
        }
        if (autoCompPref != null) {
            autoCompPref.setEnabled(isConnected && isAncMode);
        }
        if (ancLevelPref != null) {
            // ANC Level slider is enabled only when ANC mode is active and Adaptive ANC is turned OFF
            ancLevelPref.setEnabled(isConnected && isAncMode && !adaptiveAnc);
        }
        if (ambientLevelPref != null) {
            ambientLevelPref.setEnabled(isConnected && isAmbientMode);
        }
        // In TalkThru mode, DSP disables EQ and Spatial sound
        if (eqPref != null) {
            eqPref.setEnabled(isConnected && !isTalkThru);
        }
        for (String key : EQ_BAND_KEYS) {
            final Preference bandPref = handler.findPreference(key);
            if (bandPref != null) {
                bandPref.setEnabled(isConnected && !isTalkThru && isCustomEq);
            }
        }
        if (spatialPref != null) {
            spatialPref.setEnabled(isConnected && !isTalkThru);
        }

        final ListPreference leftGesturePref = handler.findPreference(PREF_GESTURE_LEFT);
        final ListPreference rightGesturePref = handler.findPreference(PREF_GESTURE_RIGHT);
        final boolean hasAscGesture = (leftGesturePref != null && "1".equals(leftGesturePref.getValue()))
                || (rightGesturePref != null && "1".equals(rightGesturePref.getValue()));
        final Preference ascCyclePref = handler.findPreference(PREF_ASC_CYCLE);
        if (ascCyclePref != null) {
            ascCyclePref.setEnabled(isConnected && hasAscGesture);
        }
    }

    @Override
    public Set<String> getPreferenceKeysWithSummary() {
        return Collections.emptySet();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull final Parcel dest, final int flags) {
        dest.writeParcelable(device, flags);
    }

    public static final Creator<JblTuneBuds2SettingsCustomizer> CREATOR =
            new Creator<JblTuneBuds2SettingsCustomizer>() {
                @Override
                public JblTuneBuds2SettingsCustomizer createFromParcel(final Parcel in) {
                    return new JblTuneBuds2SettingsCustomizer(in);
                }

                @Override
                public JblTuneBuds2SettingsCustomizer[] newArray(final int size) {
                    return new JblTuneBuds2SettingsCustomizer[size];
                }
            };
}
