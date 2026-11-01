package com.nexus.sidekick;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** Gemini native function-calling transport. Keeps API key in HTTP header, never in URL or logs. */
public final class AgentGemini {
    public static final String MODEL = "gemini-3.5-flash-lite";
    private static final String BACKUP = "gemini-3.6-flash";
    private AgentGemini() {}
    public static final class ApiError extends Exception {
        public final int status;
        ApiError(int status, String message) { super(message);this.status=status; }
    }
    public static JSONObject content(String role, JSONArray parts) throws Exception {
        return new JSONObject().put("role",role).put("parts",parts);
    }
    public static JSONObject textPart(String text) throws Exception {return new JSONObject().put("text",text);}
    private static JSONObject declaration(String name, String desc, String parameter, String description) throws Exception {
        JSONObject schema=new JSONObject().put("type","OBJECT");
        JSONObject props=new JSONObject();
        if(parameter != null) {
            props.put(parameter,new JSONObject().put("type","STRING").put("description",description));
            schema.put("required",new JSONArray().put(parameter));
        }
        schema.put("properties",props);
        return new JSONObject().put("name",name).put("description",desc).put("parameters",schema);
    }
    public static JSONArray tools() throws Exception {
        JSONArray d=new JSONArray();
        d.put(declaration("open_app","Open an installed Android application by name","app","App name such as YouTube, WhatsApp or Chrome"));
        d.put(declaration("youtube_search","Open YouTube search results for a query","query","Search words"));
        d.put(declaration("web_search","Open a browser with Google search results","query","Search words"));
        d.put(declaration("open_settings","Open Android settings",null,null));
        d.put(declaration("go_home","Navigate to Android home screen",null,null));
        d.put(declaration("go_back","Navigate back in the current screen",null,null));
        d.put(declaration("recent_apps","Show Android recent apps",null,null));
        d.put(declaration("open_notifications","Open notification shade",null,null));
        d.put(declaration("scroll_down","Scroll down in the active screen",null,null));
        d.put(declaration("scroll_up","Scroll up in the active screen",null,null));
        d.put(declaration("tap_text","Tap a visible non-sensitive control identified by text","text","Exact visible label, never payment/send/approval actions"));
        d.put(declaration("type_text","Fill the currently focused normal text field, but do not submit","text","Words to type into a non-password editable field"));
        d.put(declaration("read_screen","Read visible accessibility text when user explicitly requests it",null,null));
        return new JSONArray().put(new JSONObject().put("functionDeclarations",d));
    }
    public static JSONObject ask(String key, JSONArray history, boolean allowTools) throws Exception {
        JSONObject req = new JSONObject().put("contents",history)
            .put("generationConfig",new JSONObject().put("temperature",.3).put("maxOutputTokens",512));
        req.put("systemInstruction",new JSONObject().put("parts",new JSONArray().put(textPart(
            "You are NEXUS, a concise, helpful Android voice assistant. You can use the listed device tools " +
            "only when they help accomplish the user's expressed goal. Tools execute on the user's own phone. " +
            "Never pretend an action happened without a successful tool result. Do not request passwords, " +
            "OTP, payment or account secrets. Do not buy, send messages, transfer funds, delete data, approve, " +
            "or install apps autonomously. Don't read private screen contents unless user explicitly asks. " +
            "For ordinary questions, answer naturally and conversationally in 1-3 sentences. " +
            "For device requests, call an appropriate tool. If a tool fails, explain why briefly."))));
        if(allowTools)req.put("tools",tools());
        try {return request(key,req,MODEL);} catch(ApiError ex) {
            // If the exact model is not granted to the key, try the current stable Flash model once.
            if(ex.status==404)return request(key,req,BACKUP);
            throw ex;
        }
    }
    public static void checkConnection(String key) throws Exception {
        HttpURLConnection c=null;
        try{
            c=(HttpURLConnection)new URL("https://generativelanguage.googleapis.com/v1beta/models/"+MODEL).openConnection();
            c.setConnectTimeout(10000);c.setReadTimeout(12000);c.setRequestProperty("x-goog-api-key",key);
            int code=c.getResponseCode();if(code!=200)throw new ApiError(code,"Model check HTTP "+code);
        }finally{if(c!=null)c.disconnect();}
    }
    private static JSONObject request(String key, JSONObject json, String model) throws Exception {
        HttpURLConnection c=null;
        try {
            URL u=new URL("https://generativelanguage.googleapis.com/v1beta/models/"+model+":generateContent");
            c=(HttpURLConnection)u.openConnection();c.setRequestMethod("POST");
            c.setConnectTimeout(10000);c.setReadTimeout(26000);
            c.setRequestProperty("Content-Type","application/json");c.setRequestProperty("x-goog-api-key",key);c.setDoOutput(true);
            byte[] data=json.toString().getBytes(StandardCharsets.UTF_8);
            try(OutputStream out=c.getOutputStream()){out.write(data);}
            int status=c.getResponseCode();
            InputStream input=status<300?c.getInputStream():c.getErrorStream();
            if(input==null)throw new ApiError(status,"Empty Gemini response HTTP "+status);
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int n;
            try(InputStream in=input){while((n=in.read(buffer))>=0){if(bytes.size()>750000)throw new Exception("Gemini response too large");bytes.write(buffer,0,n);}}
            JSONObject response=new JSONObject(bytes.toString("UTF-8"));
            if(status>=300){JSONObject error=response.optJSONObject("error");
                String msg=error==null?"HTTP "+status:error.optString("message","HTTP "+status);
                throw new ApiError(status,ToolGuard.truncate(msg,200));}
            JSONArray candidates=response.optJSONArray("candidates");
            if(candidates==null||candidates.length()==0)throw new ApiError(200,"Gemini returned no candidates");
            JSONObject candidate=candidates.getJSONObject(0);JSONObject content=candidate.optJSONObject("content");
            if(content==null)throw new ApiError(200,"Gemini returned no content");
            return content;
        }catch(SocketTimeoutException e){throw new ApiError(0,"Connection to Gemini timed out. Check mobile data or Wi-Fi and retry.");}
        finally{if(c!=null)c.disconnect();}
    }
}
