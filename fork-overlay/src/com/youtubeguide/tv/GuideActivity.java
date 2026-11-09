package com.youtubeguide.tv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.app.AlertDialog;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;
import java.net.URL;
import com.liskovsoft.smartyoutubetv2.tv.ui.widgets.embedplayer.EmbedPlayerView;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import java.util.ArrayList;
import java.util.List;

/** Native, remote-first TV guide. No account, no channel editor, no embedded credentials. */
public final class GuideActivity extends LeanbackActivity {
    private static final int NAVY=Color.rgb(7,24,49), BLUE=Color.rgb(13,55,108), GOLD=Color.rgb(255,218,91), LIGHT=Color.rgb(229,239,253);
    private static final int[] CODE={KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT};
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final List<String> categories=new ArrayList<>();
    private final Runnable clock=new Runnable(){@Override public void run(){repo.refresh(false,(c,s)->{if(c)renderList();});refreshCurrent();handler.postDelayed(this,15000);}};
    private GuideRepository repo;
    private GuideRepository.Channel selected;
    private long guideTimeOffset;
    private int categoryIndex,codePos;
    private LinearLayout screen,list;private TextView status,selectedTitle,selectedInfo,categoryLabel,timeLabel;
    private ImageView thumbnail;
    private FrameLayout previewFrame;
    private EmbedPlayerView livePreview;
    private String playingPreviewId="";
    private int previewRequest;
    private String lastPreviewId = "";

