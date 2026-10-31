package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** Choose a locally owned video via Android's document picker or an authorized HTTPS video URL. */
public class AppleAerialSettingsActivity extends Activity {
    private static final int CHOOSE=745;
    private LinearLayout body;
    private String current(){return getSharedPreferences(AppleAerialDream.PREFS,MODE_PRIVATE).getString("video_uri","");}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override public void onCreate(Bundle b){super.onCreate(b);
        body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);body.setPadding(dp(65),dp(32),dp(65),dp(32));
        body.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF280919,0xFF171C30,0xFF080A13}));
        setContentView(body);draw();
    }
    private void text(String name,int size,int color){TextView v=new TextView(this);v.setText(name);v.setTextSize(size);
        v.setTextColor(color);v.setPadding(dp(16),dp(10),dp(16),dp(10));body.addView(v);}
    private void button(String name,Runnable action){TextView b=new TextView(this);b.setText(name);b.setTextSize(18);b.setTextColor(Color.WHITE);
        b.setFocusable(true);b.setClickable(true);b.setPadding(dp(26),dp(13),dp(26),dp(13));
        GradientDrawable shape=new GradientDrawable();shape.setColor(0xAA414054);shape.setCornerRadius(dp(18));
        shape.setStroke(dp(1),0x77FFFFFF);b.setBackground(shape);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(55));p.bottomMargin=dp(12);body.addView(b,p);
        b.setOnClickListener(v->action.run());b.setOnFocusChangeListener((v,f)->b.setAlpha(f?1f:0.85f));}
    private void save(String uri){getSharedPreferences(AppleAerialDream.PREFS,MODE_PRIVATE)
            .edit().putString("video_uri",uri).apply();draw();}
    private void draw(){body.removeAllViews();text("航拍屏保 · Apple TV 风格",30,Color.WHITE);
        String uri=current();text(uri.isEmpty()?"未选择视频：屏保显示深色渐变时钟。":"当前视频："+uri,15,0xFFCCCBD6);
        text("这是桌面 APK 内置的 DreamService，无需安装 Aerial Views。建议选择 1080P H.264 MP4，避免老款海思盒子解码失败。",16,0xFFE0DEE9);
        button("选择本地航拍 MP4 视频",()->{
            try {Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("video/*");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(i,CHOOSE);
            }catch(Exception e){Toast.makeText(this,"固件未提供文件选择器，可使用视频链接",Toast.LENGTH_LONG).show();}
        });
        button("输入授权的视频直链（MP4）",()->{
            EditText edit=new EditText(this);edit.setSingleLine(true);edit.setText(current());
            edit.setHint("https://.../aerial.mp4");edit.setTextColor(Color.BLACK);
            new AlertDialog.Builder(this).setTitle("航拍视频地址").setView(edit).setNegativeButton("取消",null)
                    .setPositiveButton("保存",(d,w)->{String v=edit.getText().toString().trim();
                        if(!v.startsWith("https://")){Toast.makeText(this,"仅支持 https:// 视频直链",Toast.LENGTH_LONG).show();return;}
                        save(v);
                    }).show();
        });
        button("关闭视频，使用苹果风格时钟屏保",()->save(""));
        button("返回控制中心",this::finish);
        text("注意：设置视频后需在系统屏保配置里启用本桌面的 AppleAerialDream；如果系统没有屏保选择页面，可用 ADB 设置。",14,0xFFA7A9B8);
        if(body.getChildCount()>3)body.getChildAt(3).requestFocus();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(request!=CHOOSE||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri uri=data.getData();try{
            getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
            save(uri.toString());
        }catch(Exception e){Toast.makeText(this,"不能持久读取该文件，请更换来源",Toast.LENGTH_LONG).show();}
    }
}
