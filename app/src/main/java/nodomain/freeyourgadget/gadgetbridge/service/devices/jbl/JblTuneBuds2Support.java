/*  Copyright (C) 2026

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.
*/
package nodomain.freeyourgadget.gadgetbridge.service.devices.jbl;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.BatteryState;
import nodomain.freeyourgadget.gadgetbridge.service.AbstractDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

import static nodomain.freeyourgadget.gadgetbridge.service.devices.jbl.JblProtocol.*;

/**
 * Transport and protocol implementation for the JBL Tune Buds 2 using Harman Protocol 3.
 *
 * <p>A paired JBL TWS headset exposes a rotating BLE address: the persistent identifier
 * is the device's Classic MAC, but the actual {@code BluetoothGatt} connection has to
 * be opened against the most recent session address stashed on
 * {@link GBDevice#getVolatileAddress()}. The stock {@code BtLEQueue} always opens
 * against {@code GBDevice.getAddress()} and has no override hook, so this support
 * drives its own {@link JblGattLink} against the session address. The Gadgetbridge
 * core is otherwise untouched.</p>
 *
 * <h3>Connection strategy</h3>
 * <ol>
 *   <li>If a cached volatile LE address exists, try connecting directly.</li>
 *   <li>If that fails (or no cache exists), scan for Tune Buds 2 advertisements
 *       whose CRC matches the phone's local Bluetooth name.</li>
 *   <li>On disconnect, the volatile address is <em>not</em> cleared; the BLE
 *       session address usually remains valid while the Classic ACL link is up.
 *       Only when a reconnect attempt fails do we invalidate the cache and
 *       re-scan.</li>
 * </ol>
 */
@SuppressLint("MissingPermission") // if we're using this, we have bluetooth permissions
public class JblTuneBuds2Support extends AbstractDeviceSupport implements JblGattLink.Sink {
    private static final Logger LOG = LoggerFactory.getLogger(JblTuneBuds2Support.class);

    // Harman BES BLE GATT Service and Characteristic UUIDs
    public static final UUID SERVICE_UUID               = JblProtocol.SERVICE_UUID;
    public static final UUID NOTIFY_CHARACTERISTIC_UUID = JblProtocol.NOTIFY_CHARACTERISTIC_UUID;
    public static final UUID WRITE_CHARACTERISTIC_UUID  = JblProtocol.WRITE_CHARACTERISTIC_UUID;
    public static final UUID RAW_CHARACTERISTIC_UUID    = JblProtocol.RAW_CHARACTERISTIC_UUID;

    // Preference keys
    public static final String PREF_SOUND_MODE            = "pref_jbl_tune_buds_2_sound_mode";
    public static final String PREF_ADAPTIVE_ANC          = "pref_jbl_tune_buds_2_adaptive_anc";
    public static final String PREF_AUTO_COMPENSATION     = "pref_jbl_tune_buds_2_auto_compensation";
    public static final String PREF_ANC_LEVEL             = "pref_jbl_tune_buds_2_anc_level";
    public static final String PREF_AMBIENT_LEVEL         = "pref_jbl_tune_buds_2_ambient_level";
    public static final String PREF_EQ_BAND_32            = "pref_jbl_tune_buds_2_eq_band_32";
    public static final String PREF_EQ_BAND_64            = "pref_jbl_tune_buds_2_eq_band_64";
    public static final String PREF_EQ_BAND_125           = "pref_jbl_tune_buds_2_eq_band_125";
    public static final String PREF_EQ_BAND_250           = "pref_jbl_tune_buds_2_eq_band_250";
    public static final String PREF_EQ_BAND_500           = "pref_jbl_tune_buds_2_eq_band_500";
    public static final String PREF_EQ_BAND_1K            = "pref_jbl_tune_buds_2_eq_band_1k";
    public static final String PREF_EQ_BAND_2K            = "pref_jbl_tune_buds_2_eq_band_2k";
    public static final String PREF_EQ_BAND_4K            = "pref_jbl_tune_buds_2_eq_band_4k";
    public static final String PREF_EQ_BAND_8K            = "pref_jbl_tune_buds_2_eq_band_8k";
    public static final String PREF_EQ_BAND_16K           = "pref_jbl_tune_buds_2_eq_band_16k";
    public static final String PREF_SPATIAL_AUDIO         = "pref_jbl_tune_buds_2_spatial_audio";
    public static final String PREF_LATENCY_MODE          = "pref_jbl_tune_buds_2_latency_mode";
    public static final String PREF_CALL_SOUND_EFFECT     = "pref_jbl_tune_buds_2_call_sound_effect";
    public static final String PREF_CALL_VOLUME_OPTIMIZER = "pref_jbl_tune_buds_2_call_volume_optimizer";
    public static final String PREF_SIDETONE              = "pref_jbl_tune_buds_2_sidetone";
    public static final String PREF_AUTO_POWER_OFF        = "pref_jbl_tune_buds_2_auto_power_off";
    public static final String PREF_SAFE_SOUND            = "pref_jbl_tune_buds_2_safe_sound";
    public static final String PREF_CHANNEL_BALANCE       = "pref_jbl_tune_buds_2_channel_balance";
    public static final String PREF_GESTURE_LEFT          = "pref_jbl_tune_buds_2_gesture_left";
    public static final String PREF_GESTURE_RIGHT         = "pref_jbl_tune_buds_2_gesture_right";
    public static final String PREF_ASC_CYCLE             = "pref_jbl_tune_buds_2_asc_cycle";
    public static final String PREF_VOICE_PROMPTS         = "pref_jbl_tune_buds_2_voice_prompts";

    // Initial state query packets
    private static final byte[] GET_MODES = buildPacket(CMD_NOISE_CONTROL, SUB_NOISE_CONTROL_GET_MODES);
    private static final byte[] GET_ADVANCED_ANC = buildPacket(CMD_NOISE_CONTROL, SUB_NOISE_CONTROL_GET_ADVANCED);
    private static final byte[] GET_LATENCY_MODE = buildPacket(CMD_SMART_AUDIO_VIDEO, SUB_CMD_GET, (byte) 0x01);
    private static final byte[] GET_BALANCE = buildPacket(CMD_STEREO_BALANCE, SUB_CMD_GET, (byte) 0x01);
    private static final byte[] GET_VOICE_PROMPTS = buildPacket(CMD_VOICE_PROMPTS, (byte) 0x04);
    private static final byte[] GET_EQ = buildPacket(CMD_EQ_SETTINGS_2, SUB_CMD_GET, (byte) 0xff);
    private static final byte[] GET_GESTURES = buildPacket(CMD_GESTURE_CONFIG, SUB_CMD_GET, (byte) 0xff);
    private static final String[] EQ_BAND_PREFS = {
            PREF_EQ_BAND_32, PREF_EQ_BAND_64, PREF_EQ_BAND_125, PREF_EQ_BAND_250, PREF_EQ_BAND_500,
            PREF_EQ_BAND_1K, PREF_EQ_BAND_2K, PREF_EQ_BAND_4K, PREF_EQ_BAND_8K, PREF_EQ_BAND_16K
    };
    private static final byte[] GET_DEVICE_INFO_GENERAL = buildDeviceInfoGet(
            FEATURE_AUTO_OFF,
            FEATURE_SPATIAL_SOUND,
            FEATURE_VOICE_AWARE_SIDE_TONE,
            FEATURE_CALL_SOUND_EFFECT,
            FEATURE_SAFE_SOUND,
            FEATURE_CALL_VOLUME_OPTIMIZER
    );
    private static final byte[] GET_DEVICE_INFO_BATTERY = buildDeviceInfoGet(
            FEATURE_BATTERY_LEFT,
            FEATURE_BATTERY_RIGHT,
            FEATURE_BATTERY_CASE
    );

    private boolean mRequestedAnc = false;
    private JblGattLink gattLink;
    private JblLeAddressScanner addressScanner;
    private boolean disposed;
    private boolean servicesDiscovered = false;

    private static final long EQ_WRITE_DELAY_MS = 200L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final Runnable writeCustomEq = new Runnable() {
        @Override
        public void run() {
            write(WRITE_CHARACTERISTIC_UUID, buildCustomEqCommand());
        }
    };

