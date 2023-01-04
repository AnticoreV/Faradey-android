/*
 * Copyright 2020 The Matrix.org Foundation C.I.C.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.matrix.android.sdk.internal.crypto.store

import androidx.lifecycle.LiveData
import androidx.paging.PagedList
import org.matrix.android.sdk.api.session.crypto.GlobalCryptoConfig
import org.matrix.android.sdk.api.session.crypto.NewSessionListener
import org.matrix.android.sdk.api.session.crypto.OutgoingKeyRequest
import org.matrix.android.sdk.api.session.crypto.OutgoingRoomKeyRequestState
import org.matrix.android.sdk.api.session.crypto.crosssigning.CryptoCrossSigningKeys
import org.matrix.android.sdk.api.session.crypto.crosssigning.MXCrossSigningInfo
import org.matrix.android.sdk.api.session.crypto.crosssigning.PrivateKeysInfo
import org.matrix.android.sdk.api.session.crypto.keysbackup.SavedKeyBackupKeyInfo
import org.matrix.android.sdk.api.session.crypto.model.AuditTrail
import org.matrix.android.sdk.api.session.crypto.model.CryptoDeviceInfo
import org.matrix.android.sdk.api.session.crypto.model.DeviceInfo
import org.matrix.android.sdk.api.session.crypto.model.MXUsersDevicesMap
import org.matrix.android.sdk.api.session.crypto.model.RoomKeyRequestBody
import org.matrix.android.sdk.api.session.crypto.model.TrailType
import org.matrix.android.sdk.api.session.events.model.Event
import org.matrix.android.sdk.api.session.events.model.content.RoomKeyWithHeldContent
import org.matrix.android.sdk.api.session.events.model.content.WithHeldCode
import org.matrix.android.sdk.api.util.Optional
import org.matrix.android.sdk.internal.crypto.model.MXInboundMegolmSessionWrapper
import org.matrix.android.sdk.internal.crypto.model.OlmSessionWrapper
import org.matrix.android.sdk.internal.crypto.model.OutboundGroupSessionWrapper
import org.matrix.android.sdk.internal.crypto.store.db.model.KeysBackupDataEntity
import org.matrix.olm.OlmAccount
import org.matrix.olm.OlmOutboundGroupSession

/**
 * The crypto data store.
 */
internal interface IMXCryptoStore {

    /**
     * Notify the store that a sync response treatment is starting.
     * Impacted methods:
     * - [setShouldShareHistory]
     * - [setShouldEncryptForInvitedMembers]
     * @See [onSyncCompleted] to notify that the treatment is over.
     */
    fun onSyncWillProcess()

    /**
     * Notify the store that the sync treatment response is finished.
     * The store will save all aggregated data.
     */
    fun onSyncCompleted()

    /**
     * @return the device id
     */
    fun getDeviceId(): String

    /**
     * @return the olm account
     */
    fun <T> doWithOlmAccount(block: (OlmAccount) -> T): T

    fun getOrCreateOlmAccount(): OlmAccount

    /**
     * Retrieve the known inbound group sessions.
     *
     * @return the list of all known group sessions, to export them.
     */
    fun getInboundGroupSessions(): List<MXInboundMegolmSessionWrapper>

    /**
     * Retrieve the known inbound group sessions for the specified room.
     *
     * @param roomId The roomId that the sessions will be returned
     * @return the list of all known group sessions, for the provided roomId
     */
    fun getInboundGroupSessions(roomId: String): List<MXInboundMegolmSessionWrapper>

    /**
     * @return true to unilaterally blacklist all unverified devices.
     */
    fun getGlobalBlacklistUnverifiedDevices(): Boolean

    /**
     * Set the global override for whether the client should ever send encrypted
     * messages to unverified devices.
     * If false, it can still be overridden per-room.
     * If true, it overrides the per-room settings.
     *
     * @param block true to unilaterally blacklist all
     */
    fun setGlobalBlacklistUnverifiedDevices(block: Boolean)

    /**
     * Enable or disable key gossiping.
     * Default is true.
     * If set to false this device won't send key_request nor will accept key forwarded
     */
    fun enableKeyGossiping(enable: Boolean)

