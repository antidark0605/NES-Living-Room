package dev.antidark.neslivingroom;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.drawable.GradientDrawable;

/** Remote-first Android TV launcher and controller mapping shell. */
public final class MainActivity extends Activity {
    private static final int PICK_ROM = 100;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private NesInput input;
    private LinearLayout column;
    private TextView testerStatus;
    private String screen = "home";
    private int capturing = -1;
    private boolean testing = false;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!testing || testerStatus == null) return;
            int mask=input.mask(SystemClock.uptimeMillis());
            StringBuilder s=new StringBuilder("Effective NES buttons: ");
            boolean any=false;
            for(int i=0;i<8;i++) if((mask&(1<<i))!=0) {
                if(any) s.append(", ");
                s.append(NesInput.NAMES[i]); any=true;
            }
            testerStatus.setText(any ? s.toString() : "Effective NES buttons: (none)");
            handler.postDelayed(this, 16);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        input=new NesInput(this);
        showHome();
    }
    private GradientDrawable bg(int color) {
        GradientDrawable d=new GradientDrawable();
        d.setColor(color); d.setCornerRadius(16); return d;
    }
    private void page(String page, String title, String sub) {
        testing=false; handler.removeCallbacks(tick);
        screen=page; testerStatus=null; capturing=-1;
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(15,18,31));
        column=new LinearLayout(this); column.setPadding(64,36,64,36);
        column.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(column);
        TextView header=new TextView(this);
        header.setText(title); header.setTextColor(Color.WHITE); header.setTextSize(30);
        header.setTypeface(null,Typeface.BOLD); column.addView(header);
        TextView hint=new TextView(this); hint.setText(sub);
        hint.setTextColor(0xffb7c2dd); hint.setTextSize(16); hint.setPadding(0,12,0,22);
        column.addView(hint);
        setContentView(scroll);
    }
    private Button addButton(String text, View.OnClickListener action) {
        Button b=new Button(this); b.setText(text); b.setTextSize(17); b.setAllCaps(false);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setTextColor(Color.WHITE); b.setBackground(bg(0xff293551));
        b.setPadding(22,10,22,10); b.setFocusable(true);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,62);
        lp.bottomMargin=10; column.addView(b,lp); b.setOnClickListener(action);
        b.setOnFocusChangeListener((v, focused)->b.setBackground(bg(focused ? 0xff4474b7 : 0xff293551)));
        return b;
    }
    private void showHome() {
        page("home","NES Living Room","Android TV • D-pad + OK + Back • FCEUmm native core • ARMv7 test build");
        addButton("Choose NES ROM", v->chooseRom());
        addButton("Play selected ROM", v->startGame());
        addButton("Controller settings",v->showSettings());
        addButton("Input tester",v->showTester());
        SharedPreferences p=getSharedPreferences("nes_rom",MODE_PRIVATE);
        String rom=p.getString("rom_name","No ROM selected");
        TextView status=new TextView(this);
        status.setText("ROM: "+rom+"\nARMv7 FCEUmm test build; game support is experimental.");
        status.setTextColor(0xffb7c2dd); status.setTextSize(16); column.addView(status);
        column.getChildAt(2).requestFocus();
    }
    private void startGame() {
        if (!getSharedPreferences("nes_rom",MODE_PRIVATE).contains("uri")) {
            android.widget.Toast.makeText(this,"Choose a NES ROM first",android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(new Intent(this,GameActivity.class));
    }
    private void chooseRom() {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent,PICK_ROM);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==PICK_ROM && result==RESULT_OK && data!=null && data.getData()!=null) {
            Uri uri=data.getData();
            String name=uri.getLastPathSegment();
            try(Cursor cursor=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
                if(cursor!=null && cursor.moveToFirst()) name=cursor.getString(0);
            } catch(Exception ignored){}
            if(name==null || !name.toLowerCase(java.util.Locale.ROOT).endsWith(".nes")) {
                android.widget.Toast.makeText(this,"Select a .nes file",android.widget.Toast.LENGTH_LONG).show();
            } else {
                try { getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION); }
                catch(SecurityException ignored){}
                getSharedPreferences("nes_rom",MODE_PRIVATE).edit().putString("uri",uri.toString()).putString("rom_name",name).apply();
            }
        }
        showHome();
    }
    private void showSettings() {
        page("settings","Controller settings","Select an action, then press a button on the physical gamepad. Back cancels capture.");
        for (int i=0;i<NesInput.NAMES.length;i++) {
            final int action=i;
            addButton(NesInput.NAMES[i]+"  •  "+KeyEvent.keyCodeToString(input.binding(i)),
                v->startCapture(action));
        }
        addButton("Turbo rate: "+input.turboHz()+" presses/sec",v->{input.cycleTurboHz();showSettings();});
        addButton("Back to home",v->showHome());
        column.getChildAt(2).requestFocus();
    }
    private void startCapture(int action) {
        capturing=action;
        page("capture","Bind "+NesInput.NAMES[action],"Press a physical gamepad key. TV remote Back cancels.");
        capturing=action;
        addButton("Cancel",v->showSettings()).requestFocus();
    }
    private void showTester() {
        page("tester","Input tester","Press the configured gamepad buttons. Turbo A/B pulse at the selected rate.");
        testerStatus=new TextView(this); testerStatus.setTextSize(22); testerStatus.setTextColor(Color.WHITE);
        testerStatus.setPadding(0,20,0,28); column.addView(testerStatus);
        addButton("Back to home",v->showHome()).requestFocus();
        testing=true; handler.post(tick);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code=event.getKeyCode();
        boolean down=event.getAction()==KeyEvent.ACTION_DOWN;
        if(capturing>=0) {
            if(down && event.getRepeatCount()==0) {
                if(code==KeyEvent.KEYCODE_BACK) {showSettings();return true;}
                // Avoid trapping remote navigation/OK keys as controller bindings.
                if(event.getDevice()!=null && (event.getSource() & InputDevice.SOURCE_GAMEPAD)==InputDevice.SOURCE_GAMEPAD) {
                    input.bind(capturing,code);showSettings();return true;
                }
            }
            return super.dispatchKeyEvent(event);
        }
        if("tester".equals(screen)) {
            if(code==KeyEvent.KEYCODE_BACK && down) {showHome();return true;}
            // A TV remote must keep control of menu focus. Only gamepad events feed NES input.
            if(code!=KeyEvent.KEYCODE_BACK && (event.getSource() & InputDevice.SOURCE_GAMEPAD)==InputDevice.SOURCE_GAMEPAD
                && input.onKey(code,down)) return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public void onBackPressed() {
        if("home".equals(screen)) super.onBackPressed();
        else if("capture".equals(screen)) showSettings();
        else showHome();
    }
    @Override protected void onPause() {
        input.releaseAll(); testing=false;handler.removeCallbacks(tick);super.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        if("tester".equals(screen)) {testing=true;handler.post(tick);}
    }
}