    public JblTuneBuds2Support() {
        super();
        setAutoReconnect(true);
        setScanReconnect(true);
    }

    @Override
    public boolean useAutoConnect() {
        return false;
    }

    public static byte[] buildPacket(final byte cmd, final byte... payload) {
        final byte[] packet = new byte[3 + payload.length];
        packet[0] = HEADER_MAGIC;
        packet[1] = cmd;
        packet[2] = (byte) payload.length;
        System.arraycopy(payload, 0, packet, 3, payload.length);
        return packet;
    }

    // ─── Connection ────────────────────────────────────────────────────────

    @RequiresPermission(allOf = {"android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT"})
    @Override
    public synchronized boolean connect() {
        final GBDevice device = getDevice();
        if (device == null) {
            return false;
        }
        final Context context = getContext();
        if (context == null) {
            return false;
        }
        disposed = false;
        device.setUpdateState(GBDevice.State.CONNECTING, context);

        // Try the cached volatile LE address first.
        final String cached = device.getVolatileAddress();
        if (cached != null) {
            LOG.info("JblTuneBuds2Support.connect: trying cached session address {}", cached);
            openLink(context, device, cached);
            return true;
        }

        // No cache: scan for the Harman broadcast with the local Bluetooth-name CRC.
        LOG.info("JblTuneBuds2Support.connect: no cached session address, scanning");
        scanAndConnect(context, device);
        return true;
    }

    @RequiresPermission(allOf = {"android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT"})
    private synchronized void scanAndConnect(@NonNull final Context context, @NonNull final GBDevice device) {
        if (disposed || (addressScanner != null && addressScanner.isInFlight())) {
            return;
        }
        final JblLeAddressScanner scanner = new JblLeAddressScanner(context);
        addressScanner = scanner;
        scanner.start(new JblLeAddressScanner.Listener() {
            @Override
            public void onResolved(@NonNull final String address) {
                synchronized (JblTuneBuds2Support.this) {
                    if (disposed || addressScanner != scanner) {
                        return;
                    }
                    addressScanner = null;
                    LOG.info("JblTuneBuds2Support: scanner resolved {}", address);
                    device.setVolatileAddress(address);
                    openLink(context, device, address);
                }
            }

            @Override
            public void onFailed() {
                synchronized (JblTuneBuds2Support.this) {
                    if (disposed || addressScanner != scanner) {
                        return;
                    }
                    addressScanner = null;
                    LOG.warn("JblTuneBuds2Support: scanner failed to find LE address");
                    if (device.getState() == GBDevice.State.CONNECTING) {
                        device.setUpdateState(GBDevice.State.NOT_CONNECTED, context);
                        device.sendDeviceUpdateIntent(context);
                    }
                }
            }
        });
    }

    @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
    private synchronized void openLink(@NonNull final Context context,
                          @NonNull final GBDevice device,
                          @NonNull final String address) {
        if (disposed) {
            return;
        }
        if (addressScanner != null) {
            addressScanner.cancel();
            addressScanner = null;
        }
        if (gattLink != null) {
            gattLink.dispose();
        }
        gattLink = new JblGattLink(context, device, this);
        gattLink.connect(address);
    }

    @Override
    public synchronized void dispose() {
        disposed = true;
        mainHandler.removeCallbacks(writeCustomEq);
        if (addressScanner != null) {
            addressScanner.cancel();
            addressScanner = null;
        }
        if (gattLink != null) {
            gattLink.dispose();
            gattLink = null;
        }
        servicesDiscovered = false;
    }

    @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
    private void write(@NonNull final UUID characteristicUuid, @NonNull final byte[] value) {
        if (gattLink == null) {
            LOG.warn("JblTuneBuds2Support.write: no gatt link, dropping {} bytes", value.length);
            return;
        }
        gattLink.enqueueWrite(SERVICE_UUID, characteristicUuid, value);
    }

    private void initializeDevice() {
        gattLink.requestMtu(512);
        write(WRITE_CHARACTERISTIC_UUID, GET_DEVICE_INFO_GENERAL);
        write(WRITE_CHARACTERISTIC_UUID, GET_DEVICE_INFO_BATTERY);
        write(WRITE_CHARACTERISTIC_UUID, GET_MODES);
        write(WRITE_CHARACTERISTIC_UUID, GET_ADVANCED_ANC);
        write(WRITE_CHARACTERISTIC_UUID, GET_LATENCY_MODE);
        write(WRITE_CHARACTERISTIC_UUID, GET_BALANCE);
        write(WRITE_CHARACTERISTIC_UUID, GET_VOICE_PROMPTS);
        write(WRITE_CHARACTERISTIC_UUID, GET_EQ);
        write(WRITE_CHARACTERISTIC_UUID, GET_GESTURES);
    }

    private static byte[] buildDeviceInfoGet(final byte... featureIds) {
        final int payloadLength = 1 + (featureIds.length * 2);
        final byte[] packet = new byte[4 + payloadLength];
        packet[0] = HEADER_MAGIC;
        packet[1] = CMD_DEVICE_CONFIG;
        packet[2] = (byte) payloadLength;
        packet[3] = (byte) (payloadLength >>> 8);
        packet[4] = SUB_CMD_GET;
        for (int i = 0; i < featureIds.length; i++) {
            packet[5 + (i * 2)] = 0;
            packet[6 + (i * 2)] = featureIds[i];
        }
        return packet;
    }

    private static byte[] buildDeviceInfoSet(final byte featureId, final byte... data) {
        final int payloadLength = 4 + data.length;
        final byte[] packet = new byte[4 + payloadLength];
        packet[0] = HEADER_MAGIC;
        packet[1] = CMD_DEVICE_CONFIG;
        packet[2] = (byte) payloadLength;
        packet[3] = (byte) (payloadLength >>> 8);
        packet[4] = SUB_CMD_SET;
        packet[5] = 0;
        packet[6] = featureId;
        packet[7] = (byte) data.length;
        System.arraycopy(data, 0, packet, 8, data.length);
        return packet;
    }

    // ─── GATT callbacks ────────────────────────────────────────────────────

    @RequiresPermission("android.permission.BLUETOOTH_CONNECT")
    @Override
    public void onConnectionStateChange(final int newState, final int status) {
        final GBDevice device = getDevice();
        if (device == null) {
            return;
        }
        final Context context = broadcastContext();
        switch (newState) {
            case BluetoothProfile.STATE_CONNECTED:
                LOG.info("JBL: connected (status={})", status);
                servicesDiscovered = false;
                if (device.getState() != GBDevice.State.CONNECTED) {
                    device.setUpdateState(GBDevice.State.CONNECTED, context);
                }
                device.sendDeviceUpdateIntent(context);
                ensureDeviceRegistered();
                if (gattLink != null) {
                    gattLink.discoverServices();
                }
                break;

            case BluetoothProfile.STATE_DISCONNECTED:
                LOG.info("JBL: disconnected (status={})", status);
                servicesDiscovered = false;

                // Keep the cached address only after the expected JBL GATT
                // service and notifications were configured. A successful LE
                // link to a different peripheral is not a usable connection.
                final boolean protocolNotReady = gattLink == null || !gattLink.didCompleteProtocolSetup();
                if (protocolNotReady) {
                    LOG.warn("JBL: protocol setup did not complete (status={}), invalidating stale LE address", status);
                    device.setVolatileAddress(null);
                }

                final GBDevice.State prevState = device.getState();
                if (prevState != GBDevice.State.NOT_CONNECTED) {
                    device.setUpdateState(GBDevice.State.NOT_CONNECTED, context);
                }
                device.sendDeviceUpdateIntent(context);

                if (protocolNotReady && device.getState() == GBDevice.State.NOT_CONNECTED) {
                    // Immediate fresh scan fallback for auto-reconnect.
                    scanAndConnect(context, device);
                }
                break;

            default:
                break;
        }
    }

    private Context broadcastContext() {
        final Context ctx = getContext();
        return ctx != null ? ctx : GBApplication.app();
    }