    fun isKeyGossipingEnabled(): Boolean

    /**
     * As per MSC3061.
     * If true will make it possible to share part of e2ee room history
     * on invite depending on the room visibility setting.
     */
    fun enableShareKeyOnInvite(enable: Boolean)

    /**
     * As per MSC3061.
     * If true will make it possible to share part of e2ee room history
     * on invite depending on the room visibility setting.
     */
    fun isShareKeysOnInviteEnabled(): Boolean

    /**
     * Provides the rooms ids list in which the messages are not encrypted for the unverified devices.
     *
     * @return the room Ids list
     */
    fun getRoomsListBlacklistUnverifiedDevices(): List<String>

    /**
     * A live status regarding sharing keys for unverified devices in this room.
     *
     * @return Live status
     */
    fun getLiveBlockUnverifiedDevices(roomId: String): LiveData<Boolean>

    /**
     * Tell if unverified devices should be blacklisted when sending keys.
     *
     * @return true if should not send keys to unverified devices
     */
    fun getBlockUnverifiedDevices(roomId: String): Boolean

    /**
     * Define if encryption keys should be sent to unverified devices in this room.
     *
     * @param roomId the roomId
     * @param block if true will not send keys to unverified devices
     */
    fun blockUnverifiedDevicesInRoom(roomId: String, block: Boolean)

    /**
     * Get the current keys backup version.
     */
    fun getKeyBackupVersion(): String?

    /**
     * Set the current keys backup version.
     *
     * @param keyBackupVersion the keys backup version or null to delete it
     */
    fun setKeyBackupVersion(keyBackupVersion: String?)

    /**
     * Get the current keys backup local data.
     */
    fun getKeysBackupData(): KeysBackupDataEntity?

    /**
     * Set the keys backup local data.
     *
     * @param keysBackupData the keys backup local data, or null to erase data
     */
    fun setKeysBackupData(keysBackupData: KeysBackupDataEntity?)

    /**
     * @return the devices statuses map (userId -> tracking status)
     */
    fun getDeviceTrackingStatuses(): Map<String, Int>

    /**
     * Indicate if the store contains data for the passed account.
     *
     * @return true means that the user enabled the crypto in a previous session
     */
    fun hasData(): Boolean

    /**
     * Delete the crypto store for the passed credentials.
     */
    fun deleteStore()

    /**
     * open any existing crypto store.
     */
    fun open()

    /**
     * Close the store.
     */
    fun close()

    /**
     * Store the device id.
     *
     * @param deviceId the device id
     */
    fun storeDeviceId(deviceId: String)

    /**
     * Store the end to end account for the logged-in user.
     */
    fun saveOlmAccount()

    /**
     * Retrieve a device for a user.
     *
     * @param userId the user's id.
     * @param deviceId the device id.
     * @return the device
     */
    fun getUserDevice(userId: String, deviceId: String): CryptoDeviceInfo?

    /**
     * Retrieve a device by its identity key.
     *
     * @param identityKey the device identity key (`MXDeviceInfo.identityKey`)
     * @return the device or null if not found
     */
    fun deviceWithIdentityKey(identityKey: String): CryptoDeviceInfo?

    /**
     * Store the known devices for a user.
     *
     * @param userId The user's id.
     * @param devices A map from device id to 'MXDevice' object for the device.
     */
    fun storeUserDevices(userId: String, devices: Map<String, CryptoDeviceInfo>?)

    fun storeUserCrossSigningKeys(
            userId: String,
            cryptoCrossSigningKeys: CryptoCrossSigningKeys
    )

    /**
     * Retrieve the known devices for a user.
     *
     * @param userId The user's id.
     * @return The devices map if some devices are known, else null
     */
    fun getUserDevices(userId: String): Map<String, CryptoDeviceInfo>?

    fun getUserDeviceList(userId: String): List<CryptoDeviceInfo>?

    fun getLiveDeviceList(userId: String): LiveData<List<CryptoDeviceInfo>>

    fun getLiveDeviceList(userIds: List<String>): LiveData<List<CryptoDeviceInfo>>

    // TODO temp
    fun getLiveDeviceList(): LiveData<List<CryptoDeviceInfo>>

