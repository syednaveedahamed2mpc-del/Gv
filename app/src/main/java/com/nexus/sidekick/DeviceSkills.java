package com.nexus.sidekick;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import org.json.JSONObject;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Strict allowlist and bounded execution. No transaction submission, SMS send, delete, password input. */
public final class DeviceSkills {
    private final Context app;
    private volatile String originalRequest="";
    DeviceSkills(Context context){app=context.getApplicationContext();}
    void forRequest(String request){originalRequest=request.toLowerCase(Locale.ROOT);}
    interface Task {String run() throws Exception;}
    private String onMain(Task task) throws Exception {
        if(Looper.myLooper()==Looper.getMainLooper())return task.run();
        CountDownLatch latch=new CountDownLatch(1);String[] result={"Action unavailable"};
        new Handler(Looper.getMainLooper()).post(()->{try{result[0]=task.run();}catch(Exception e){result[0]="Action failed: "+e.getClass().getSimpleName();}finally{latch.countDown();}});
        return latch.await(5,TimeUnit.SECONDS)?result[0]:"Action timed out";
    }
    public String run(String name,JSONObject args) throws Exception {
        final JSONObject values=args==null?new JSONObject():args;
        String input=values.optString("text",values.optString("query",values.optString("app",""))).trim();
        if(input.length()>300)return "Argument too long";
        Diagnostics.record(app,"SKILL","Requested: "+ToolGuard.truncate(name,45));
        switch(name){
            case "open_app":return onMain(()->openApp(values.optString("app","")));
            case "youtube_search":return onMain(()->browse("https://www.youtube.com/results?search_query="+Uri.encode(values.optString("query","")),true));
            case "web_search":return onMain(()->browse("https://www.google.com/search?q="+Uri.encode(values.optString("query","")),false));
            case "open_settings":return onMain(()->{Intent i=new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);app.startActivity(i);return "Opened settings";});
            case "go_home":case "go_back":case "recent_apps":case "open_notifications":
                return onMain(()->withService(name, ""));
            case "scroll_down":case "scroll_up":return onMain(()->withService(name,""));
            case "tap_text":return onMain(()->withService(name,values.optString("text","")));
            case "type_text":return onMain(()->withService(name,values.optString("text","")));
            case "read_screen":
                if(!(originalRequest.contains("screen")||originalRequest.contains("read what's")||originalRequest.contains("what is shown")))return "Blocked: ask explicitly to read the screen.";
                return onMain(()->withService(name,""));
            default:return "Unsupported skill: "+name;
        }
    }
    private String browse(String link,boolean youtube){
        Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if(youtube){Intent target=new Intent(i).setPackage("com.google.android.youtube");try{app.startActivity(target);return "Opened YouTube results";}catch(Exception ignored){}}
        try{app.startActivity(i);return "Opened search results";}catch(Exception ex){return "No app can open search results";}
    }
    private String openApp(String name){
        if(name==null||name.trim().isEmpty())return "Please specify an app name";
        String n=name.trim().toLowerCase(Locale.ROOT);
        if(n.equals("youtube"))return browse("https://youtube.com",true);
        if(n.equals("browser")||n.equals("chrome"))return browse("https://google.com",false);
        Intent intent=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        PackageManager pm=app.getPackageManager();List<ResolveInfo> choices=pm.queryIntentActivities(intent,0);
        for(ResolveInfo info:choices){
            String label=info.loadLabel(pm).toString();
            if(label.equalsIgnoreCase(name.trim())||label.toLowerCase(Locale.ROOT).startsWith(n+" ")){
                Intent launch=pm.getLaunchIntentForPackage(info.activityInfo.packageName);
                if(launch!=null){launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);app.startActivity(launch);return "Opened "+label;}
            }
        }
        return "Could not locate installed app: "+ToolGuard.truncate(name,40);
    }
    private String withService(String action,String text){
        NexusAccessibilityService service=NexusAccessibilityService.active;
        if(service==null)return "Accessibility skill disabled. Enable NEXUS in Android Accessibility settings first.";
        boolean ok;
        switch(action){
            case "go_home":case "go_back":case "recent_apps":case "open_notifications":ok=service.global(action);break;
            case "scroll_up":case "scroll_down":ok=service.scroll(action.equals("scroll_down"));break;
            case "tap_text":if(ToolGuard.isSensitiveTap(text))return "Blocked sensitive or ambiguous button. Tap manually.";ok=service.tapLabel(text);break;
            case "type_text":if(text.isEmpty()||text.length()>300)return "Invalid text length";ok=service.typeText(text);break;
            case "read_screen":return service.screenSummary();
            default:return "Not an Accessibility skill";
        }
        return ok?"Completed "+action:"Could not perform "+action+" on current screen";
    }
}
