/*
 * Copyright (C) 2015 The Android Open Source Project
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
package android.car.media;

import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.annotation.TestApi;
import android.car.Car;
import android.car.CarLibLog;
import android.car.CarManagerBase;
import android.car.CarNotConnectedException;
import android.content.Context;
import android.media.AudioAttributes;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * APIs for handling audio in a car.
 *
 * In a car environment, we introduced the support to turn audio dynamic routing on /off by
 * setting the "audioUseDynamicRouting" attribute in config.xml
 *
 * When audio dynamic routing is enabled:
 * - Audio devices are grouped into zones
 * - There is at least one primary zone, and extra secondary zones such as RSE
 *   (Reat Seat Entertainment)
 * - Within each zone, audio devices are grouped into volume groups for volume control
 * - Audio is assigned to an audio device based on its AudioAttributes usage
 *
 * When audio dynamic routing is disabled:
 * - There is exactly one audio zone, which is the primary zone
 * - Each volume group represents a controllable STREAM_TYPE, same as AudioManager
 */
public final class CarAudioManager implements CarManagerBase {

    /**
     * Zone id of the primary audio zone.
     */
    public static final int PRIMARY_AUDIO_ZONE = 0x0;

    private final ICarAudio mService;
    private final List<CarVolumeCallback> mCarVolumeCallbacks;

    private final ICarVolumeCallback mCarVolumeCallbackImpl = new ICarVolumeCallback.Stub() {
        @Override
        public void onGroupVolumeChanged(int zoneId, int groupId, int flags) {
            for (CarVolumeCallback callback : mCarVolumeCallbacks) {
                callback.onGroupVolumeChanged(zoneId, groupId, flags);
            }
        }

        @Override
        public void onMasterMuteChanged(int zoneId, int flags) {
            for (CarVolumeCallback callback : mCarVolumeCallbacks) {
                callback.onMasterMuteChanged(zoneId, flags);
            }
        }
    };

    /**
     * @return Whether dynamic routing is enabled or not.
     * @hide
     */
    @TestApi
    public boolean isDynamicRoutingEnabled() throws CarNotConnectedException {
        try {
            return mService.isDynamicRoutingEnabled();
        } catch (RemoteException e) {
            Log.e(CarLibLog.TAG_CAR, "isDynamicRoutingEnabled failed", e);
            throw new CarNotConnectedException(e);
        }
    }

    /**
     * Sets the volume index for a volume group in primary zone.
     *
     * @see {@link #setGroupVolume(int, int, int, int)}
     */
    public void setGroupVolume(int groupId, int index, int flags) throws CarNotConnectedException {
        setGroupVolume(PRIMARY_AUDIO_ZONE, groupId, index, flags);
    }

    /**
     * Sets the volume index for a volume group.
     *
     * @param zoneId The zone id whose volume group is affected.
     * @param groupId The volume group id whose volume index should be set.
     * @param index The volume index to set. See
     *            {@link #getGroupMaxVolume(int, int)} for the largest valid value.
     * @param flags One or more flags (e.g., {@link android.media.AudioManager#FLAG_SHOW_UI},
     *              {@link android.media.AudioManager#FLAG_PLAY_SOUND})
     */
    @RequiresPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    public void setGroupVolume(int zoneId, int groupId, int index, int flags)
            throws CarNotConnectedException {
        try {
            mService.setGroupVolume(zoneId, groupId, index, flags);
        } catch (RemoteException e) {
            Log.e(CarLibLog.TAG_CAR, "setGroupVolume failed", e);
            throw new CarNotConnectedException(e);
        }
    }

    /**
     * Returns the maximum volume index for a volume group in primary zone.
     *
     * @see {@link #getGroupMaxVolume(int, int)}
     */
    public int getGroupMaxVolume(int groupId) throws CarNotConnectedException {
        return getGroupMaxVolume(PRIMARY_AUDIO_ZONE, groupId);
    }