    fun getLiveDeviceWithId(deviceId: String): LiveData<Optional<CryptoDeviceInfo>>

    fun getMyDevicesInfo(): List<DeviceInfo>

    fun getLiveMyDevicesInfo(): LiveData<List<DeviceInfo>>

    fun getLiveMyDevicesInfo(deviceId: String): LiveData<Optional<DeviceInfo>>

    fun saveMyDevicesInfo(info: List<DeviceInfo>)

    /**
     * Store the crypto algorithm for a room.
     *
     * @param roomId the id of the room.
     * @param algorithm the algorithm.
     */
    fun storeRoomAlgorithm(roomId: String, algorithm: String?)

    /**
     * Provides the algorithm used in a dedicated room.
     *
     * @param roomId the room id
     * @return the algorithm, null is the room is not encrypted
     */
    fun getRoomAlgorithm(roomId: String): String?

    /**
     * This is a bit different than isRoomEncrypted.
     * A room is encrypted when there is a m.room.encryption state event in the room (malformed/invalid or not).
     * But the crypto layer has additional guaranty to ensure that encryption would never been reverted.
     * It's defensive coding out of precaution (if ever state is reset).
     */
    fun roomWasOnceEncrypted(roomId: String): Boolean

    fun shouldEncryptForInvitedMembers(roomId: String): Boolean

    /**
     * The data is not stored immediately, this MUST be call during a sync response treatment.
     */
    fun setShouldEncryptForInvitedMembers(roomId: String, shouldEncryptForInvitedMembers: Boolean)

    fun shouldShareHistory(roomId: String): Boolean

    /**
     * Sets a boolean flag that will determine whether or not room history (existing inbound sessions)
     * will be shared to new user invites.
     * The data is not stored immediately, this MUST be call during a sync response treatment.
     *
     * @param roomId the room id
     * @param shouldShareHistory The boolean flag
     */
    fun setShouldShareHistory(roomId: String, shouldShareHistory: Boolean)

    /**
     * Store a session between the logged-in user and another device.
     *
     * @param olmSessionWrapper the end-to-end session.
     * @param deviceKey the public key of the other device.
     */
    fun storeSession(olmSessionWrapper: OlmSessionWrapper, deviceKey: String)

    /**
     * Retrieve all end-to-end session ids between our own device and another
     * device.
     *
     * @param deviceKey the public key of the other device.
     * @return A set of sessionId, or null if device is not known
     */
    fun getDeviceSessionIds(deviceKey: String): List<String>?

    /**
     * Retrieve an end-to-end session between our own device and another
     * device.
     *
     * @param sessionId the session Id.
     * @param deviceKey the public key of the other device.
     * @return The Base64 end-to-end session, or null if not found
     */
    fun getDeviceSession(sessionId: String, deviceKey: String): OlmSessionWrapper?

    /**
     * Retrieve the last used sessionId, regarding `lastReceivedMessageTs`, or null if no session exist.
     *
     * @param deviceKey the public key of the other device.
     * @return last used sessionId, or null if not found
     */
    fun getLastUsedSessionId(deviceKey: String): String?

    /**
     * Store inbound group sessions.
     *
     * @param sessions the inbound group sessions to store.
     */
    fun storeInboundGroupSessions(sessions: List<MXInboundMegolmSessionWrapper>)

    /**
     * Retrieve an inbound group session.
     *
     * @param sessionId the session identifier.
     * @param senderKey the base64-encoded curve25519 key of the sender.
     * @return an inbound group session.
     */
    fun getInboundGroupSession(sessionId: String, senderKey: String): MXInboundMegolmSessionWrapper?

    /**
     * Retrieve an inbound group session, filtering shared history.
     *
     * @param sessionId the session identifier.
     * @param senderKey the base64-encoded curve25519 key of the sender.
     * @param sharedHistory filter inbound session with respect to shared history field
     * @return an inbound group session.
     */
    fun getInboundGroupSession(sessionId: String, senderKey: String, sharedHistory: Boolean): MXInboundMegolmSessionWrapper?

