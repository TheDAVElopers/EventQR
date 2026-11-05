package com.thedavelopers.eventqr.ui.icons

import androidx.annotation.DrawableRes
import com.thedavelopers.eventqr.R

/**
 * The single address of the icon family (`MyFiles/design/05-icon-system.md` §2-§4).
 *
 * Every icon is one Material Symbols Outlined vector at `res/drawable/ic_ui_<symbol>.xml`: 24x24
 * viewport, one path, `#FF000000`, tinted at the use site. A concept resolves to exactly one entry
 * here, so the same picture can never mean two things on two screens, and `R.drawable.ic_` outside
 * this file is a build error (`tools/check-tokens.ps1`, category `icons`).
 *
 * XML keeps referencing `@drawable/ic_ui_*` directly - a layout cannot call Kotlin.
 */
object AppIcons {
    @DrawableRes val Event = R.drawable.ic_ui_event
    @DrawableRes val Request = R.drawable.ic_ui_pending_actions
    @DrawableRes val Pass = R.drawable.ic_ui_confirmation_number
    @DrawableRes val Reward = R.drawable.ic_ui_redeem
    @DrawableRes val People = R.drawable.ic_ui_group
    @DrawableRes val Profile = R.drawable.ic_ui_person
    @DrawableRes val Scan = R.drawable.ic_ui_qr_code_scanner
    @DrawableRes val QrCode = R.drawable.ic_ui_qr_code_2
    @DrawableRes val Report = R.drawable.ic_ui_assessment
    @DrawableRes val Logs = R.drawable.ic_ui_receipt_long
    @DrawableRes val Audit = R.drawable.ic_ui_admin_panel_settings
    @DrawableRes val Search = R.drawable.ic_ui_search
    @DrawableRes val Add = R.drawable.ic_ui_add
    @DrawableRes val More = R.drawable.ic_ui_more_horiz
    @DrawableRes val Back = R.drawable.ic_ui_arrow_back
    @DrawableRes val Forward = R.drawable.ic_ui_arrow_forward
    @DrawableRes val Chevron = R.drawable.ic_ui_chevron_right
    @DrawableRes val Expand = R.drawable.ic_ui_expand_more
    @DrawableRes val Close = R.drawable.ic_ui_close
    @DrawableRes val Check = R.drawable.ic_ui_check
    @DrawableRes val Success = R.drawable.ic_ui_check_circle
    @DrawableRes val Error = R.drawable.ic_ui_error
    @DrawableRes val Warning = R.drawable.ic_ui_warning
    @DrawableRes val Notification = R.drawable.ic_ui_notifications_none
    @DrawableRes val NoNotifications = R.drawable.ic_ui_notifications_off
    @DrawableRes val Location = R.drawable.ic_ui_location_on
    @DrawableRes val Time = R.drawable.ic_ui_schedule
    @DrawableRes val Mail = R.drawable.ic_ui_mail
    @DrawableRes val Phone = R.drawable.ic_ui_call
    @DrawableRes val Lock = R.drawable.ic_ui_lock
    @DrawableRes val Visibility = R.drawable.ic_ui_visibility
    @DrawableRes val VisibilityOff = R.drawable.ic_ui_visibility_off
    @DrawableRes val Edit = R.drawable.ic_ui_edit
    @DrawableRes val Document = R.drawable.ic_ui_description
    @DrawableRes val Checklist = R.drawable.ic_ui_checklist
    @DrawableRes val Print = R.drawable.ic_ui_print
    @DrawableRes val TrendUp = R.drawable.ic_ui_trending_up
    @DrawableRes val TrendDown = R.drawable.ic_ui_trending_down
    @DrawableRes val Flashlight = R.drawable.ic_ui_flashlight_on
    @DrawableRes val Camera = R.drawable.ic_ui_photo_camera
    @DrawableRes val Home = R.drawable.ic_ui_home
    @DrawableRes val QrFrameCorner = R.drawable.ic_ui_qr_frame_corner
}