    /**
     * Returns the maximum volume index for a volume group.
     *
     * @param zoneId The zone id whose volume group is queried.
     * @param groupId The volume group id whose maximum volume index is returned.
     * @return The maximum valid volume index for the given group.
     */
    @RequiresPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    public int getGroupMaxVolume(int zoneId, int groupId) throws CarNotConnectedException {
        try {
            return mService.getGroupMaxVolume(zoneId, groupId);
        } catch (RemoteException e) {
            Log.e(CarLibLog.TAG_CAR, "getGroupMaxVolume failed", e);
            throw new CarNotConnectedException(e);
        }
    }

    /**
     * Returns the minimum volume index for a volume group in primary zone.
     *
     * @see {@link #getGroupMinVolume(int, int)}
     */
    public int getGroupMinVolume(int groupId) throws CarNotConnectedException {
        return getGroupMinVolume(PRIMARY_AUDIO_ZONE, groupId);
    }

    /**
     * Returns the minimum volume index for a volume group.
     *
     * @param zoneId The zone id whose volume group is queried.
     * @param groupId The volume group id whose minimum volume index is returned.
     * @return The minimum valid volume index for the given group, non-negative
     */
    @RequiresPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    public int getGroupMinVolume(int zoneId, int groupId) throws CarNotConnectedException {
        try {
            return mService.getGroupMinVolume(zoneId, groupId);
        } catch (RemoteException e) {
            Log.e(CarLibLog.TAG_CAR, "getGroupMinVolume failed", e);
            throw new CarNotConnectedException(e);
        }
    }

    /**
     * Returns the current volume index for a volume group in primary zone.
     *
     * @see {@link #getGroupVolume(int, int)}
     */
    public int getGroupVolume(int groupId) throws CarNotConnectedException {
        return getGroupVolume(PRIMARY_AUDIO_ZONE, groupId);
    }

    /**
     * Returns the current volume index for a volume group.
     *
     * @param zoneId The zone id whose volume groups is queried.
     * @param groupId The volume group id whose volume index is returned.
     * @return The current volume index for the given group.
     *
     * @see #getGroupMaxVolume(int, int)
     * @see #setGroupVolume(int, int, int, int)
     */
    @RequiresPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    public int getGroupVolume(int zoneId, int groupId) throws CarNotConnectedException {
        try {
            return mService.getGroupVolume(zoneId, groupId);
        } catch (RemoteException e) {
            Log.e(CarLibLog.TAG_CAR, "getGroupVolume failed", e);
            throw new CarNotConnectedException(e);
        }
    }

    /**
     * Adjust the relative volume in the front vs back of the vehicle cabin.
     *
     * @param value in the range -1.0 to 1.0 for fully toward the back through
     *              fully toward the front.  0.0 means evenly balanced.
     *
     * @see #setBalanceTowardRight(float)
     */
    @RequiresPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    public void setFadeTowardFront(float value) throws CarNotConnectedException {
        try {
            mService.setFadeTowardFront(value);
        } catch (RemoteException e) {
            Log.e(CarLibLog.TAG_CAR, "setFadeTowardFront failed", e);
            throw new CarNotConnectedException(e);
        }
    }

    /**
     * Adjust the relative volume on the left vs right side of the vehicle cabin.
     *
     * @param value in the range -1.0 to 1.0 for fully toward the left through
     *              fully toward the right.  0.0 means evenly balanced.
     *
     * @see #setFadeTowardFront(float)
     */
    @RequiresPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    public void setBalanceTowardRight(float value) throws CarNotConnectedException {
        try {
            mService.setBalanceTowardRight(value);
        } catch (RemoteException e) {
            Log.e(CarLibLog.TAG_CAR, "setBalanceTowardRight failed", e);
            throw new CarNotConnectedException(e);
        }
    }

    /**
     * Queries the system configuration in order to report the available, non-microphone audio
     * input devices.
     *
     * @return An array of strings representing the available input ports.
     * Each port is identified by it's "address" tag in the audioPolicyConfiguration xml file.
     * Empty array if we find nothing.
     *
     * @see #createAudioPatch(String, int, int)
     * @see #releaseAudioPatch(CarAudioPatchHandle)
     */
    @RequiresPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    public @NonNull String[] getExternalSources() throws CarNotConnectedException {
        try {
            return mService.getExternalSources();
        } catch (RemoteException e) {
            Log.e(CarLibLog.TAG_CAR, "getExternalSources failed", e);
            throw new CarNotConnectedException(e);
        }
    }

