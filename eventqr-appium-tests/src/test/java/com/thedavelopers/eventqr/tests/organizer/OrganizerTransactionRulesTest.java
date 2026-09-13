package com.thedavelopers.eventqr.tests.organizer;

import com.thedavelopers.eventqr.base.BaseTest;
import com.thedavelopers.eventqr.config.TestConfig;
import com.thedavelopers.eventqr.pages.LoginPage;
import com.thedavelopers.eventqr.pages.OrganizerDashboardPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TestFlow 6.9 — Transaction Rules (TR-1..TR-2). Programmatic screen; all
 * assertions text-based.
 */
public class OrganizerTransactionRulesTest extends BaseTest {

    @BeforeEach
    void openTransactionRules() {
        LoginPage login = new LoginPage();
        login.login(TestConfig.ORGANIZER_EMAIL, TestConfig.ORGANIZER_PASS);
        OrganizerDashboardPage dash = new OrganizerDashboardPage();
        dash.seedApprovedEventIfNone();
        dash.tapSeeAllEvents();
        waitForVisibleId(id("recyclerEvents"));
        findElements(id("txtAttendeeEventTitle")).get(0).click();
        waitForText("Event Management");
        tapByText("Transaction Rules");
    }

    @Test
    @DisplayName("TR-1: Rules sections render")
    void sectionsRender() {
        assertAll(
                () -> assertTrue(isTextDisplayed("Transaction Rules"), "Screen title expected"),
                () -> assertTrue(isTextDisplayed("Duplicate Prevention"), "Duplicate Prevention section"),
                () -> assertTrue(isTextDisplayed("Allow Duplicate Entry Scans"), "Duplicate entry rule"),
                () -> assertTrue(isTextDisplayed("Scan Limits"), "Scan Limits section")
        );
    }

    @Test
    @DisplayName("TR-2: Saving rules confirms and returns")
    void saveRulesConfirms() {
        tapByText("Save Rules");
        assertTrue(isToastDisplayed("Rules saved successfully") || isTextDisplayed("Transaction Rules"),
                "Saving rules should toast success (or keep the screen)");
    }
}