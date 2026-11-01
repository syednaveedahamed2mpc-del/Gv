package com.nexus.sidekick;
import org.junit.Test;
import static org.junit.Assert.*;
public class ToolGuardTest {
    @Test public void sensitiveControlsDenied(){
        assertTrue(ToolGuard.isSensitiveTap("Pay"));
        assertTrue(ToolGuard.isSensitiveTap("Send now"));
        assertTrue(ToolGuard.isSensitiveTap("Confirm"));
        assertTrue(ToolGuard.isSensitiveTap("Delete"));
        assertTrue(ToolGuard.isSensitiveTap("Subscribe"));
        assertFalse(ToolGuard.isSensitiveTap("Search"));
        assertFalse(ToolGuard.isSensitiveTap("Next"));
    }
}
