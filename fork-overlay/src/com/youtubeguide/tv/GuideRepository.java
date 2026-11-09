package com.youtubeguide.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Shared broadcast-clock repository. No GitHub/YouTube credentials needed. */
public final class GuideRepository {
    public static final String CHANNELS_URL = "https://anticgoldfish.github.io/youtube-guide-surfer/channels.json";
    // GitHub Pages publishes the contents of YouTube-Guide/, not the repo root.
    public static final String RAW_URL = "https://raw.githubusercontent.com/anticgoldfish/youtube-guide-surfer/main/YouTube-Guide/channels.json";
    private static final long HOUR = 3600000L;
    private static GuideRepository instance;
    private JSONObject document = new JSONObject();
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean busy;
    private long lastCheck;
    public interface Callback { void finished(boolean changed, String status); }
    public static final class Program {
        public final String id, title, creator;
        public final long start, end;
        public Program(String i,String t,String c,long s,long e){id=i;title=t;creator=c;start=s;end=e;}
    }
    public static final class Channel {
        public final String id,name,category;
        public final int number;
        public final JSONObject source;
        public Channel(JSONObject j){source=j;id=j.optString("id");name=j.optString("name","Untitled");category=j.optString("category","Other");number=j.optInt("number");}
    }
    private GuideRepository(Context ctx){prefs=ctx.getApplicationContext().getSharedPreferences("guide_cache",0);try{document=new JSONObject(prefs.getString("json","{}"));}catch(Exception ignored){}}
    public static synchronized GuideRepository get(Context ctx){if(instance==null)instance=new GuideRepository(ctx);return instance;}
    public synchronized List<Channel> channels(){List<Channel> out=new ArrayList<>();JSONArray items=document.optJSONArray("channels");if(items!=null)for(int i=0;i<items.length();i++){JSONObject j=items.optJSONObject(i);if(j!=null)out.add(new Channel(j));}out.sort((a,b)->Integer.compare(a.number,b.number));return out;}
    public synchronized String updated(){return document.optString("updatedAt", "not published yet");}
    public synchronized Program at(Channel ch,long time){
        JSONObject j=ch.source;
        Program p=within(j.optJSONObject("nextSchedule"),j,time);
        if(p!=null)return p;
        p=within(j.optJSONObject("schedule"),j,time);
        if(p!=null)return p;
        JSONArray pool=j.optJSONArray("videos");
        if(j.optJSONArray("pendingVideos")!=null && time>=j.optLong("activateAt",Long.MAX_VALUE))pool=j.optJSONArray("pendingVideos");
        if(pool==null||pool.length()==0)return null;
        long anchor=j.optLong("anchor",0L),total=0;
        for(int i=0;i<pool.length();i++){JSONObject v=pool.optJSONObject(i);if(v!=null)total+=Math.max(1,v.optLong("duration",0))*1000;}
        if(total<=0)return null;
        long offset=Math.floorMod(time-anchor,total);
        for(int i=0;i<pool.length();i++){
            JSONObject v=pool.optJSONObject(i);if(v==null)continue;
            long duration=Math.max(1,v.optLong("duration",0))*1000;
            if(offset<duration){long start=time-offset;return new Program(v.optString("id"),v.optString("title","Program"),v.optString("creator", ""),start,start+duration);}
            offset-=duration;
        }
        return null;
    }
    private Program within(JSONObject schedule,JSONObject channel,long time){
        if(schedule==null||time<schedule.optLong("start",Long.MAX_VALUE)||time>=schedule.optLong("end",0))return null;
        JSONArray entries=schedule.optJSONArray("programs");if(entries==null)return null;
        String id=null;long start=0,end=0;
        for(int i=0;i<entries.length();i++){
            JSONObject p=entries.optJSONObject(i);if(p==null)continue;
            long s=p.optLong("start"),e=p.optLong("end");
            if(time>=s&&time<e){id=p.optString("videoId");start=s;end=e;break;}
        }
        if(id==null)return null;
        JSONArray videos=channel.optJSONArray("videos");
        JSONArray pending=channel.optJSONArray("pendingVideos");
        JSONObject metadata=find(videos,id);if(metadata==null)metadata=find(pending,id);
        return new Program(id,metadata==null?id:metadata.optString("title",id),metadata==null?"":metadata.optString("creator",""),start,end);
    }
    private JSONObject find(JSONArray pool,String id){if(pool!=null)for(int i=0;i<pool.length();i++){JSONObject v=pool.optJSONObject(i);if(v!=null&&id.equals(v.optString("id")))return v;}return null;}
    public synchronized void refresh(boolean force, Callback cb){
        long now=System.currentTimeMillis();if(busy){if(cb!=null)cb.finished(false,"Refresh already running");return;}
        if(!force && now-lastCheck<300000L){if(cb!=null)cb.finished(false,"Up to date");return;}
        busy=true;lastCheck=now;
        new Thread(()->{
            boolean changed=false;String status="Channel refresh failed; using saved channels";
            try{
                // Live Pages JSON first, raw repository JSON as fallback.
                String txt;
                try{txt=download(CHANNELS_URL);}catch(Exception ignored){txt=download(RAW_URL);}
                JSONObject incoming=new JSONObject(txt);
                if(incoming.optJSONArray("channels")==null)throw new IllegalArgumentException("Missing channel list");
                synchronized(this){
                    changed=!txt.equals(prefs.getString("json",""));
                    document=incoming;
                    prefs.edit().putString("json",txt).apply();
                }
                status="Loaded "+incoming.getJSONArray("channels").length()+" channels";
            }catch(Exception e){status="Offline: "+e.getMessage();}
            final boolean result=changed;final String message=status;
            main.post(()->{synchronized(this){busy=false;}if(cb!=null)cb.finished(result,message);});
        },"GuideChannelSync").start();
    }
    private static String download(String uri)throws Exception{
        HttpURLConnection conn=(HttpURLConnection)new URL(uri).openConnection();
        conn.setConnectTimeout(12000);conn.setReadTimeout(18000);
        conn.setRequestProperty("Cache-Control","no-cache");conn.setRequestProperty("User-Agent","YouTubeGuideTV/1.0");
        try{if(conn.getResponseCode()!=200)throw new IllegalStateException("HTTP "+conn.getResponseCode());
            try(InputStream in=conn.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buf=new byte[16384];int n;while((n=in.read(buf))!=-1){out.write(buf,0,n);if(out.size()>64000000)throw new IllegalStateException("Channel database too large");}
                return out.toString("UTF-8");
            }
        }finally{conn.disconnect();}
    }
}