    /**
     * Get the current outbound group session for this encrypted room.
     */
    fun getCurrentOutboundGroupSessionForRoom(roomId: String): OutboundGroupSessionWrapper?

    /**
     * Get the current outbound group session for this encrypted room.
     */
    fun storeCurrentOutboundGroupSessionForRoom(roomId: String, outboundGroupSession: OlmOutboundGroupSession?)

    /**
     * Remove an inbound group session.
     *
     * @param sessionId the session identifier.
     * @param senderKey the base64-encoded curve25519 key of the sender.
     */
    fun removeInboundGroupSession(sessionId: String, senderKey: String)

    /* ==========================================================================================
     * Keys backup
     * ========================================================================================== */

    /**
     * Mark all inbound group sessions as not backed up.
     */
    fun resetBackupMarkers()

    /**
     * Mark inbound group sessions as backed up on the user homeserver.
     *
     * @param olmInboundGroupSessionWrappers the sessions
     */
    fun markBackupDoneForInboundGroupSessions(olmInboundGroupSessionWrappers: List<MXInboundMegolmSessionWrapper>)

    /**
     * Retrieve inbound group sessions that are not yet backed up.
     *
     * @param limit the maximum number of sessions to return.
     * @return an array of non backed up inbound group sessions.
     */
    fun inboundGroupSessionsToBackup(limit: Int): List<MXInboundMegolmSessionWrapper>

    /**
     * Number of stored inbound group sessions.
     *
     * @param onlyBackedUp if true, count only session marked as backed up.
     * @return a count.
     */
    fun inboundGroupSessionsCount(onlyBackedUp: Boolean): Int

    /**
     * Save the device statuses.
     *
     * @param deviceTrackingStatuses the device tracking statuses
     */
    fun saveDeviceTrackingStatuses(deviceTrackingStatuses: Map<String, Int>)

    /**
     * Get the tracking status of a specified userId devices.
     *
     * @param userId the user id
     * @param defaultValue the default value
     * @return the tracking status
     */
    fun getDeviceTrackingStatus(userId: String, defaultValue: Int): Int

    /**
     * Look for an existing outgoing room key request, and if none is found, return null.
     *
     * @param requestBody the request body
     * @return an OutgoingRoomKeyRequest instance or null
     */
    fun getOutgoingRoomKeyRequest(requestBody: RoomKeyRequestBody): OutgoingKeyRequest?
    fun getOutgoingRoomKeyRequest(requestId: String): OutgoingKeyRequest?
    fun getOutgoingRoomKeyRequest(roomId: String, sessionId: String, algorithm: String, senderKey: String): List<OutgoingKeyRequest>

    /**
     * Look for an existing outgoing room key request, and if none is found, add a new one.
     *
     * @param requestBody the request
     * @param recipients list of recipients
     * @param fromIndex start index
     * @return either the same instance as passed in, or the existing one.
     */
    fun getOrAddOutgoingRoomKeyRequest(requestBody: RoomKeyRequestBody, recipients: Map<String, List<String>>, fromIndex: Int): OutgoingKeyRequest
    fun updateOutgoingRoomKeyRequestState(requestId: String, newState: OutgoingRoomKeyRequestState)
    fun updateOutgoingRoomKeyRequiredIndex(requestId: String, newIndex: Int)
    fun updateOutgoingRoomKeyReply(
            roomId: String,
            sessionId: String,
            algorithm: String,
            senderKey: String,
            fromDevice: String?,
            event: Event
    )

    fun deleteOutgoingRoomKeyRequest(requestId: String)
    fun deleteOutgoingRoomKeyRequestInState(state: OutgoingRoomKeyRequestState)

    fun saveIncomingKeyRequestAuditTrail(
            requestId: String,
            roomId: String,
            sessionId: String,
            senderKey: String,
            algorithm: String,
            fromUser: String,
            fromDevice: String
    )

    fun saveWithheldAuditTrail(
            roomId: String,
            sessionId: String,
            senderKey: String,
            algorithm: String,
            code: WithHeldCode,
            userId: String,
            deviceId: String
    )

    fun saveForwardKeyAuditTrail(
            roomId: String,
            sessionId: String,
            senderKey: String,
            algorithm: String,
            userId: String,
            deviceId: String,
            chainIndex: Long?
    )

