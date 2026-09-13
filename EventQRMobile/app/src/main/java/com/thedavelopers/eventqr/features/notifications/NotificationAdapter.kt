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

            iconContainer.setBackgroundResource(R.drawable.bg_notification_icon_box)
            when {
                item.notificationType == NotificationType.STAFF_ASSIGNMENT -> {
                    iconView.setImageResource(R.drawable.ic_group)
                    iconView.setColorFilter(0xFF4F46E5.toInt())
                }
                item.notificationType == NotificationType.REGISTRATION_NEW -> {
                    iconView.setImageResource(R.drawable.ic_check_circle_purple)
                    iconView.setColorFilter(0xFF10B981.toInt())
                }
                item.notificationType == NotificationType.CAPACITY_WARNING || item.notificationType == NotificationType.CAPACITY_FULL -> {
                    iconView.setImageResource(R.drawable.ic_admin_users)
                    iconView.setColorFilter(0xFFB8860B.toInt())
                }
                item.notificationType == NotificationType.REWARD_EXHAUSTED -> {
                    iconView.setImageResource(R.drawable.ic_gift)
                    iconView.setColorFilter(0xFFEF4444.toInt())
                }
                item.notificationType == NotificationType.POINTS_ADJUSTED -> {
                    iconView.setImageResource(R.drawable.ic_check_circle_purple)
                    iconView.setColorFilter(0xFF4F46E5.toInt())
                }
                item.notificationType == NotificationType.EVENT_APPROVED -> {
                    iconView.setImageResource(R.drawable.ic_check_circle_purple)
                    iconView.setColorFilter(0xFF10B981.toInt())
                }
                item.notificationType == NotificationType.EVENT_REJECTED -> {
                    iconView.setImageResource(R.drawable.ic_error_circle)
                    iconView.setColorFilter(0xFFEF4444.toInt())
                }
                item.notificationType == NotificationType.EVENT_STARTING_SOON -> {
                    iconView.setImageResource(R.drawable.ic_row_clock)
                    iconView.setColorFilter(0xFF4F46E5.toInt())
                }
                item.notificationType == NotificationType.EVENT_COMPLETED -> {
                    iconView.setImageResource(R.drawable.ic_check_circle_purple)
                    iconView.setColorFilter(0xFF10B981.toInt())
                }
                item.notificationType == NotificationType.SCAN_REJECTED -> {
                    iconView.setImageResource(R.drawable.ic_error_circle)
                    iconView.setColorFilter(0xFFEF4444.toInt())
                }

                item.notificationType == NotificationType.SCAN_APPROVED -> {
                    iconView.setImageResource(R.drawable.ic_check_circle_purple)
                    iconView.setColorFilter(0xFF10B981.toInt())
                }

                else -> {
                    iconView.setImageResource(R.drawable.ic_check_circle_purple)
                    iconView.setColorFilter(0xFF10B981.toInt())
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