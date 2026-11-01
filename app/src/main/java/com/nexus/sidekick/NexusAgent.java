package com.nexus.sidekick;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared chat/tool state for both the main screen and overlay. */
public final class NexusAgent {
    public interface Listener {
        void status(String status);
        void reply(String text);
        void approval(String request,Runnable approve,Runnable deny);
    }
    private static NexusAgent shared;
    public static synchronized NexusAgent get(Context context){if(shared==null)shared=new NexusAgent(context.getApplicationContext());return shared;}
    private final Context app;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final ArrayList<JSONObject> history=new ArrayList<>();
    private boolean busy=false;
    private NexusAgent(Context app){this.app=app;}
    private void status(Listener l,String s){main.post(()->l.status(s));}
    private void reply(Listener l,String s){main.post(()->l.reply(s));}
    public synchronized void clearConversation(){history.clear();}
    public void checkConnection(Listener listener){executor.execute(()->{
        status(listener,"Checking Gemini connection…");
        try{
            String key=SecureKeyStore.load(app);
            if(key.isEmpty()){status(listener,"Save your Gemini API key first.");return;}
            String model=GeminiBridge.discover(key);Diagnostics.record(app,"GEMINI","Model list succeeded");
            status(listener,"Connected to Gemini • "+model);
        }catch(Exception e){Diagnostics.record(app,"GEMINI","Test failed "+e.getClass().getSimpleName());status(listener,explain(e));}
    });}
    public void ask(String prompt,Listener listener){
        if(prompt==null||prompt.trim().isEmpty()){status(listener,"Tell me what to do first.");return;}
        synchronized(this){if(busy){status(listener,"NEXUS is completing another request.");return;}busy=true;}
        executor.execute(()->{
            try{
                String key=SecureKeyStore.load(app);if(key.isEmpty())throw new IllegalStateException("Save your Gemini key in NEXUS settings first.");
                String model=GeminiBridge.discover(key);
                ArrayList<JSONObject> messages;
                synchronized(this){messages=new ArrayList<>(history);}
                messages.add(content("user",new JSONArray().put(new JSONObject().put("text",prompt.trim()))));
                Diagnostics.record(app,"GEMINI","Chat started • "+model);status(listener,"Gemini is thinking…");
                continueConversation(key,model,messages,listener,0);
            }catch(Exception e){fail(listener,e);}
        });
    }
    private static JSONObject content(String role,JSONArray parts)throws Exception {return new JSONObject().put("role",role).put("parts",parts);}
    private void continueConversation(String key,String model,ArrayList<JSONObject> messages,Listener listener,int round){
        try{
            if(round>=5){complete(messages,listener,"I stopped after several actions to avoid an unsafe loop.");return;}
            JSONObject returned=GeminiBridge.generate(key,model,new JSONArray(messages));
            messages.add(returned);JSONArray parts=returned.optJSONArray("parts");
            JSONObject call=null;StringBuilder answer=new StringBuilder();
            for(int i=0;i<parts.length();i++){
                JSONObject part=parts.optJSONObject(i);if(part==null)continue;
                if(call==null)call=part.optJSONObject("functionCall");
                String text=part.optString("text","");if(!text.isEmpty())answer.append(text);
            }
            if(call==null){complete(messages,listener,answer.length()>0?answer.toString():"Gemini did not return a text response.");return;}
            final JSONObject toolCall=call;
            final String tool=call.optString("name","");JSONObject args=call.optJSONObject("args");
            final JSONObject finalArgs=args==null?new JSONObject():args;
            Diagnostics.record(app,"TOOL","Requested "+tool.replaceAll("[^a-zA-Z_]",""));
            SkillExecutor.Result result=SkillExecutor.execute(app,tool,finalArgs,false);
            if(result.needsApproval){
                status(listener,"Action needs your approval.");
                main.post(()->listener.approval(result.message,
                    ()->executor.execute(()->continueAction(key,model,messages,listener,round,toolCall,tool,finalArgs,true)),
                    ()->executor.execute(()->continueAction(key,model,messages,listener,round,toolCall,tool,finalArgs,false))));
            }else sendResult(key,model,messages,listener,round,toolCall,tool,result);
        }catch(Exception e){fail(listener,e);}
    }
    private void continueAction(String key,String model,ArrayList<JSONObject> messages,Listener listener,int round,JSONObject call,String tool,JSONObject args,boolean approve){
        SkillExecutor.Result r=approve?SkillExecutor.execute(app,tool,args,true):new SkillExecutor.Result(false,false,"User declined the action.");
        sendResult(key,model,messages,listener,round,call,tool,r);
    }
    private void sendResult(String key,String model,ArrayList<JSONObject> messages,Listener listener,int round,JSONObject call,String tool,SkillExecutor.Result result){
        try{
            Diagnostics.record(app,"TOOL",tool.replaceAll("[^a-zA-Z_]","")+" • "+(result.success?"success":"not completed"));
            status(listener,result.message);
            JSONObject response=new JSONObject().put("name",tool).put("response",new JSONObject().put("success",result.success).put("message",result.message));
            if(call.has("id"))response.put("id",call.optString("id"));
            messages.add(content("user",new JSONArray().put(new JSONObject().put("functionResponse",response))));
            continueConversation(key,model,messages,listener,round+1);
        }catch(Exception e){fail(listener,e);}
    }
    private void complete(ArrayList<JSONObject> messages,Listener l,String answer){
        synchronized(this){history.clear();int from=Math.max(0,messages.size()-12);history.addAll(messages.subList(from,messages.size()));busy=false;}
        Diagnostics.record(app,"GEMINI","Conversation turn completed");reply(l,answer);
    }
    private void fail(Listener l,Exception e){synchronized(this){busy=false;}
        Diagnostics.record(app,"GEMINI","Error "+e.getClass().getSimpleName()+(e instanceof GeminiBridge.Problem?" HTTP "+((GeminiBridge.Problem)e).code:""));
        status(l,explain(e));
    }
    private static String explain(Exception e){if(e instanceof GeminiBridge.Problem||e instanceof IllegalStateException)return e.getMessage();return "Gemini connection failed: "+e.getClass().getSimpleName();}
}
