package com.nexus.sidekick;
import org.junit.Test;
import static org.junit.Assert.*;
public class ActionGuardTest {
 @Test public void blocksConsequentialControls(){
   assertFalse(ActionGuard.isAllowed("Send"));
   assertFalse(ActionGuard.isAllowed("Confirm payment"));
   assertFalse(ActionGuard.isAllowed("Delete"));
   assertFalse(ActionGuard.isAllowed("Enter OTP"));
   assertFalse(ActionGuard.isAllowed("Authorize"));
 }
 @Test public void permitsNonSensitiveControls(){
   assertTrue(ActionGuard.isAllowed("Search"));
   assertTrue(ActionGuard.isAllowed("Settings"));
   assertTrue(ActionGuard.isAllowed("Music"));
 }
}
