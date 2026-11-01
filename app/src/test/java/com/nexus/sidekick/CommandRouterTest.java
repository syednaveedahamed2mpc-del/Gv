package com.nexus.sidekick;
import static org.junit.Assert.*;
import org.junit.Test;
public class CommandRouterTest {
 @Test public void combinedYouTubeSearch(){check("open youtube and search mr beast",CommandRouter.Type.YOUTUBE_SEARCH,"mr beast");}
 @Test public void combinedFor(){check("Open YouTube and search for music",CommandRouter.Type.YOUTUBE_SEARCH,"music");}
 @Test public void searchYouTubeFirst(){check("search youtube for cats",CommandRouter.Type.YOUTUBE_SEARCH,"cats");}
 @Test public void searchYoutubeLast(){check("search cats on YouTube",CommandRouter.Type.YOUTUBE_SEARCH,"cats");}
 @Test public void googleSearch(){check("search google for Kotlin",CommandRouter.Type.GOOGLE_SEARCH,"Kotlin");}
 @Test public void playSearch(){check("play music",CommandRouter.Type.YOUTUBE_SEARCH,"music");}
 @Test public void settings(){check("open settings",CommandRouter.Type.OPEN_SETTINGS,"");}
 @Test public void launch(){check("open WhatsApp",CommandRouter.Type.OPEN_APP,"WhatsApp");}
 @Test public void chat(){check("what is the weather?",CommandRouter.Type.CHAT,"what is the weather?");}
 @Test public void empty(){check(" ",CommandRouter.Type.EMPTY,"");}
 @Test public void unicodeSearch(){check("search youtube for தமிழ் பாடல்",CommandRouter.Type.YOUTUBE_SEARCH,"தமிழ் பாடல்");}
 private void check(String input,CommandRouter.Type expectedType,String expectedValue){CommandRouter.Command c=CommandRouter.parse(input);assertEquals(expectedType,c.type);assertEquals(expectedValue,c.value);}
}