    @Override public void onCreate(Bundle saved){super.onCreate(saved);repo=GuideRepository.get(this);buildUI();renderList();repo.refresh(true,(changed,s)->{status.setText(s);renderList();});}
    @Override protected void onResume(){super.onResume();handler.removeCallbacks(clock);handler.post(clock);handler.postDelayed(()->refreshCurrent(),300);}
    @Override protected void onPause(){handler.removeCallbacks(clock);stopPreview();super.onPause();}
    private int dp(float v){return (int)(getResources().getDisplayMetrics().density*v);}
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView txt(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private Button button(String label,Runnable action){Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setTextSize(15);b.setTextColor(Color.WHITE);b.setBackground(bg(BLUE,5));b.setOnClickListener(v->action.run());b.setFocusable(true);return b;}
    private void add(LinearLayout p,View v,int w,int h){p.addView(v,new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h)));}
    private void buildUI(){
        screen=new LinearLayout(this);screen.setOrientation(LinearLayout.VERTICAL);screen.setBackgroundColor(NAVY);screen.setPadding(dp(20),dp(12),dp(20),dp(12));setContentView(screen);
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);add(screen,heading,-1,48);
        TextView brand=txt("YouTube Guide",32,GOLD);brand.setTypeface(Typeface.DEFAULT,Typeface.BOLD);heading.addView(brand,new LinearLayout.LayoutParams(0,-1,1));
        status=txt("Loading live channels…",13,LIGHT);add(heading,status,310,-1);
        LinearLayout top=new LinearLayout(this);top.setPadding(0,dp(8),0,dp(10));add(screen,top,-1,182);
        FrameLayout preview=new FrameLayout(this);previewFrame=preview;preview.setBackground(bg(Color.BLACK,7));top.addView(preview,new LinearLayout.LayoutParams(dp(285),-1));
        thumbnail=new ImageView(this);thumbnail.setScaleType(ImageView.ScaleType.CENTER_CROP);preview.addView(thumbnail,new FrameLayout.LayoutParams(-1,-1));
        TextView logo=txt("LIVE PREVIEW",12,GOLD);logo.setPadding(dp(9),dp(5),dp(9),dp(5));logo.setBackground(bg(NAVY,4));FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-2,dp(26),Gravity.TOP|Gravity.LEFT);preview.addView(logo,lp);
        LinearLayout desc=new LinearLayout(this);desc.setOrientation(LinearLayout.VERTICAL);desc.setPadding(dp(18),0,0,0);top.addView(desc,new LinearLayout.LayoutParams(0,-1,1));
        selectedTitle=txt("Select a channel",24,Color.WHITE);selectedTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);add(desc,selectedTitle,-1,50);
        selectedInfo=txt("Now on your guide",16,LIGHT);add(desc,selectedInfo,-1,60);
        Button watch=button("WATCH CHANNEL LIVE  ▶",this::watch);add(desc,watch,245,55);
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER_VERTICAL);add(screen,nav,-1,56);
        add(nav,button("◀ 2h",()->moveGuide(-120)),96,52);add(nav,button("◀ 1h",()->moveGuide(-60)),96,52);
        add(nav,button("◀ 30m",()->moveGuide(-30)),108,52);add(nav,button("NOW",()->{guideTimeOffset=0;renderList();}),80,52);
        add(nav,button("30m ▶",()->moveGuide(30)),106,52);add(nav,button("1h ▶",()->moveGuide(60)),96,52);
        add(nav,button("2h ▶",()->moveGuide(120)),96,52);
        add(nav,button("CATEGORY ▼",this::rotateCategory),155,52);
        categoryLabel=txt("All categories",13,LIGHT);nav.addView(categoryLabel,new LinearLayout.LayoutParams(0,-1,1));
        timeLabel=txt("",14,GOLD);add(screen,timeLabel,-1,28);
        ScrollView scroll=new ScrollView(this);screen.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);
        TextView hint=txt("DPAD / OK: select and watch   •   Back: return from video   •   Guide updates every 5 minutes",12,LIGHT);add(screen,hint,-1,28);
    }
    private void moveGuide(long minutes){guideTimeOffset=Math.max(-72*60,Math.min(100*60,guideTimeOffset+minutes));renderList();}
    private void rotateCategory(){categoryIndex=(categoryIndex+1)%Math.max(1,categories.size());renderList();}
    private void renderList(){
        if(list==null)return;
        List<GuideRepository.Channel> channels=repo.channels();
        if(selected!=null){boolean found=false;for(GuideRepository.Channel ch:channels){if(ch.id.equals(selected.id)){selected=ch;found=true;break;}}if(!found){selected=null;stopPreview();}}
        categories.clear();categories.add("All");for(GuideRepository.Channel ch:channels)if(!categories.contains(ch.category))categories.add(ch.category);
        if(categoryIndex>=categories.size())categoryIndex=0;
        categoryLabel.setText(categories.get(categoryIndex));
        long t=System.currentTimeMillis()+guideTimeOffset*60000L;
        java.text.SimpleDateFormat fmt=new java.text.SimpleDateFormat("EEE MMM d  h:mm a",java.util.Locale.getDefault());
        timeLabel.setText("TV GUIDE  •  "+fmt.format(new java.util.Date(t))+"  •  "+channels.size()+" channels");
        list.removeAllViews();
        for(GuideRepository.Channel ch:channels){
            if(categoryIndex!=0&&!categories.get(categoryIndex).equals(ch.category))continue;
            GuideRepository.Program p=repo.at(ch,t);
            LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);line.setPadding(dp(12),dp(3),dp(12),dp(3));line.setBackground(bg(ch==selected?BLUE:Color.rgb(12,37,69),4));
            LinearLayout.LayoutParams rowLp=new LinearLayout.LayoutParams(-1,dp(66));rowLp.bottomMargin=dp(4);list.addView(line,rowLp);
            TextView no=txt(String.valueOf(ch.number),25,GOLD);no.setTypeface(Typeface.DEFAULT,Typeface.BOLD);add(line,no,72,-1);
            TextView name=txt(ch.name,19,Color.WHITE);name.setTypeface(Typeface.DEFAULT,Typeface.BOLD);add(line,name,245,-1);
            String title=p==null?"No programs scheduled":p.title;
            TextView program=txt(title,16,LIGHT);line.addView(program,new LinearLayout.LayoutParams(0,-1,1));
            if(p!=null){TextView when=txt(fmt.format(new java.util.Date(p.start)),13,LIGHT);add(line,when,170,-1);}
            line.setFocusable(true);line.setClickable(true);
            line.setOnFocusChangeListener((v,hasFocus)->{if(hasFocus){selected=ch;refreshCurrent();}});
            line.setOnClickListener(v->{selected=ch;watch();});
            if(ch==selected){line.setBackground(bg(BLUE,4));}
        }
        if(selected==null&&!channels.isEmpty()){selected=channels.get(0);refreshCurrent();}
    }
    private void refreshCurrent(){
        if(selected==null||selectedTitle==null)return;
        long now=System.currentTimeMillis();GuideRepository.Program p=repo.at(selected,now);
        selectedTitle.setText("CH "+selected.number+"  •  "+selected.name);
        selectedInfo.setText(p==null?"No scheduled video":"NOW PLAYING: "+p.title+"\n"+p.creator+"  •  "+Math.max(0,(p.end-now)/60000)+" minutes left");
        if(p!=null && !p.id.equals(lastPreviewId)){String id=p.id;lastPreviewId=id;int serial=++previewRequest;new Thread(()->{
            try{Bitmap bitmap=BitmapFactory.decodeStream(new URL("https://i.ytimg.com/vi/"+id+"/mqdefault.jpg").openStream());
                handler.post(()->{if(serial==previewRequest&&thumbnail!=null)thumbnail.setImageBitmap(bitmap);});
            }catch(Exception ignored){}
        },"PreviewImage").start();}
        // SmartTube's embedded ExoPlayer renders the moving preview inside the guide.
        // Keep it muted so browsing the grid doesn't compete with fullscreen audio.
        if(p!=null && !p.id.equals(playingPreviewId))startPreview(p);
        if(p==null)stopPreview();
    }
    private void stopPreview(){
        if(livePreview!=null){
            try{livePreview.finish();}catch(Exception ignored){}
            if(previewFrame!=null)previewFrame.removeView(livePreview);
            livePreview=null;
        }
        playingPreviewId="";
    }
    private void startPreview(GuideRepository.Program p){
        if(previewFrame==null||p==null)return;
        if(livePreview==null){
            livePreview=new EmbedPlayerView(this);
            livePreview.setUseController(false);
            livePreview.setQuality(EmbedPlayerView.QUALITY_LOW);
            livePreview.setMute(true);
            livePreview.setFocusable(false);
            previewFrame.addView(livePreview, 1, new FrameLayout.LayoutParams(-1,-1));
            // The LIVE PREVIEW badge remains visible over the embedded player.
        }
        Video video=Video.from(p.id);
        video.pendingPosMs=Math.max(0,System.currentTimeMillis()-p.start);
        try{livePreview.openVideo(video);playingPreviewId=p.id;}
        catch(Exception e){stopPreview();status.setText("Preview unavailable: "+e.getMessage());}
    }
    private void watch(){
        if(selected==null)return;
        GuideRepository.Program p=repo.at(selected,System.currentTimeMillis());
        if(p==null){new AlertDialog.Builder(this).setMessage("No available video for this channel.").setPositiveButton("OK",null).show();return;}
        getPreferences(MODE_PRIVATE).edit().putString("channel_id",selected.id).apply();
        GuidePlayback.activeChannel=selected.id;
        GuidePlayback.repo=repo;
        GuidePlayback.activeVideo=p.id;
        // Stop the preview before switching to SmartTube's fullscreen playback view.
        stopPreview();
        PlaybackPresenter.instance(this).openVideo(p.id,false,Math.max(0,System.currentTimeMillis()-p.start),false);
    }
    // Capture BEFORE the focused guide row consumes D-pad navigation.
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0){
            int key=event.getKeyCode();
            if(key==CODE[codePos])codePos++;else codePos=(key==CODE[0]?1:0);
            if(codePos==CODE.length){codePos=0;debugMenu();return true;}
        }
        return super.dispatchKeyEvent(event);
    }
    private void debugMenu(){new AlertDialog.Builder(this).setTitle("YouTube Guide diagnostics")
       .setMessage("Channels: "+repo.channels().size()+"\nPublished: "+repo.updated()+"\nCurrent URL: "+GuideRepository.RAW_URL)
       .setPositiveButton("Reload GitHub channels",(d,w)->repo.refresh(true,(changed,s)->{status.setText(s);renderList();}))
       .setNegativeButton("Close",null).show();}
}
