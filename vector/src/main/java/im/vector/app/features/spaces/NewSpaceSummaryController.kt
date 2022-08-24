/*
 * Copyright (c) 2021 New Vector Ltd
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

package im.vector.app.features.spaces

import com.airbnb.epoxy.EpoxyController
import im.vector.app.R
import im.vector.app.core.resources.StringProvider
import im.vector.app.features.grouplist.newHomeSpaceSummaryItem
import im.vector.app.features.home.AvatarRenderer
import im.vector.app.features.home.room.list.UnreadCounterBadgeView
import im.vector.app.features.settings.VectorPreferences
import org.matrix.android.sdk.api.session.room.model.Membership
import org.matrix.android.sdk.api.session.room.model.RoomSummary
import org.matrix.android.sdk.api.session.room.model.SpaceChildInfo
import org.matrix.android.sdk.api.session.room.summary.RoomAggregateNotificationCount
import org.matrix.android.sdk.api.util.toMatrixItem
import javax.inject.Inject

class NewSpaceSummaryController @Inject constructor(
        private val avatarRenderer: AvatarRenderer,
        private val stringProvider: StringProvider,
        private val vectorPreferences: VectorPreferences,
) : EpoxyController() {

    var callback: Callback? = null
    private var viewState: SpaceListViewState? = null

    private val subSpaceComparator: Comparator<SpaceChildInfo> = compareBy<SpaceChildInfo> { it.order }.thenBy { it.childRoomId }

    fun update(viewState: SpaceListViewState) {
        this.viewState = viewState
        requestModelBuild()
    }

    override fun buildModels() {
        val nonNullViewState = viewState ?: return
        buildGroupModels(
                nonNullViewState.spaces,
                nonNullViewState.selectedSpace,
                nonNullViewState.rootSpacesOrdered,
                nonNullViewState.homeAggregateCount
        )
    }

    private fun buildGroupModels(
            spaceSummaries: List<RoomSummary>?,
            selectedSpace: RoomSummary?,
            rootSpaces: List<RoomSummary>?,
            homeCount: RoomAggregateNotificationCount
    ) {
        val host = this
        val useAggregateCounts = vectorPreferences.aggregateUnreadRoomCounts()
        newSpaceListHeaderItem {
            id("space_list_header")
        }

        if (selectedSpace != null) {
            addSubSpaces(selectedSpace, spaceSummaries, homeCount, useAggregateCounts)
        } else {
            addHomeItem(true, homeCount)
            addRootSpaces(rootSpaces, useAggregateCounts)
        }

        newSpaceAddItem {
            id("create")
            listener { host.callback?.onAddSpaceSelected() }
        }
    }

    private fun addHomeItem(selected: Boolean, homeCount: RoomAggregateNotificationCount) {
        val host = this
        newHomeSpaceSummaryItem {
            id("space_home")
            text(host.stringProvider.getString(R.string.all_chats))
            selected(selected)
            countState(UnreadCounterBadgeView.State(homeCount.totalCount, homeCount.isHighlight, homeCount.unreadCount, false))
            listener { host.callback?.onSpaceSelected(null) }
        }
    }

    private fun addSubSpaces(
            selectedSpace: RoomSummary,
            spaceSummaries: List<RoomSummary>?,
            homeCount: RoomAggregateNotificationCount,
            useAggregateCounts: Boolean,
    ) {
        val host = this
        val spaceChildren = selectedSpace.spaceChildren
        var subSpacesAdded = false

        spaceChildren?.sortedWith(subSpaceComparator)?.forEach { spaceChild ->
            val subSpaceSummary = spaceSummaries?.firstOrNull { it.roomId == spaceChild.childRoomId } ?: return@forEach

            if (subSpaceSummary.membership != Membership.INVITE) {
                subSpacesAdded = true
                newSpaceSummaryItem {
                    avatarRenderer(host.avatarRenderer)
                    id(subSpaceSummary.roomId)
                    matrixItem(subSpaceSummary.toMatrixItem())
                    selected(false)
                    listener { host.callback?.onSpaceSelected(subSpaceSummary) }
                    countState(
                            UnreadCounterBadgeView.State(
                                    if (useAggregateCounts) subSpaceSummary.aggregatedNotificationCount else subSpaceSummary.notificationCount,
                                    subSpaceSummary.highlightCount > 0,
                                    if (useAggregateCounts) subSpaceSummary.aggregatedUnreadCount else subSpaceSummary.safeUnreadCount,
                                    subSpaceSummary.markedUnread
                            )
                    )
                }
            }
        }

        if (!subSpacesAdded) {
            addHomeItem(false, homeCount)
        }
    }

    private fun addRootSpaces(rootSpaces: List<RoomSummary>?, useAggregateCounts: Boolean) {
        val host = this
        rootSpaces
                ?.filter { it.membership != Membership.INVITE }
                ?.forEach { roomSummary ->
                    newSpaceSummaryItem {
                        avatarRenderer(host.avatarRenderer)
                        id(roomSummary.roomId)
                        matrixItem(roomSummary.toMatrixItem())
                        listener { host.callback?.onSpaceSelected(roomSummary) }
                        countState(
                                UnreadCounterBadgeView.State(
                                        if (useAggregateCounts) roomSummary.aggregatedNotificationCount else roomSummary.notificationCount,
                                        roomSummary.highlightCount > 0,
                                        if (useAggregateCounts) roomSummary.aggregatedUnreadCount else roomSummary.safeUnreadCount,
                                        roomSummary.markedUnread
                                )
                        )
                    }
                }
    }

    interface Callback {
        fun onSpaceSelected(spaceSummary: RoomSummary?)
        fun onSpaceInviteSelected(spaceSummary: RoomSummary)
        fun onSpaceSettings(spaceSummary: RoomSummary)
        fun onAddSpaceSelected()
        fun sendFeedBack()
    }
}