    /**
     * Given an input port identified by getExternalSources(), request that it's audio signal
     * be routed below the HAL to the output port associated with the given usage.  For example,
     * The output of a tuner might be routed directly to the output buss associated with
     * AudioAttributes.USAGE_MEDIA while the tuner is playing.
     *
     * @param sourceAddress the input port name obtained from getExternalSources().
     * @param usage the type of audio represented by this source (usually USAGE_MEDIA).
     * @param gainInMillibels How many steps above the minimum value defined for the source port to
     *                       set the gain when creating the patch.
     *                       This may be used for source balancing without affecting the user
     *                       controlled volumes applied to the destination ports.  A value of
     *                       0 indicates no gain change is requested.
     * @return A handle for the created patch which can be used to later remove it.
     *
     * @see #getExternalSources()
     * @see #releaseAudioPatch(CarAudioPatchHandle)
     */
    @RequiresPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    public CarAudioPatchHandle createAudioPatch(String sourceAddress,
            @AudioAttributes.AttributeUsage int usage, int gainInMillibels)
            throws CarNotConnectedException {
        try {
            return mService.createAudioPatch(sourceAddress, usage, gainInMillibels);
        } catch (RemoteException e) {
            Log.e(CarLibLog.TAG_CAR, "createAudioPatch failed", e);
            throw new CarNotConnectedException(e);
        }
    }

