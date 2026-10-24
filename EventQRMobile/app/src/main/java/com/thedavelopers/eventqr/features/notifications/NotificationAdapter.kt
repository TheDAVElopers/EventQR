package com.thedavelopers.eventqr.features.notifications

import android.widget.ImageView
import android.widget.LinearLayout
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.thedavelopers.eventqr.R
import com.thedavelopers.eventqr.core.api.dto.NotificationStatus
import com.thedavelopers.eventqr.core.api.dto.NotificationType
import com.thedavelopers.eventqr.core.util.RelativeTimeUtils
import com.thedavelopers.eventqr.features.notifications.model.dto.NotificationResponse
import com.thedavelopers.eventqr.ui.theme.color

class NotificationAdapter(
    private val onItemTap: (NotificationResponse) -> Unit,
) : RecyclerView.Adapter<NotificationAdapter.ViewHolder>() {

    private val items = mutableListOf<NotificationResponse>()

    fun submitItems(newItems: List<NotificationResponse>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardContainer: LinearLayout = itemView.findViewById(R.id.layoutNotificationCard)
        private val iconContainer: LinearLayout = itemView.findViewById(R.id.layoutNotificationIcon)
        private val iconView: ImageView = itemView.findViewById(R.id.imgNotificationIcon)
        private val unreadDot: View = itemView.findViewById(R.id.viewUnreadDot)
        private val titleView: TextView = itemView.findViewById(R.id.txtNotificationTitle)
        private val messageView: TextView = itemView.findViewById(R.id.txtNotificationMessage)
        private val dateTimeView: TextView = itemView.findViewById(R.id.txtNotificationDateTime)

        fun bind(item: NotificationResponse) {
            val isRead = item.status == NotificationStatus.READ || item.readAt != null

            titleView.text = item.title
            messageView.text = item.message
            dateTimeView.text = RelativeTimeUtils.formatRelative(item.createdAt ?: item.readAt)

            when {
                item.notificationType == NotificationType.STAFF_ASSIGNMENT -> {
                    iconContainer.setBackgroundResource(R.drawable.bg_stat_circle_purple)
                    iconView.setImageResource(R.drawable.ic_group)
                    iconView.setColorFilter(itemView.context.color(R.color.primary))
                }
                item.notificationType == NotificationType.REGISTRATION_NEW -> {
                    iconContainer.setBackgroundResource(R.drawable.bg_stat_circle_green)
                    iconView.setImageResource(R.drawable.ic_check_circle_purple)
                    iconView.setColorFilter(itemView.context.color(R.color.success))
                }
                item.notificationType == NotificationType.CAPACITY_WARNING || item.notificationType == NotificationType.CAPACITY_FULL -> {
                    iconContainer.setBackgroundResource(R.drawable.bg_stat_circle_purple)
                    iconView.setImageResource(R.drawable.ic_admin_users)
                    iconView.setColorFilter(itemView.context.color(R.color.warning_dark))
                }
                item.notificationType == NotificationType.REWARD_EXHAUSTED -> {
                    iconContainer.setBackgroundResource(R.drawable.bg_icon_tile_red)
                    iconView.setImageResource(R.drawable.ic_gift)
                    iconView.setColorFilter(itemView.context.color(R.color.danger))
                }
                item.notificationType == NotificationType.REWARD_REDEEMED -> {
                    iconContainer.setBackgroundResource(R.drawable.bg_stat_circle_green)
                    iconView.setImageResource(R.drawable.ic_gift)
                    iconView.setColorFilter(itemView.context.color(R.color.success))
                }
                item.notificationType == NotificationType.POINTS_ADJUSTED -> {
                    iconContainer.setBackgroundResource(R.drawable.bg_stat_circle_purple)
                    iconView.setImageResource(R.drawable.ic_check_circle_purple)
                    iconView.setColorFilter(itemView.context.color(R.color.primary))
                }
                item.notificationType == NotificationType.EVENT_APPROVED -> {
                    iconContainer.setBackgroundResource(R.drawable.bg_stat_circle_green)
                    iconView.setImageResource(R.drawable.ic_check_circle_purple)
                    iconView.setColorFilter(itemView.context.color(R.color.success))
                }
                item.notificationType == NotificationType.EVENT_REJECTED -> {
                    iconContainer.setBackgroundResource(R.drawable.bg_icon_tile_red)
                    iconView.setImageResource(R.drawable.ic_error_circle)
                    iconView.setColorFilter(itemView.context.color(R.color.danger))
                }
                item.notificationType == NotificationType.EVENT_STARTING_SOON -> {
                    iconContainer.setBackgroundResource(R.drawable.bg_stat_circle_purple)
                    iconView.setImageResource(R.drawable.ic_row_clock)
                    iconView.setColorFilter(itemView.context.color(R.color.primary))
                }
                item.notificationType == NotificationType.EVENT_COMPLETED -> {
                    iconContainer.setBackgroundResource(R.drawable.bg_stat_circle_green)
                    iconView.setImageResource(R.drawable.ic_check_circle_purple)
                    iconView.setColorFilter(itemView.context.color(R.color.success))
                }
                item.notificationType == NotificationType.SCAN_REJECTED -> {
                    iconContainer.setBackgroundResource(R.drawable.bg_icon_tile_red)
                    iconView.setImageResource(R.drawable.ic_error_circle)
                    iconView.setColorFilter(itemView.context.color(R.color.danger))
                }

                item.notificationType == NotificationType.SCAN_APPROVED -> {
                    iconContainer.setBackgroundResource(R.drawable.bg_stat_circle_green)
                    iconView.setImageResource(R.drawable.ic_check_circle_purple)
                    iconView.setColorFilter(itemView.context.color(R.color.success))
                }

                else -> {
                    iconContainer.setBackgroundResource(R.drawable.bg_stat_circle_green)
                    iconView.setImageResource(R.drawable.ic_check_circle_purple)
                    iconView.setColorFilter(itemView.context.color(R.color.success))
                }
            }

            if (isRead) {
                cardContainer.setBackgroundResource(R.drawable.bg_notification_card_read)
                unreadDot.visibility = View.GONE
            } else {
                cardContainer.setBackgroundResource(R.drawable.bg_notification_card_unread)
                unreadDot.visibility = View.VISIBLE
            }

            itemView.setOnClickListener { onItemTap(item) }
        }
    }
}