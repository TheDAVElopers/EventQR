package com.thedavelopers.eventqr.pages;

import com.thedavelopers.eventqr.base.BaseTest;
import io.appium.java_client.AppiumBy;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

public class AdminDashboardPage extends BaseTest {

    private static final String PORTAL_SWITCHER = "portalSwitcherChip";
    private static final String PENDING_REQUESTS_VALUE = "textPendingRequestsValue";
    private static final String TOTAL_ACCOUNTS_VALUE = "textTotalAccountsValue";
    private static final String ACTIVE_EVENTS_VALUE = "textActiveEventsValue";
    private static final String AUDIT_LOGS_VALUE = "textAuditLogsValue";
    private static final String BOTTOM_NAV_REQUESTS = "navRequests";
    private static final String BOTTOM_NAV_ACCOUNTS = "navAccounts";
    private static final String BOTTOM_NAV_LOGS = "navLogs";

    // Event requests screen (activity_admin_event_requests.xml)
    private static final String CHIP_PENDING = "chipPending";

    // Request list item (item_admin_event_request.xml)
    private static final String REQUEST_ITEM_TITLE = "textTitle";

    // Pending-requests alert banner (activity_admin_dashboard.xml)
    private static final String CARD_PENDING_ALERT = "cardPendingAlert";
    private static final String TEXT_PENDING_ALERT = "textPendingAlert";

    // Bottom navigation
    private static final String BOTTOM_NAV_DASHBOARD = "navDashboard";

    public boolean isDashboardVisible() {
        return isDisplayed(id("textAdminPortalTitle"));
    }

    /** True when the pending-requests alert banner is rendered (hidden when 0 pending). */
    public boolean isPendingAlertVisible() {
        return isDisplayed(id(CARD_PENDING_ALERT));
    }

    public String getPendingAlertText() {
        return getText(id(TEXT_PENDING_ALERT));
    }

    /** Opens the Requests list from the pending-requests alert banner. */
    public void tapPendingAlert() {
        tap(id(CARD_PENDING_ALERT));
    }

    public void tapDashboardTab() {
        tap(id(BOTTOM_NAV_DASHBOARD));
    }

    public void tapPortalSwitcher() {
        tap(id(PORTAL_SWITCHER));
    }

    public String getPortalTitle() {
        return getText(id("textAdminPortalTitle"));
    }

    public String getPendingRequestsValue() {
        return getText(id(PENDING_REQUESTS_VALUE));
    }

    public String getTotalAccountsValue() {
        return getText(id(TOTAL_ACCOUNTS_VALUE));
    }

    public String getActiveEventsValue() {
        return getText(id(ACTIVE_EVENTS_VALUE));
    }

    public String getAuditLogsValue() {
        return getText(id(AUDIT_LOGS_VALUE));
    }

    public void openRequestsTab() {
        tap(id(BOTTOM_NAV_REQUESTS));
    }

    /**
     * Applies the Pending filter and opens the first pending request's detail
     * screen. The request item is a RecyclerView row (item_admin_event_request.
     * xml) whose click listener lives on the row root; tapping its {@code
     * textTitle} TextView bubbles up to that listener.
     */
    public void openFirstPendingRequestDetail() {
        tap(id(CHIP_PENDING));
        WebElement itemTitle = wait.until(ExpectedConditions.presenceOfElementLocated(
                AppiumBy.androidUIAutomator(
                        "new UiScrollable(new UiSelector().scrollable(true)).scrollIntoView("
                                + "new UiSelector().resourceId(\"" + id(REQUEST_ITEM_TITLE) + "\"))")));
        itemTitle.click();
    }

    public void openAccountsTab() {
        tap(id(BOTTOM_NAV_ACCOUNTS));
    }

    public void openLogsTab() {
        tap(id(BOTTOM_NAV_LOGS));
    }

}