    /**
     * Removes the association between an input port and an output port identified by the provided
     * handle.
     *
     * @param patch CarAudioPatchHandle returned from createAudioPatch().
     *
     * @see #getExternalSources()
     * @see #createAudioPatch(String, int, int)
     */
    @RequiresPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_SETTINGS)
    public void releaseAudioPatch(CarAudioPatchHandle patch) throws CarNotConnectedException {
        try {
            mService.releaseAudioPatch(patch);
        } catch (RemoteException e) {
            Log.e(CarLibLog.TAG_CAR, "releaseAudioPatch failed", e);
            throw new CarNotConnectedException(e);
        }
    }

    /**
     * Gets the count of available volume groups in primary zone.
     *
     * @see {@link #getVolumeGroupCount(int)}
     */
    public int getVolumeGroupCount() throws CarNotConnectedException {
        return getVolumeGroupCount(PRIMARY_AUDIO_ZONE);
    }

    /**
     * Gets the count of available volume groups in the system.
     *
     * @param zoneId The zone id whois count of volume groups is queried.
     * @return Count of volume groups
     */
    @RequiresPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    public int getVolumeGroupCount(int zoneId) throws CarNotConnectedException {
        try {
            return mService.getVolumeGroupCount(zoneId);
        } catch (RemoteException e) {
            Log.e(CarLibLog.TAG_CAR, "getVolumeGroupCount failed", e);
            throw new CarNotConnectedException(e);
        }
    }

    /**
     * Gets the volume group id for a given {@link AudioAttributes} usage in primary zone.
     *
     * @see {@link #getVolumeGroupIdForUsage(int, int)}
     */
    public int getVolumeGroupIdForUsage(@AudioAttributes.AttributeUsage int usage)
            throws CarNotConnectedException {
        return getVolumeGroupIdForUsage(PRIMARY_AUDIO_ZONE, usage);
    }

    /**
     * Gets the volume group id for a given {@link AudioAttributes} usage.
     *
     * @param zoneId The zone id whose volume group is queried.
     * @param usage The {@link AudioAttributes} usage to get a volume group from.
     * @return The volume group id where the usage belongs to
     */
    @RequiresPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    public int getVolumeGroupIdForUsage(int zoneId, @AudioAttributes.AttributeUsage int usage)
            throws CarNotConnectedException {
        try {
            return mService.getVolumeGroupIdForUsage(zoneId, usage);
        } catch (RemoteException e) {
            Log.e(CarLibLog.TAG_CAR, "getVolumeGroupIdForUsage failed", e);
            throw new CarNotConnectedException(e);
        }
    }

    /**
     * Gets array of {@link AudioAttributes} usages for a volume group in primary zone.
     *
     * @see {@link #getUsagesForVolumeGroupId(int, int)}
     */
    public @NonNull int[] getUsagesForVolumeGroupId(int groupId) throws CarNotConnectedException {
        return getUsagesForVolumeGroupId(PRIMARY_AUDIO_ZONE, groupId);
    }

    /**
     * Gets array of {@link AudioAttributes} usages for a volume group in a zone.
     *
     * @param zoneId The zone id whose volume group is queried.
     * @param groupId The volume group id whose associated audio usages is returned.
     * @return Array of {@link AudioAttributes} usages for a given volume group id
     */
    @RequiresPermission(Car.PERMISSION_CAR_CONTROL_AUDIO_VOLUME)
    public @NonNull int[] getUsagesForVolumeGroupId(int zoneId, int groupId)
            throws CarNotConnectedException {
        try {
            return mService.getUsagesForVolumeGroupId(zoneId, groupId);
        } catch (RemoteException e) {
            Log.e(CarLibLog.TAG_CAR, "getUsagesForVolumeGroupId failed", e);
            throw new CarNotConnectedException(e);
        }
    }

    /** @hide */
    @Override
    public void onCarDisconnected() {
        if (mService != null) {
            try {
                mService.unregisterVolumeCallback(mCarVolumeCallbackImpl.asBinder());
            } catch (RemoteException e) {
                Log.e(CarLibLog.TAG_CAR, "unregisterVolumeCallback failed", e);
            }
        }
    }

    /** @hide */
    public CarAudioManager(IBinder service, Context context, Handler handler) {
        mService = ICarAudio.Stub.asInterface(service);
        mCarVolumeCallbacks = new ArrayList<>();

        try {
            mService.registerVolumeCallback(mCarVolumeCallbackImpl.asBinder());
        } catch (RemoteException e) {
            Log.e(CarLibLog.TAG_CAR, "registerVolumeCallback failed", e);
        }
    }

    /**
     * Registers a {@link CarVolumeCallback} to receive volume change callbacks
     * @param callback {@link CarVolumeCallback} instance, can not be null
     */
    public void registerCarVolumeCallback(@NonNull CarVolumeCallback callback) {
        mCarVolumeCallbacks.add(callback);
    }

    /**
     * Unregisters a {@link CarVolumeCallback} from receiving volume change callbacks
     * @param callback {@link CarVolumeCallback} instance previously registered, can not be null
     */
    public void unregisterCarVolumeCallback(@NonNull CarVolumeCallback callback) {
        mCarVolumeCallbacks.remove(callback);
    }

    /**
     * Callback interface to receive volume change events in a car.
     * Extend this class and register it with {@link #registerCarVolumeCallback(CarVolumeCallback)}
     * and unregister it via {@link #unregisterCarVolumeCallback(CarVolumeCallback)}
     */
    public abstract static class CarVolumeCallback {
        /**
         * This is called whenever a group volume is changed.
         * The changed-to volume index is not included, the caller is encouraged to
         * get the current group volume index via CarAudioManager.
         *
         * @param zoneId Id of the audio zone that volume change happens
         * @param groupId Id of the volume group that volume is changed
         * @param flags see {@link android.media.AudioManager} for flag definitions
         */
        public void onGroupVolumeChanged(int zoneId, int groupId, int flags) {}

        /**
         * This is called whenever the master mute state is changed.
         * The changed-to master mute state is not included, the caller is encouraged to
         * get the current master mute state via AudioManager.
         *
         * @param zoneId Id of the audio zone that master mute state change happens
         * @param flags see {@link android.media.AudioManager} for flag definitions
         */
        public void onMasterMuteChanged(int zoneId, int flags) {}
    }
}