    fun saveIncomingForwardKeyAuditTrail(
            roomId: String,
            sessionId: String,
            senderKey: String,
            algorithm: String,
            userId: String,
            deviceId: String,
            chainIndex: Long?
    )

    fun addNewSessionListener(listener: NewSessionListener)

    fun removeSessionListener(listener: NewSessionListener)

    // =============================================
    // CROSS SIGNING
    // =============================================

    /**
     * Gets the current crosssigning info.
     */
    fun getMyCrossSigningInfo(): MXCrossSigningInfo?

    fun setMyCrossSigningInfo(info: MXCrossSigningInfo?)

    fun getCrossSigningInfo(userId: String): MXCrossSigningInfo?
    fun getLiveCrossSigningInfo(userId: String): LiveData<Optional<MXCrossSigningInfo>>
    fun setCrossSigningInfo(userId: String, info: MXCrossSigningInfo?)

    fun markMyMasterKeyAsLocallyTrusted(trusted: Boolean)

    fun storePrivateKeysInfo(msk: String?, usk: String?, ssk: String?)
    fun storeMSKPrivateKey(msk: String?)
    fun storeSSKPrivateKey(ssk: String?)
    fun storeUSKPrivateKey(usk: String?)

    fun getCrossSigningPrivateKeys(): PrivateKeysInfo?
    fun getLiveCrossSigningPrivateKeys(): LiveData<Optional<PrivateKeysInfo>>

    fun getGlobalCryptoConfig(): GlobalCryptoConfig
    fun getLiveGlobalCryptoConfig(): LiveData<GlobalCryptoConfig>

    fun saveBackupRecoveryKey(recoveryKey: String?, version: String?)
    fun getKeyBackupRecoveryKeyInfo(): SavedKeyBackupKeyInfo?

    fun setUserKeysAsTrusted(userId: String, trusted: Boolean = true)
    fun setDeviceTrust(userId: String, deviceId: String, crossSignedVerified: Boolean, locallyVerified: Boolean?)

    fun clearOtherUserTrust()

    fun updateUsersTrust(check: (String) -> Boolean)

    fun addWithHeldMegolmSession(withHeldContent: RoomKeyWithHeldContent)
    fun getWithHeldMegolmSession(roomId: String, sessionId: String): RoomKeyWithHeldContent?

    fun markedSessionAsShared(
            roomId: String?,
            sessionId: String,
            userId: String,
            deviceId: String,
            deviceIdentityKey: String,
            chainIndex: Int
    )

    /**
     * Query for information on this session sharing history.
     * @return SharedSessionResult
     * if found is true then this session was initialy shared with that user|device,
     * in this case chainIndex is not nullindicates the ratchet position.
     * In found is false, chainIndex is null
     */
    fun getSharedSessionInfo(roomId: String?, sessionId: String, deviceInfo: CryptoDeviceInfo): SharedSessionResult
    data class SharedSessionResult(val found: Boolean, val chainIndex: Int?)

    fun getSharedWithInfo(roomId: String?, sessionId: String): MXUsersDevicesMap<Int>
    // Dev tools

    fun getOutgoingRoomKeyRequests(): List<OutgoingKeyRequest>
    fun getOutgoingRoomKeyRequestsPaged(): LiveData<PagedList<OutgoingKeyRequest>>
    fun getGossipingEventsTrail(): LiveData<PagedList<AuditTrail>>
    fun <T> getGossipingEventsTrail(type: TrailType, mapper: ((AuditTrail) -> T)): LiveData<PagedList<T>>
    fun getGossipingEvents(): List<AuditTrail>

    fun setDeviceKeysUploaded(uploaded: Boolean)
    fun areDeviceKeysUploaded(): Boolean
    fun tidyUpDataBase()
    fun getOutgoingRoomKeyRequests(inStates: Set<OutgoingRoomKeyRequestState>): List<OutgoingKeyRequest>

    /**
     * Store a bunch of data related to the users. @See [UserDataToStore].
     */
    fun storeUserDataToStore(userDataToStore: UserDataToStore)
}
