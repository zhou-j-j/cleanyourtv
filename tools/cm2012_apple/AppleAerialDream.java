package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.VideoView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Integrated Android DreamService. Optional user-selected aerial video + Apple-like clock overlay. */
public class AppleAerialDream extends DreamService {
    static final String PREFS="cm2012_apple_aerial";
    private FrameLayout frame;
    private TextView label;
    private VideoView video;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable ticker=new Runnable(){@Override public void run(){
        if(label!=null)label.setText(new SimpleDateFormat("HH:mm     EEEE · M月d日",Locale.CHINA).format(new Date()));
        handler.postDelayed(this,30000);
    }};
    @Override public void onAttachedToWindow(){
        super.onAttachedToWindow();setFullscreen(true);setInteractive(false);setScreenBright(false);
        frame=new FrameLayout(this);
        frame.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF250816,0xFF141827,0xFF04080E}));
        setContentView(frame);
        SharedPreferences prefs=getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String uri=prefs.getString("video_uri","");
        if(!uri.isEmpty()){
            try{
                video=new VideoView(this);
                FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER);
                frame.addView(video,p);
                video.setOnPreparedListener(m->{m.setLooping(true);m.setVolume(0f,0f);video.start();});
                video.setOnErrorListener((m,what,extra)->{removeVideo();return true;});
                video.setVideoURI(Uri.parse(uri));
            }catch(Exception e){removeVideo();}
        }
        label=new TextView(this);
        label.setTextSize(27);label.setTypeface(null,Typeface.LIGHT);label.setTextColor(Color.WHITE);
        label.setGravity(Gravity.CENTER);label.setShadowLayer(9f,2f,3f,Color.BLACK);
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.LEFT);
        int pad=Math.round(44*getResources().getDisplayMetrics().density);lp.setMargins(pad,0,0,pad);
        frame.addView(label,lp);ticker.run();
    }
    private void removeVideo(){ if(video!=null){try{video.stopPlayback();}catch(Exception ignored){}
        if(frame!=null)frame.removeView(video);video=null;} }
    @Override public void onDreamingStarted(){super.onDreamingStarted();if(video!=null)video.start();}
    @Override public void onDreamingStopped(){removeVideo();handler.removeCallbacks(ticker);super.onDreamingStopped();}
    @Override public void onDetachedFromWindow(){removeVideo();handler.removeCallbacks(ticker);super.onDetachedFromWindow();}
}