    private void ensureDeviceRegistered() {
        final GBDevice device = getDevice();
        if (device == null) {
            return;
        }
        final Context context = broadcastContext();
        try (DBHandler dbHandler = GBApplication.acquireDB()) {
            DBHelper.getDevice(device, dbHandler.getDaoSession());
            LOG.info("JBL: device {} registered in database", device.getAddress());
        } catch (final Exception e) {
            LOG.warn("JBL: failed to register device in database", e);
        }
    }

    @Override
    public void onServicesDiscovered(@NonNull final List<BluetoothGattService> services) {
        servicesDiscovered = true;
        final GBDevice device = getDevice();
        if (device == null) {
            return;
        }
        if (device.getState() == GBDevice.State.CONNECTING
                || device.getState() == GBDevice.State.CONNECTED) {
            initializeDevice();
        }
    }

    @Override
    public void onCharacteristicChanged(@NonNull final BluetoothGattCharacteristic characteristic,
                                        @NonNull final byte[] value) {
        final UUID uuid = characteristic.getUuid();
        if (NOTIFY_CHARACTERISTIC_UUID.equals(uuid) || RAW_CHARACTERISTIC_UUID.equals(uuid)) {
            LOG.info("JBL notification {}: {}", uuid, GB.hexdump(value));
            parseBatteryResponse(value);
            parseSoundModeResponse(value);
            parseAdvancedAncResponse(value);
            parseEqResponse(value);
            parseSpatial3DResponse(value);
            parseSmartSwitchResponse(value);
            parseVoiceAwareResponse(value);
            parseAutoOffResponse(value);
            parseSafeSoundResponse(value);
            parseBalanceResponse(value);
            parseVoicePromptsResponse(value);
            parseDeviceConfigResponse(value);
            parseGestureResponse(value);
        }
    }

    @Override
    public void onCharacteristicWrite(@NonNull final BluetoothGattCharacteristic characteristic, final int status) {
        final GBDevice device = getDevice();
        if (device == null) {
            return;
        }
        final Context context = getContext();
        if (context == null) {
            return;
        }
        if (status == BluetoothGatt.GATT_SUCCESS) {
            // Transition CONNECTING -> INITIALIZED once the first write succeeds.
            // Note: STAY_ON_CONNECTED was set when the GATT link came up, so the
            // state is CONNECTED (not CONNECTING) by the time writes flow; accept
            // either so the device does not remain stuck on "connecting".
            if (servicesDiscovered
                    && (device.getState() == GBDevice.State.CONNECTING
                        || device.getState() == GBDevice.State.CONNECTED)) {
                device.setUpdateState(GBDevice.State.INITIALIZED, context);
            }
        }
    }

