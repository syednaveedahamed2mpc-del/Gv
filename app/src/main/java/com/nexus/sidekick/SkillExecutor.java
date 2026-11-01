package com.nexus.sidekick;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.Settings;
import org.json.JSONObject;
import java.util.List;
import java.util.Locale;

/** Device skills: allowlist-only, no arbitrary shell, no payments/messages/deletion. */
public final class SkillExecutor {
    public static final class Result{
        public final boolean success,needsApproval;public final String message;
        Result(boolean success,boolean pending,String message){this.success=success;this.needsApproval=pending;this.message=message;}
    }
    private static Result ok(String s){return new Result(true,false,s);}
    private static Result fail(String s){return new Result(false,false,s);}
    private SkillExecutor(){}
    public static Result execute(Context context,String name,JSONObject args,boolean confirmed){
        if(args==null)args=new JSONObject();
        try{
            switch(name){
                case "open_app":{
                    String app=args.optString("app","").trim();if(app.isEmpty())return fail("No app name provided.");
                    if(app.equalsIgnoreCase("camera")){context.startActivity(new Intent("android.media.action.STILL_IMAGE_CAMERA").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return ok("Opened camera.");}
                    if(app.equalsIgnoreCase("settings")){Intent i=new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(i);return ok("Opened Android Settings.");}
                    if(app.equalsIgnoreCase("youtube")){view(context,"https://www.youtube.com",true);return ok("Opened YouTube.");}
                    if(app.equalsIgnoreCase("chrome")||app.equalsIgnoreCase("browser")){view(context,"https://www.google.com",false);return ok("Opened browser.");}
                    Intent query=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                    List<ResolveInfo> list=context.getPackageManager().queryIntentActivities(query,0);
                    for(ResolveInfo r:list){if(r.loadLabel(context.getPackageManager()).toString().equalsIgnoreCase(app)){
                        Intent launch=context.getPackageManager().getLaunchIntentForPackage(r.activityInfo.packageName);
                        if(launch!=null){launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);context.startActivity(launch);return ok("Opened "+app+".");}
                    }}return fail("I couldn't find an installed app named "+app+".");
                }
                case "youtube_search":{
                    String q=args.optString("query","").trim();if(q.isEmpty())return fail("Nothing to search for.");
                    view(context,"https://www.youtube.com/results?search_query="+Uri.encode(q),true);
                    return ok("Opened YouTube search results.");
                }
                case "web_search":{
                    String q=args.optString("query","").trim();if(q.isEmpty())return fail("Nothing to search for.");
                    view(context,"https://www.google.com/search?q="+Uri.encode(q),false);return ok("Opened web search results.");
                }
                case "go_back":case "go_home":case "scroll_down":case "scroll_up":case "tap_text":case "type_text":{
                    DeviceAccessibilityService svc=DeviceAccessibilityService.current();
                    if(svc==null)return fail("Enable NEXUS Accessibility in Android settings to use screen controls.");
                    if("go_back".equals(name))return mainAction(()->svc.global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK),"Pressed Back.","Back was unavailable.");
                    if("go_home".equals(name))return mainAction(()->svc.global(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME),"Went Home.","Home was unavailable.");
                    if("scroll_down".equals(name))return mainAction(()->svc.scroll(true),"Scrolled down.","No scrollable content detected.");
                    if("scroll_up".equals(name))return mainAction(()->svc.scroll(false),"Scrolled up.","No scrollable content detected.");
                    String value=args.optString("text","").trim();
                    if(value.isEmpty()||value.length()>300)return fail("Invalid action text.");
                    if(!ActionGuard.isAllowed(value))return fail("This action is sensitive and must be completed manually.");
                    if(!confirmed)return new Result(false,true,"Allow NEXUS to "+("tap_text".equals(name)?"tap the visible button ‘"+shorten(value)+"’":"type ‘"+shorten(value)+"’ into the focused field")+"?");
                    if("tap_text".equals(name))return mainAction(()->svc.tapVisibleText(value),"Tapped the matching visible control.","Matching clickable text wasn't found.");
                    return mainAction(()->svc.typeFocused(value),"Text entered, not submitted.","Focused editable field was not available or is protected.");
                }
                default:return fail("Unknown or prohibited skill: "+name);
            }
        }catch(Exception error){Diagnostics.record(context,"SKILL",name.replaceAll("[^a-zA-Z_]","")+" failed "+error.getClass().getSimpleName());return fail("Android couldn't complete that action ("+error.getClass().getSimpleName()+").");}
    }
    private interface BoolTask { boolean run(); }
    private static Result mainAction(BoolTask task,String success,String failure){
        if(Looper.myLooper()==Looper.getMainLooper())return task.run()?ok(success):fail(failure);
        CountDownLatch done=new CountDownLatch(1);AtomicReference<Boolean> result=new AtomicReference<>(false);
        new Handler(Looper.getMainLooper()).post(()->{try{result.set(task.run());}catch(Exception ignored){}finally{done.countDown();}});
        try{if(!done.await(6,TimeUnit.SECONDS))return fail("Android screen control timed out.");}
        catch(InterruptedException e){Thread.currentThread().interrupt();return fail("Interrupted screen action.");}
        return result.get()?ok(success):fail(failure);
    }
    private static String shorten(String s){return s.length()>75?s.substring(0,75)+"…":s;}
    private static void view(Context c,String url,boolean youtube){
        Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if(youtube){Intent yt=new Intent(i).setPackage("com.google.android.youtube");
            try{c.startActivity(yt);return;}catch(Exception ignored){}}
        c.startActivity(i);
    }
}