    @Override
    public void onMtuChanged(final int mtu, final int status) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            LOG.info("JBL: MTU changed to {}", mtu);
        }
    }

    // ─── Configuration dispatch ────────────────────────────────────────────

    @Override
    public void onSendConfiguration(final String config) {
        if (!PREF_SOUND_MODE.equals(config)
                && !PREF_ADAPTIVE_ANC.equals(config)
                && !PREF_AUTO_COMPENSATION.equals(config)
                && !PREF_ANC_LEVEL.equals(config)
                && !PREF_AMBIENT_LEVEL.equals(config)
                && !PREF_EQ_BAND_32.equals(config)
                && !PREF_EQ_BAND_64.equals(config)
                && !PREF_EQ_BAND_125.equals(config)
                && !PREF_EQ_BAND_250.equals(config)
                && !PREF_EQ_BAND_500.equals(config)
                && !PREF_EQ_BAND_1K.equals(config)
                && !PREF_EQ_BAND_2K.equals(config)
                && !PREF_EQ_BAND_4K.equals(config)
                && !PREF_EQ_BAND_8K.equals(config)
                && !PREF_EQ_BAND_16K.equals(config)
                && !PREF_SPATIAL_AUDIO.equals(config)
                && !PREF_LATENCY_MODE.equals(config)
                && !PREF_CALL_SOUND_EFFECT.equals(config)
                && !PREF_CALL_VOLUME_OPTIMIZER.equals(config)
                && !PREF_SIDETONE.equals(config)
                && !PREF_AUTO_POWER_OFF.equals(config)
                && !PREF_SAFE_SOUND.equals(config)
                && !PREF_CHANNEL_BALANCE.equals(config)
                && !PREF_GESTURE_LEFT.equals(config)
                && !PREF_GESTURE_RIGHT.equals(config)
                && !PREF_ASC_CYCLE.equals(config)
                && !PREF_VOICE_PROMPTS.equals(config)) {
            return;
        }

        try {
            final SharedPreferences prefs =
                    GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress());
            final int mode = Integer.parseInt(prefs.getString(PREF_SOUND_MODE, "0"));

            if (PREF_SOUND_MODE.equals(config)) {
                mRequestedAnc = (mode == SOUND_MODE_ANC);
                write(WRITE_CHARACTERISTIC_UUID, buildModeCommand(mode));
            }

            if (PREF_ADAPTIVE_ANC.equals(config)) {
                final boolean adaptive = prefs.getBoolean(PREF_ADAPTIVE_ANC, false);
                if (adaptive) {
                    write(WRITE_CHARACTERISTIC_UUID, buildAdaptiveAncCommand(true));
                } else {
                    write(WRITE_CHARACTERISTIC_UUID, buildAdaptiveAncCommand(false));
                    write(WRITE_CHARACTERISTIC_UUID,
                            buildAncLevelCommand(prefs.getInt(PREF_ANC_LEVEL, 4)));
                }
            } else if (PREF_ANC_LEVEL.equals(config)) {
                prefs.edit().putBoolean(PREF_ADAPTIVE_ANC, false).apply();
                write(WRITE_CHARACTERISTIC_UUID, buildAdaptiveAncCommand(false));
                write(WRITE_CHARACTERISTIC_UUID,
                        buildAncLevelCommand(prefs.getInt(PREF_ANC_LEVEL, 4)));
            } else if (PREF_AUTO_COMPENSATION.equals(config)) {
                final boolean autoComp = prefs.getBoolean(PREF_AUTO_COMPENSATION, true);
                write(WRITE_CHARACTERISTIC_UUID, buildAutoCompensationCommand(autoComp));
            } else if (PREF_SOUND_MODE.equals(config) && mode == SOUND_MODE_ANC) {
                final boolean adaptive = prefs.getBoolean(PREF_ADAPTIVE_ANC, false);
                if (adaptive) {
                    write(WRITE_CHARACTERISTIC_UUID, buildAdaptiveAncCommand(true));
                } else {
                    write(WRITE_CHARACTERISTIC_UUID, buildAdaptiveAncCommand(false));
                    write(WRITE_CHARACTERISTIC_UUID,
                            buildAncLevelCommand(prefs.getInt(PREF_ANC_LEVEL, 4)));
                }
            } else if (mode == SOUND_MODE_AMBIENT && (PREF_AMBIENT_LEVEL.equals(config) || PREF_SOUND_MODE.equals(config))) {
                write(WRITE_CHARACTERISTIC_UUID,
                        buildAmbientLevelCommand(prefs.getInt(PREF_AMBIENT_LEVEL, 4)));
            } else if (isEqBandPref(config)) {
                mainHandler.removeCallbacks(writeCustomEq);
                mainHandler.postDelayed(writeCustomEq, EQ_WRITE_DELAY_MS);
            } else if (PREF_SPATIAL_AUDIO.equals(config)) {
                final int spatial = Integer.parseInt(prefs.getString(PREF_SPATIAL_AUDIO, "0"));
                write(WRITE_CHARACTERISTIC_UUID, buildSpatialAudioCommand(spatial));
            } else if (PREF_LATENCY_MODE.equals(config)) {
                final int latencyMode = Integer.parseInt(prefs.getString(PREF_LATENCY_MODE, "0"));
                write(WRITE_CHARACTERISTIC_UUID, buildLatencyModeCommand(latencyMode));
            } else if (PREF_CALL_SOUND_EFFECT.equals(config)) {
                final int callEffect = Integer.parseInt(prefs.getString(PREF_CALL_SOUND_EFFECT, "0"));
                write(WRITE_CHARACTERISTIC_UUID, buildCallSoundEffectCommand(callEffect));
            } else if (PREF_CALL_VOLUME_OPTIMIZER.equals(config)) {
                final boolean opt = prefs.getBoolean(PREF_CALL_VOLUME_OPTIMIZER, false);
                write(WRITE_CHARACTERISTIC_UUID, buildCallVolumeOptimizerCommand(opt));
            } else if (PREF_SIDETONE.equals(config)) {
                final int sidetoneLevel = Integer.parseInt(prefs.getString(PREF_SIDETONE, "0"));
                write(WRITE_CHARACTERISTIC_UUID, buildSidetoneCommand(sidetoneLevel));
            } else if (PREF_AUTO_POWER_OFF.equals(config)) {
                final int autoOff = Integer.parseInt(prefs.getString(PREF_AUTO_POWER_OFF, "0"));
                write(WRITE_CHARACTERISTIC_UUID, buildAutoPowerOffCommand(autoOff));
            } else if (PREF_SAFE_SOUND.equals(config)) {
                final boolean safeSound = prefs.getBoolean(PREF_SAFE_SOUND, false);
                write(WRITE_CHARACTERISTIC_UUID, buildSafeSoundCommand(safeSound));
            } else if (PREF_CHANNEL_BALANCE.equals(config)) {
                final int balance = prefs.getInt(PREF_CHANNEL_BALANCE, 0);
                write(WRITE_CHARACTERISTIC_UUID, buildChannelBalanceCommand(balance));
            } else if (PREF_GESTURE_LEFT.equals(config)) {
                final int gestureRole = Integer.parseInt(prefs.getString(PREF_GESTURE_LEFT, "1"));
                write(WRITE_CHARACTERISTIC_UUID, buildGestureCommand(true, gestureRole));
            } else if (PREF_GESTURE_RIGHT.equals(config)) {
                final int gestureRole = Integer.parseInt(prefs.getString(PREF_GESTURE_RIGHT, "2"));
                write(WRITE_CHARACTERISTIC_UUID, buildGestureCommand(false, gestureRole));
            } else if (PREF_ASC_CYCLE.equals(config)) {
                final String leftRole = prefs.getString(PREF_GESTURE_LEFT, "1");
                final String rightRole = prefs.getString(PREF_GESTURE_RIGHT, "2");
                if (String.valueOf(GESTURE_ROLE_AMBIENT_SOUND_CONTROL).equals(leftRole)) {
                    write(WRITE_CHARACTERISTIC_UUID, buildGestureCommand(true, GESTURE_ROLE_AMBIENT_SOUND_CONTROL));
                }
                if (String.valueOf(GESTURE_ROLE_AMBIENT_SOUND_CONTROL).equals(rightRole)) {
                    write(WRITE_CHARACTERISTIC_UUID, buildGestureCommand(false, GESTURE_ROLE_AMBIENT_SOUND_CONTROL));
                }
            } else if (PREF_VOICE_PROMPTS.equals(config)) {
                final int vpType = Integer.parseInt(prefs.getString(PREF_VOICE_PROMPTS, "0"));
                write(WRITE_CHARACTERISTIC_UUID, buildVoicePromptsCommand(vpType));
            }
        } catch (final RuntimeException e) {
            LOG.error("Failed to send JBL configuration", e);
        }
    }

    private boolean isEqBandPref(final String config) {
        return PREF_EQ_BAND_32.equals(config)
                || PREF_EQ_BAND_64.equals(config)
                || PREF_EQ_BAND_125.equals(config)
                || PREF_EQ_BAND_250.equals(config)
                || PREF_EQ_BAND_500.equals(config)
                || PREF_EQ_BAND_1K.equals(config)
                || PREF_EQ_BAND_2K.equals(config)
                || PREF_EQ_BAND_4K.equals(config)
                || PREF_EQ_BAND_8K.equals(config)
                || PREF_EQ_BAND_16K.equals(config);
    }

    // ─── Command builders ──────────────────────────────────────────────────

    private byte[] buildModeCommand(final int mode) {
        switch (mode) {
            case SOUND_MODE_ANC:
                return buildPacket(CMD_NOISE_CONTROL, SUB_NOISE_CONTROL_SET_MODES,
                        SLOT_ANC, (byte) 1, SLOT_AMBIENT, (byte) 0, SLOT_TALKTHRU, (byte) 0);
            case SOUND_MODE_TALKTHRU:
                return buildPacket(CMD_NOISE_CONTROL, SUB_NOISE_CONTROL_SET_MODES,
                        SLOT_ANC, (byte) 0, SLOT_AMBIENT, (byte) 0, SLOT_TALKTHRU, (byte) 1);
            case SOUND_MODE_AMBIENT:
                return buildPacket(CMD_NOISE_CONTROL, SUB_NOISE_CONTROL_SET_MODES,
                        SLOT_ANC, (byte) 0, SLOT_AMBIENT, (byte) 1, SLOT_TALKTHRU, (byte) 0);
            default:
                return buildPacket(CMD_NOISE_CONTROL, SUB_NOISE_CONTROL_SET_OFF);
        }
    }

    private byte[] buildAdaptiveAncCommand(final boolean enabled) {
        return buildPacket(CMD_NOISE_CONTROL, SUB_NOISE_CONTROL_SET_ADVANCED,
                FIELD_ADAPTIVE_ANC, (byte) (enabled ? 1 : 0));
    }

    private byte[] buildAncLevelCommand(final int level) {
        LOG.info("JBL Tune Buds 2: Setting Manual ANC Level: {}", level);
        final byte clamped = (byte) Math.max(1, Math.min(7, level));
        return buildPacket(CMD_NOISE_CONTROL, SUB_NOISE_CONTROL_SET_ADVANCED,
                FIELD_MANUAL_ANC_LEVEL, clamped);
    }

    private byte[] buildAmbientLevelCommand(final int level) {
        final byte clamped = (byte) Math.max(1, Math.min(7, level));
        return buildPacket(CMD_NOISE_CONTROL, SUB_NOISE_CONTROL_SET_ADVANCED,
                FIELD_AMBIENT_AWARE_LEVEL, clamped);
    }

    private byte[] buildAutoCompensationCommand(final boolean enabled) {
        LOG.info("JBL Tune Buds 2: Setting Auto Leakage Compensation: {}", enabled);
        return buildPacket(CMD_NOISE_CONTROL, SUB_NOISE_CONTROL_SET_ADVANCED,
                FIELD_AUTO_LEAKAGE_COMPENSATION, (byte) (enabled ? 1 : 0));
    }

    private byte[] buildCustomEqCommand() {
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress());
        return buildEqSettingsCommand(EQ_CATEGORY_CUSTOMIZED, prefs);
    }

    private byte[] buildEqSettingsCommand(final int category, final SharedPreferences prefs) {
        final int bandCount = prefs == null ? 0 : CUSTOM_EQ_BAND_COUNT;
        final byte[] payload = new byte[EQ_SETTINGS_HEADER_LENGTH + bandCount * EQ_BAND_LENGTH];
        final ByteBuffer pBuf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        pBuf.put(EQ_SET_Q_MULTIPLY_10);
        pBuf.put((byte) category);
        pBuf.putFloat(0.0f);
        pBuf.put((byte) 48);
        pBuf.putFloat(0.0f);
        pBuf.putFloat(0.0f);
        pBuf.put((byte) bandCount);

        for (int i = 0; i < bandCount; i++) {
            final int gainDb = prefs.getInt(EQ_BAND_PREFS[i], 0);
            pBuf.put((byte) 1);
            pBuf.putFloat((float) gainDb);
            pBuf.putFloat(CUSTOM_EQ_FREQUENCIES[i]);
            pBuf.put((byte) 10);
        }

        final ByteBuffer packet = ByteBuffer.allocate(payload.length + 4).order(ByteOrder.LITTLE_ENDIAN);
        packet.put(HEADER_MAGIC);
        packet.put(CMD_EQ_SETTINGS_2);
        packet.putShort((short) payload.length);
        packet.put(payload);
        return packet.array();
    }

    private byte[] buildCallSoundEffectCommand(final int effectType) {
        LOG.info("JBL Tune Buds 2: Setting Call Sound Effect: {}", effectType);
        return buildDeviceInfoSet(FEATURE_CALL_SOUND_EFFECT, (byte) 0x01, (byte) effectType);
    }

    private byte[] buildCallVolumeOptimizerCommand(final boolean enabled) {
        LOG.info("JBL Tune Buds 2: Setting Call Volume Optimizer: {}", enabled);
        return buildDeviceInfoSet(FEATURE_CALL_VOLUME_OPTIMIZER, (byte) (enabled ? 1 : 0));
    }

    private byte[] buildSpatialAudioCommand(final int spatial) {
        LOG.info("JBL Tune Buds 2: Setting Spatial Audio: {}", spatial);
        final boolean isOn = (spatial > 0);
        final int type = spatial == SPATIAL_AUDIO_MUSIC ? 2
                : spatial == SPATIAL_AUDIO_MOVIE ? 1
                : spatial == SPATIAL_AUDIO_GAME ? 3 : 0;
        return buildDeviceInfoSet(FEATURE_SPATIAL_SOUND,
                (byte) (isOn ? 1 : 0), (byte) type);
    }

    private byte[] buildLatencyModeCommand(final int modeVal) {
        LOG.info("JBL Tune Buds 2: Setting Latency mode: {}", modeVal);
        if (modeVal == LATENCY_MODE_AUDIO) {
            return buildPacket(CMD_SMART_AUDIO_VIDEO, SUB_CMD_SET,
                    (byte) 0x00, (byte) 0x01, (byte) 0x2e, (byte) 0x00, (byte) 0x18, (byte) 0x01, (byte) 0xff, (byte) 0xff);
        } else {
            return buildPacket(CMD_SMART_AUDIO_VIDEO, SUB_CMD_SET,
                    (byte) 0xc5, (byte) 0x00, (byte) 0x2e, (byte) 0x00, (byte) 0x50, (byte) 0x00, (byte) 0xff, (byte) 0xff);
        }
    }

    private byte[] buildSidetoneCommand(final int level) {
        final int clampedLevel = Math.max(0, Math.min(3, level));
        LOG.info("JBL Tune Buds 2: Setting Sidetone level: {}", clampedLevel);
        return buildDeviceInfoSet(FEATURE_VOICE_AWARE_SIDE_TONE,
                (byte) (clampedLevel > 0 ? 1 : 0), (byte) clampedLevel);
    }

    private byte[] buildAutoPowerOffCommand(final int autoOffIndex) {
        int minutes;
        switch (autoOffIndex) {
            case 1: minutes = 15; break;
            case 2: minutes = 30; break;
            case 3: minutes = 60; break;
            case 4: minutes = 120; break;
            default: minutes = 0; break;
        }
        LOG.info("JBL Tune Buds 2: Setting Auto Power Off minutes: {}", minutes);
        final boolean enabled = (minutes > 0);
        return buildDeviceInfoSet(FEATURE_AUTO_OFF,
                (byte) (enabled ? 1 : 0), (byte) minutes, (byte) (minutes >>> 8));
    }

    private byte[] buildSafeSoundCommand(final boolean enabled) {
        LOG.info("JBL Tune Buds 2: Setting Safe Sound: {}", enabled);
        return buildDeviceInfoSet(FEATURE_SAFE_SOUND, (byte) (enabled ? 1 : 0));
    }

    private byte[] buildChannelBalanceCommand(final int balance) {
        final int clamped = Math.max(-10, Math.min(10, balance));
        final byte rawLevel = (byte) (BALANCE_CENTER_OFFSET + clamped);
        return buildPacket(CMD_STEREO_BALANCE, SUB_CMD_SET, (byte) 0x01, (byte) 0x01, (byte) 0x02, rawLevel);
    }

    private byte[] buildVoicePromptsCommand(final int vpType) {
        LOG.info("JBL Tune Buds 2: Setting Voice Prompts: {}", vpType);
        final boolean language = vpType == VOICE_PROMPT_LANGUAGE;
        return buildPacket(CMD_VOICE_PROMPTS, (byte) 0x03, (byte) (language ? 1 : 0));
    }

    private byte[] buildGestureCommand(final boolean isLeft, final int role) {
        LOG.info("JBL Tune Buds 2: Setting Gesture role for {}: {}", isLeft ? "Left" : "Right", role);
        final byte tapSlot = isLeft ? GESTURE_SLOT_LEFT_SINGLE_TAP : GESTURE_SLOT_RIGHT_SINGLE_TAP;
        final byte doubleSlot = isLeft ? GESTURE_SLOT_LEFT_DOUBLE_TAP : GESTURE_SLOT_RIGHT_DOUBLE_TAP;
        final byte tripleSlot = isLeft ? GESTURE_SLOT_LEFT_TRIPLE_TAP : GESTURE_SLOT_RIGHT_TRIPLE_TAP;
        final byte holdSlot = isLeft ? GESTURE_SLOT_LEFT_HOLD : GESTURE_SLOT_RIGHT_HOLD;

        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress());
        int ascCycleAction = GESTURE_ACTION_ANC_AMBIENT_AWARE_OFF;
        try {
            ascCycleAction = Integer.parseInt(prefs.getString(PREF_ASC_CYCLE, "9"));
        } catch (final NumberFormatException ignored) {
        }

        byte aTap = GESTURE_ACTION_NONE;
        byte aDouble = GESTURE_ACTION_NONE;
        byte aTriple = GESTURE_ACTION_NONE;
        byte aHold = GESTURE_ACTION_TALK_TO_DEFAULT_ASSISTANT;

        switch (role) {
            case GESTURE_ROLE_AMBIENT_SOUND_CONTROL:
                aTap = (byte) ascCycleAction;
                aDouble = GESTURE_ACTION_TALKTHRU;
                aTriple = GESTURE_ACTION_NONE;
                aHold = GESTURE_ACTION_TALK_TO_DEFAULT_ASSISTANT;
                break;
            case GESTURE_ROLE_PLAYBACK_CONTROL:
                aTap = GESTURE_ACTION_PLAY_PAUSE;
                aDouble = GESTURE_ACTION_NEXT_TRACK;
                aTriple = GESTURE_ACTION_PREV_TRACK;
                aHold = GESTURE_ACTION_TALK_TO_DEFAULT_ASSISTANT;
                break;
            case GESTURE_ROLE_VOLUME_CONTROL:
                aTap = GESTURE_ACTION_VOLUME_UP;
                aDouble = GESTURE_ACTION_VOLUME_DOWN;
                aTriple = GESTURE_ACTION_NONE;
                aHold = GESTURE_ACTION_TALK_TO_DEFAULT_ASSISTANT;
                break;
            case GESTURE_ROLE_VOICE_ASSISTANT:
                aTap = GESTURE_ACTION_NONE;
                aDouble = GESTURE_ACTION_NONE;
                aTriple = GESTURE_ACTION_NONE;
                aHold = GESTURE_ACTION_TALK_TO_DEFAULT_ASSISTANT;
                break;
            default:
                aTap = GESTURE_ACTION_NONE;
                aDouble = GESTURE_ACTION_NONE;
                aTriple = GESTURE_ACTION_NONE;
                aHold = GESTURE_ACTION_NONE;
                break;
        }

        LOG.info("JBL Tune Buds 2: gesture packet role={} ascCycleAction={} aTap={} tapSlot={}", role, ascCycleAction, aTap, tapSlot);
        return buildPacket(CMD_GESTURE_CONFIG,
                (byte) 0x00,
                tapSlot, aTap,
                doubleSlot, aDouble,
                tripleSlot, aTriple,
                holdSlot, aHold);
    }

    @Override
    public void onFindDevice(final boolean start) {
        LOG.info("JBL Tune Buds 2: onFindDevice: {}", start);
        if (start) {
            write(WRITE_CHARACTERISTIC_UUID, buildPacket(CMD_EAR_BEEPING, BEEP_START_LEFT));
            write(WRITE_CHARACTERISTIC_UUID, buildPacket(CMD_EAR_BEEPING, BEEP_START_RIGHT));
        } else {
            write(WRITE_CHARACTERISTIC_UUID, buildPacket(CMD_EAR_BEEPING, BEEP_STOP_LEFT));
            write(WRITE_CHARACTERISTIC_UUID, buildPacket(CMD_EAR_BEEPING, BEEP_STOP_RIGHT));
        }
    }

    // ─── Response parsers ──────────────────────────────────────────────────

    private void parseBatteryResponse(final byte[] value) {
        if (value == null || value.length < 10
                || value[0] != HEADER_MAGIC
                || value[1] != CMD_BATTERY_STATUS
                || value[3] != SUB_CMD_GET) {
            return;
        }

        final int leftStatus  = value[4] & 0xff;
        final int rightStatus = value[5] & 0xff;
        final int leftLevel   = value[6] & 0xff;
        final int rightLevel  = value[7] & 0xff;
        final int caseStatus  = value[8] & 0xff;
        final int caseLevel   = value[9] & 0xff;

        handleGBDeviceEvent(createBatteryEvent(0, leftLevel, leftStatus));
        handleGBDeviceEvent(createBatteryEvent(1, rightLevel, rightStatus));
        if (caseLevel > 0) {
            handleGBDeviceEvent(createBatteryEvent(2, caseLevel, caseStatus));
        }
    }

    private void parseGestureResponse(final byte[] value) {
        if (value == null || value.length < 4
                || value[0] != HEADER_MAGIC
                || value[1] != CMD_GESTURE_CONFIG) {
            return;
        }
        final int payloadLength = value[2] & 0xff;
        final int payloadEnd = Math.min(value.length, 3 + payloadLength);
        if (payloadEnd < 4 || value[3] != SUB_CMD_SET) {
            return;
        }
        final int[] actions = new int[15];
        java.util.Arrays.fill(actions, -1);
        for (int i = 4; i + 1 < payloadEnd; i += 2) {
            final int slot = value[i] & 0xff;
            if (slot < actions.length) {
                actions[slot] = value[i + 1] & 0xff;
            }
        }

        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress());
        final SharedPreferences.Editor editor = prefs.edit();
        boolean modified = updateGestureRole(actions, true, prefs, editor);
        modified |= updateGestureRole(actions, false, prefs, editor);
        if (modified) {
            editor.apply();
            getDevice().sendDeviceUpdateIntent(getContext());
        }
    }

    private boolean updateGestureRole(final int[] actions,
                                     final boolean left,
                                     final SharedPreferences prefs,
                                     final SharedPreferences.Editor editor) {
        final int tapSlot = left ? GESTURE_SLOT_LEFT_SINGLE_TAP : GESTURE_SLOT_RIGHT_SINGLE_TAP;
        final int doubleSlot = left ? GESTURE_SLOT_LEFT_DOUBLE_TAP : GESTURE_SLOT_RIGHT_DOUBLE_TAP;
        final int tripleSlot = left ? GESTURE_SLOT_LEFT_TRIPLE_TAP : GESTURE_SLOT_RIGHT_TRIPLE_TAP;
        final int holdSlot = left ? GESTURE_SLOT_LEFT_HOLD : GESTURE_SLOT_RIGHT_HOLD;
        final int tap = actions[tapSlot];
        final int doubleTap = actions[doubleSlot];
        final int tripleTap = actions[tripleSlot];
        final int hold = actions[holdSlot];
        if (tap < 0 || doubleTap < 0 || tripleTap < 0 || hold < 0) {
            return false;
        }

        final String prefKey = left ? PREF_GESTURE_LEFT : PREF_GESTURE_RIGHT;
        final int role;
        boolean modified = false;
        if (tap == GESTURE_ACTION_PLAY_PAUSE
                && doubleTap == GESTURE_ACTION_NEXT_TRACK
                && tripleTap == GESTURE_ACTION_PREV_TRACK) {
            role = GESTURE_ROLE_PLAYBACK_CONTROL;
        } else if (tap == GESTURE_ACTION_VOLUME_UP
                && doubleTap == GESTURE_ACTION_VOLUME_DOWN
                && tripleTap == GESTURE_ACTION_NONE) {
            role = GESTURE_ROLE_VOLUME_CONTROL;
        } else if ((tap == GESTURE_ACTION_ANC_AMBIENT_AWARE_OFF
                    || tap == GESTURE_ACTION_ANC_AMBIENT
                    || tap == GESTURE_ACTION_ANC_OFF
                    || tap == GESTURE_ACTION_AMBIENT_AWARE_OFF)
                && doubleTap == GESTURE_ACTION_TALKTHRU
                && tripleTap == GESTURE_ACTION_NONE) {
            role = GESTURE_ROLE_AMBIENT_SOUND_CONTROL;
            final String ascAction = String.valueOf(tap);
            if (!ascAction.equals(prefs.getString(PREF_ASC_CYCLE, "9"))) {
                editor.putString(PREF_ASC_CYCLE, ascAction);
                modified = true;
            }
        } else if (tap == GESTURE_ACTION_NONE
                && doubleTap == GESTURE_ACTION_NONE
                && tripleTap == GESTURE_ACTION_NONE
                && hold == GESTURE_ACTION_TALK_TO_DEFAULT_ASSISTANT) {
            role = GESTURE_ROLE_VOICE_ASSISTANT;
        } else if (tap == GESTURE_ACTION_NONE
                && doubleTap == GESTURE_ACTION_NONE
                && tripleTap == GESTURE_ACTION_NONE
                && hold == GESTURE_ACTION_NONE) {
            role = GESTURE_ROLE_OFF;
        } else {
            return false;
        }

        final String roleValue = String.valueOf(role);
        if (!roleValue.equals(prefs.getString(prefKey, null))) {
            editor.putString(prefKey, roleValue);
            modified = true;
        }
        return modified;
    }

    private void parseSoundModeResponse(final byte[] value) {
        if (value == null || value.length < 5
                || value[0] != HEADER_MAGIC
                || value[1] != CMD_NOISE_CONTROL
                || value[3] != SUB_NOISE_CONTROL_RESP_MODES) {
            return;
        }

        final int payloadLen = value[2] & 0xff;
        final int fieldEnd = 3 + payloadLen;
        int mode = SOUND_MODE_OFF;
        int i = 4;
        while (i + 1 < fieldEnd && i + 1 < value.length) {
            final int slot = value[i] & 0xff;
            final int enabled = value[i + 1] & 0xff;
            if (enabled != 0) {
                if (slot == SLOT_ANC) {
                    mode = SOUND_MODE_ANC;
                } else if (slot == SLOT_AMBIENT) {
                    mode = SOUND_MODE_AMBIENT;
                } else if (slot == SLOT_TALKTHRU) {
                    mode = SOUND_MODE_TALKTHRU;
                }
            }
            i += 2;
        }

        LOG.info("JBL sound mode from device: {}", mode);

        if (mRequestedAnc) {
            mRequestedAnc = false;
            if (mode != SOUND_MODE_ANC) {
                LOG.info("JBL: Firmware rejected ANC request (mono mode active); showing toast");
                GB.toast(getContext().getString(nodomain.freeyourgadget.gadgetbridge.R.string.pref_mono_mode_anc_disabled),
                        android.widget.Toast.LENGTH_SHORT, GB.INFO);
            }
        }

        final SharedPreferences prefs =
                GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress());
        final String modeStr = String.valueOf(mode);
        if (!modeStr.equals(prefs.getString(PREF_SOUND_MODE, null))) {
            prefs.edit().putString(PREF_SOUND_MODE, modeStr).apply();
            getDevice().sendDeviceUpdateIntent(getContext());
        }
    }

    private void parseAdvancedAncResponse(final byte[] value) {
        if (value == null || value.length < 5
                || value[0] != HEADER_MAGIC
                || value[1] != CMD_NOISE_CONTROL
                || value[3] != SUB_NOISE_CONTROL_RESP_ADVANCED) {
            return;
        }

        final SharedPreferences prefs =
                GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress());
        final SharedPreferences.Editor editor = prefs.edit();
        boolean modified = false;

        final int payloadLen = value[2] & 0xff;
        final int fieldEnd   = 3 + payloadLen;
        int i = 4;
        while (i + 1 < fieldEnd && i + 1 < value.length) {
            final int fieldType  = value[i]     & 0xff;
            final int fieldValue = value[i + 1] & 0xff;
            switch ((byte) fieldType) {
                case FIELD_ADAPTIVE_ANC:
                    LOG.info("JBL: adaptive ANC from device = {}", fieldValue);
                    final boolean isAdaptive = (fieldValue == 1);
                    if (prefs.getBoolean(PREF_ADAPTIVE_ANC, false) != isAdaptive) {
                        editor.putBoolean(PREF_ADAPTIVE_ANC, isAdaptive);
                        modified = true;
                    }
                    break;
                case FIELD_MANUAL_ANC_LEVEL:
                    LOG.info("JBL: manual ANC level from device = {}", fieldValue);
                    if (fieldValue >= 1 && fieldValue <= 7 && prefs.getInt(PREF_ANC_LEVEL, 4) != fieldValue) {
                        editor.putInt(PREF_ANC_LEVEL, fieldValue);
                        modified = true;
                    }
                    break;
                case FIELD_AUTO_LEAKAGE_COMPENSATION:
                    LOG.info("JBL: auto leakage compensation from device = {}", fieldValue);
                    final boolean isAutoComp = (fieldValue == 1);
                    if (prefs.getBoolean(PREF_AUTO_COMPENSATION, true) != isAutoComp) {
                        editor.putBoolean(PREF_AUTO_COMPENSATION, isAutoComp);
                        modified = true;
                    }
                    break;
                case FIELD_AMBIENT_AWARE_LEVEL:
                    LOG.info("JBL: ambient level from device = {}", fieldValue);
                    if (fieldValue >= 1 && fieldValue <= 7 && prefs.getInt(PREF_AMBIENT_LEVEL, 4) != fieldValue) {
                        editor.putInt(PREF_AMBIENT_LEVEL, fieldValue);
                        modified = true;
                    }
                    break;
                default:
                    break;
            }
            i += 2;
        }

        if (modified) {
            editor.apply();
            getDevice().sendDeviceUpdateIntent(getContext());
        }
    }

    private void parseDeviceConfigResponse(final byte[] value) {
        if (value == null || value.length < 6
                || value[0] != HEADER_MAGIC
                || value[1] != CMD_DEVICE_CONFIG) {
            return;
        }

        final int payloadType = value[4] & 0xff;
        if (payloadType != SUB_CMD_RET && payloadType != SUB_CMD_SET) {
            return;
        }
        final int payloadLength = (value[2] & 0xff) | ((value[3] & 0xff) << 8);
        final int payloadEnd = Math.min(value.length, 4 + payloadLength);

        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress());
        final SharedPreferences.Editor editor = prefs.edit();
        boolean modified = false;

        int i = 5;
        while (i + 2 < payloadEnd) {
            final int featureId = ((value[i] & 0xff) << 8) | (value[i + 1] & 0xff);
            final int len = value[i + 2] & 0xff;
            if (i + 3 + len > payloadEnd) {
                break;
            }
            final int dataOffset = i + 3;
            if ((featureId == (FEATURE_BATTERY_LEFT & 0xff)
                    || featureId == (FEATURE_BATTERY_RIGHT & 0xff)
                    || featureId == (FEATURE_BATTERY_CASE & 0xff)) && len >= 2) {
                final int batteryIndex = featureId == (FEATURE_BATTERY_LEFT & 0xff) ? 0
                        : featureId == (FEATURE_BATTERY_RIGHT & 0xff) ? 1 : 2;
                handleGBDeviceEvent(createBatteryEvent(batteryIndex,
                        value[dataOffset + 1] & 0xff, value[dataOffset] & 0xff));
            } else if (featureId == (FEATURE_AUTO_OFF & 0xff) && len >= 3) {
                final boolean enabled = (value[dataOffset] & 0xff) == 1;
                final int minutes = (value[dataOffset + 1] & 0xff)
                        | ((value[dataOffset + 2] & 0xff) << 8);
                final int index = !enabled ? 0
                        : minutes <= 15 ? 1
                        : minutes <= 30 ? 2
                        : minutes <= 60 ? 3 : 4;
                final String autoOff = String.valueOf(index);
                if (!autoOff.equals(prefs.getString(PREF_AUTO_POWER_OFF, "0"))) {
                    editor.putString(PREF_AUTO_POWER_OFF, autoOff);
                    modified = true;
                }
            } else if (featureId == (FEATURE_SPATIAL_SOUND & 0xff) && len >= 2) {
                final boolean enabled = (value[dataOffset] & 0xff) == 1;
                final int rawType = value[dataOffset + 1] & 0xff;
                final int type = rawType == 1 ? SPATIAL_AUDIO_MOVIE
                        : rawType == 2 ? SPATIAL_AUDIO_MUSIC
                        : rawType == 3 ? SPATIAL_AUDIO_GAME : SPATIAL_AUDIO_OFF;
                final String spatial = String.valueOf(enabled ? type : SPATIAL_AUDIO_OFF);
                if (!spatial.equals(prefs.getString(PREF_SPATIAL_AUDIO, "0"))) {
                    editor.putString(PREF_SPATIAL_AUDIO, spatial);
                    modified = true;
                }
            } else if (featureId == (FEATURE_VOICE_AWARE_SIDE_TONE & 0xff) && len >= 2) {
                final int level = (value[dataOffset] & 0xff) == 1 ? value[dataOffset + 1] & 0xff : 0;
                final String sidetone = String.valueOf(level);
                if (!sidetone.equals(prefs.getString(PREF_SIDETONE, "0"))) {
                    editor.putString(PREF_SIDETONE, sidetone);
                    modified = true;
                }
            } else if (featureId == (FEATURE_CALL_SOUND_EFFECT & 0xff) && len >= 2) {
                final int type = value[dataOffset + 1] & 0xff;
                final String typeStr = String.valueOf(type);
                if (!typeStr.equals(prefs.getString(PREF_CALL_SOUND_EFFECT, null))) {
                    editor.putString(PREF_CALL_SOUND_EFFECT, typeStr);
                    modified = true;
                }
            } else if (featureId == (FEATURE_CALL_VOLUME_OPTIMIZER & 0xff) && len >= 1) {
                final boolean isOpt = (value[dataOffset] & 0xff) == 1;
                if (prefs.getBoolean(PREF_CALL_VOLUME_OPTIMIZER, false) != isOpt) {
                    editor.putBoolean(PREF_CALL_VOLUME_OPTIMIZER, isOpt);
                    modified = true;
                }
            } else if (featureId == (FEATURE_SAFE_SOUND & 0xff) && len >= 1) {
                final boolean enabled = (value[dataOffset] & 0xff) != 0;
                if (prefs.getBoolean(PREF_SAFE_SOUND, false) != enabled) {
                    editor.putBoolean(PREF_SAFE_SOUND, enabled);
                    modified = true;
                }
            }
            i += 3 + len;
        }

        if (modified) {
            editor.apply();
            getDevice().sendDeviceUpdateIntent(getContext());
        }
    }

    private void parseEqResponse(final byte[] value) {
        if (value == null || value.length < 3 || value[0] != HEADER_MAGIC) {
            return;
        }

        if (value[1] == CMD_EQ_SETTINGS_2) {
            parseEqSettings2Response(value);
        }
    }

    private void parseEqSettings2Response(final byte[] value) {
        if (value.length < 6) {
            return;
        }
        final int payloadLength = (value[2] & 0xff) | ((value[3] & 0xff) << 8);
        if (payloadLength != value.length - 4 || payloadLength < 2) {
            return;
        }
        final int responseType = value[4] & 0xff;
        if (responseType != (SUB_CMD_RET & 0xff) && responseType != (EQ_RET_Q_MULTIPLY_10 & 0xff)) {
            return;
        }

        final int category = value[5] & 0xff;
        if (category != EQ_CATEGORY_CUSTOMIZED) {
            return;
        }
        final int bandCount = payloadLength >= EQ_SETTINGS_HEADER_LENGTH ? value[19] & 0xff : 0;
        if (payloadLength != EQ_SETTINGS_HEADER_LENGTH + bandCount * EQ_BAND_LENGTH) {
            return;
        }

        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress());
        final SharedPreferences.Editor editor = prefs.edit();
        boolean modified = false;
        final ByteBuffer data = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < bandCount; i++) {
            final int bandOffset = 20 + i * EQ_BAND_LENGTH;
            final float gain = data.getFloat(bandOffset + 1);
            final float frequency = data.getFloat(bandOffset + 5);
            final int preferenceIndex = findEqBandPreference(frequency);
            if (preferenceIndex >= 0) {
                final int gainDb = Math.max(-6, Math.min(6, Math.round(gain)));
                if (prefs.getInt(EQ_BAND_PREFS[preferenceIndex], 0) != gainDb) {
                    editor.putInt(EQ_BAND_PREFS[preferenceIndex], gainDb);
                    modified = true;
                }
            }
        }

        if (modified) {
            editor.apply();
            getDevice().sendDeviceUpdateIntent(getContext());
        }
    }

    private int findEqBandPreference(final float frequency) {
        for (int i = 0; i < CUSTOM_EQ_BAND_COUNT; i++) {
            if (Math.abs(CUSTOM_EQ_FREQUENCIES[i] - frequency) < 0.5f) {
                return i;
            }
        }
        return -1;
    }

    private byte[] buildEqSettingsCommand(final byte[] response, final int payloadLength, final int responseType) {
        final byte[] payload = new byte[payloadLength];
        System.arraycopy(response, 4, payload, 0, payloadLength);
        payload[0] = EQ_SET_Q_MULTIPLY_10;
        if (responseType == (SUB_CMD_RET & 0xff)) {
            final int bandCount = payload[15] & 0xff;
            for (int i = 0; i < bandCount; i++) {
                final int qIndex = EQ_SETTINGS_HEADER_LENGTH + i * EQ_BAND_LENGTH + EQ_BAND_LENGTH - 1;
                payload[qIndex] = (byte) Math.min(0xff, (payload[qIndex] & 0xff) * 10);
            }
        }

        final ByteBuffer packet = ByteBuffer.allocate(payloadLength + 4).order(ByteOrder.LITTLE_ENDIAN);
        packet.put(HEADER_MAGIC);
        packet.put(CMD_EQ_SETTINGS_2);
        packet.putShort((short) payloadLength);
        packet.put(payload);
        return packet.array();
    }

    private void parseSpatial3DResponse(final byte[] value) {
        if (value == null || value.length < 5
                || value[0] != HEADER_MAGIC
                || value[1] != CMD_SPATIAL_AUDIO) {
            return;
        }
        final int payloadType = value[3] & 0xff;
        if (payloadType != SUB_CMD_RET && payloadType != SUB_CMD_SET) {
            return;
        }
        final boolean isOn = (value[4] != 0);
        final int type = (value.length >= 6) ? (value[5] & 0xff) : 1;
        final int spatialVal = isOn ? type : SPATIAL_AUDIO_OFF;
        LOG.info("JBL: Spatial 3D from device = {} (on={}, type={})", spatialVal, isOn, type);
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress());
        final String currentVal = prefs.getString(PREF_SPATIAL_AUDIO, "0");
        final String newVal = String.valueOf(spatialVal);
        if (!newVal.equals(currentVal)) {
            prefs.edit().putString(PREF_SPATIAL_AUDIO, newVal).apply();
            getDevice().sendDeviceUpdateIntent(getContext());
        }
    }

    private void parseSmartSwitchResponse(final byte[] value) {
        if (value == null || value.length < 10
                || value[0] != HEADER_MAGIC
                || value[1] != CMD_SMART_AUDIO_VIDEO) {
            return;
        }
        final int latency = ((value[9] & 0xff) << 8) | (value[8] & 0xff);
        final int mode = (latency <= 100) ? LATENCY_MODE_VIDEO : LATENCY_MODE_AUDIO;
        LOG.info("JBL: Smart Switch / Latency mode from device = {} (latency={})", mode, latency);
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress());
        final String currentVal = prefs.getString(PREF_LATENCY_MODE, "0");
        final String newVal = String.valueOf(mode);
        if (!newVal.equals(currentVal)) {
            prefs.edit().putString(PREF_LATENCY_MODE, newVal).apply();
            getDevice().sendDeviceUpdateIntent(getContext());
        }
    }

    private void parseVoiceAwareResponse(final byte[] value) {
        if (value == null || value.length < 5
                || value[0] != HEADER_MAGIC
                || value[1] != CMD_SIDETONE) {
            return;
        }
        final int level = value[4] & 0xff;
        LOG.info("JBL: Sidetone level from device = {}", level);
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress());
        final String currentVal = prefs.getString(PREF_SIDETONE, "0");
        final String newVal = String.valueOf(level);
        if (!newVal.equals(currentVal)) {
            prefs.edit().putString(PREF_SIDETONE, newVal).apply();
            getDevice().sendDeviceUpdateIntent(getContext());
        }
    }

    private void parseAutoOffResponse(final byte[] value) {
        if (value == null || value.length < 4
                || value[0] != HEADER_MAGIC
                || value[1] != CMD_AUTO_POWER_OFF) {
            return;
        }
        final int raw = value[3] & 0xff;
        final boolean enabled = (raw & 0x80) != 0;
        final int minutes = raw & 0x7f;
        int idx = 0;
        if (enabled) {
            if (minutes <= 15) idx = 1;
            else if (minutes <= 30) idx = 2;
            else if (minutes <= 60) idx = 3;
            else idx = 4;
        }
        LOG.info("JBL: Auto Power Off from device: enabled={}, minutes={}, idx={}", enabled, minutes, idx);
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress());
        final String currentVal = prefs.getString(PREF_AUTO_POWER_OFF, "0");
        final String newVal = String.valueOf(idx);
        if (!newVal.equals(currentVal)) {
            prefs.edit().putString(PREF_AUTO_POWER_OFF, newVal).apply();
            getDevice().sendDeviceUpdateIntent(getContext());
        }
    }

    private void parseSafeSoundResponse(final byte[] value) {
        if (value == null || value.length < 6
                || value[0] != HEADER_MAGIC
                || value[1] != CMD_SAFE_SOUND) {
            return;
        }
        final boolean enabled = (value[5] != 0);
        LOG.info("JBL: Safe Sound from device = {}", enabled);
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress());
        if (prefs.getBoolean(PREF_SAFE_SOUND, false) != enabled) {
            prefs.edit().putBoolean(PREF_SAFE_SOUND, enabled).apply();
            getDevice().sendDeviceUpdateIntent(getContext());
        }
    }

    private void parseBalanceResponse(final byte[] value) {
        if (value == null || value.length < 8
                || value[0] != HEADER_MAGIC
                || value[1] != CMD_STEREO_BALANCE) {
            return;
        }
        final int rawLevel = value[7] & 0xff;
        final int balance = rawLevel - BALANCE_CENTER_OFFSET;
        LOG.info("JBL: Stereo Balance from device = {}", balance);
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress());
        if (prefs.getInt(PREF_CHANNEL_BALANCE, 0) != balance) {
            prefs.edit().putInt(PREF_CHANNEL_BALANCE, balance).apply();
            getDevice().sendDeviceUpdateIntent(getContext());
        }
    }

    private void parseVoicePromptsResponse(final byte[] value) {
        if (value == null || value.length < 5
                || value[0] != HEADER_MAGIC
                || value[1] != CMD_VOICE_PROMPTS) {
            return;
        }
        final int vpType = value[4] & 0xff;
        LOG.info("JBL: Voice Prompts type from device = {}", vpType);
        final SharedPreferences prefs = GBApplication.getDeviceSpecificSharedPrefs(getDevice().getAddress());
        final String currentVal = prefs.getString(PREF_VOICE_PROMPTS, "0");
        final String newVal = String.valueOf(vpType);
        if (!newVal.equals(currentVal)) {
            prefs.edit().putString(PREF_VOICE_PROMPTS, newVal).apply();
            getDevice().sendDeviceUpdateIntent(getContext());
        }
    }

    private GBDeviceEventBatteryInfo createBatteryEvent(final int index, final int level, final int status) {
        final GBDeviceEventBatteryInfo event = new GBDeviceEventBatteryInfo();
        event.batteryIndex = index;
        event.level = level >= 0 && level <= 100 ? level : GBDevice.BATTERY_UNKNOWN;
        event.state = status != 0 ? BatteryState.BATTERY_CHARGING : BatteryState.BATTERY_NORMAL;
        return event;
    }
}
