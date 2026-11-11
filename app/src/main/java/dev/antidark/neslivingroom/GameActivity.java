package dev.antidark.neslivingroom;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Fullscreen native-core NES player. No UI interaction from the emulation thread. */
public final class GameActivity extends Activity implements SurfaceHolder.Callback {
    private static final int MAX_ROM_BYTES=32*1024*1024;
    private NesInput input;
    private SurfaceView surface;
    private final AtomicBoolean running=new AtomicBoolean(false);
    private Thread emulationThread;
    private AlertDialog pauseDialog;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        input=new NesInput(this);
        surface=new SurfaceView(this);
        surface.getHolder().addCallback(this);
        surface.setKeepScreenOn(true);
        setContentView(surface);
    }
    private byte[] readRom() throws IOException {
        String s=getPreferences(MODE_PRIVATE).getString("rom_uri",null);
        // MainActivity uses its own Activity preferences, not the GameActivity namespace.
        s=getSharedPreferences("nes_rom",MODE_PRIVATE).getString("uri",s);
        if(s==null) throw new IOException("Choose a ROM first");
        try(InputStream stream=getContentResolver().openInputStream(Uri.parse(s));
            ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            if(stream==null) throw new IOException("ROM unavailable");
            byte[] temp=new byte[16384];int count;
            while((count=stream.read(temp))!=-1) {
                if(output.size()+count>MAX_ROM_BYTES) throw new IOException("ROM is larger than 32 MiB");
                output.write(temp,0,count);
            }
            return output.toByteArray();
        }
    }
    @Override public void surfaceCreated(SurfaceHolder holder) { startEmulation(); }
    @Override public void surfaceDestroyed(SurfaceHolder holder) { stopEmulation(); }
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height) {}

    private synchronized void startEmulation() {
        if(running.get() || emulationThread!=null) return;
        running.set(true);
        emulationThread=new Thread(this::emulate,"NES-Emulation");
        emulationThread.start();
    }
    private void emulate() {
        AudioTrack track=null;
        try {
            byte[] bytes=readRom();
            String path=getApplicationInfo().nativeLibraryDir+"/libretro.so";
            if(!NativeNes.open(path,getFilesDir().getAbsolutePath()))
                throw new IOException("Missing or incompatible ARMv7 NES core");
            if(!NativeNes.load(bytes)) throw new IOException("Core rejected the NES ROM");
            int hz=NativeNes.sampleRate();
            int min=AudioTrack.getMinBufferSize(hz,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT);
            if(min>0) {
                track=new AudioTrack(AudioManager.STREAM_MUSIC,hz,AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,Math.max(min,16384),AudioTrack.MODE_STREAM);
                if(track.getState()==AudioTrack.STATE_INITIALIZED) track.play();
                else {track.release();track=null;}
            }
            Paint paint=new Paint();paint.setFilterBitmap(false);
            Bitmap bitmap=null;
            int lastW=0,lastH=0;
            long frameNs=Math.round(1_000_000_000.0/NativeNes.frameRate());
            long deadline=System.nanoTime();
            while(running.get()) {
                NativeNes.runFrame(input.mask(SystemClock.uptimeMillis()));
                int w=NativeNes.frameWidth(),h=NativeNes.frameHeight();
                int[] pixels=NativeNes.framePixels();
                if(w>0 && h>0 && pixels!=null && pixels.length==w*h) {
                    if(bitmap==null || w!=lastW || h!=lastH) {
                        bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                        lastW=w;lastH=h;
                    }
                    bitmap.setPixels(pixels,0,w,0,0,w,h);
                    Canvas canvas=null;
                    try {
                        canvas=surface.getHolder().lockCanvas();
                        if(canvas!=null) {
                            canvas.drawColor(Color.BLACK);
                            // NES picture pixels are non-square: display with ~4:3 ratio.
                            int outW=canvas.getWidth(),outH=canvas.getHeight();
                            int targetW=Math.min(outW,Math.round(outH*4f/3f));
                            int targetH=Math.min(outH,Math.round(outW*3f/4f));
                            Rect dest=new Rect((outW-targetW)/2,(outH-targetH)/2,
                                (outW+targetW)/2,(outH+targetH)/2);
                            canvas.drawBitmap(bitmap,null,dest,paint);
                        }
                    } finally {if(canvas!=null) surface.getHolder().unlockCanvasAndPost(canvas);}
                }
                short[] samples=NativeNes.frameAudio();
                if(track!=null && samples!=null && samples.length>0)
                    track.write(samples,0,samples.length);
                deadline+=frameNs;
                long remaining=deadline-System.nanoTime();
                if(remaining>0) SystemClock.sleep(remaining/1_000_000L);
                else if(remaining < -frameNs*3) deadline=System.nanoTime();
            }
        } catch(Exception | UnsatisfiedLinkError failure) {
            final String msg=failure.getMessage()==null?"Unable to start emulator":failure.getMessage();
            runOnUiThread(()->{Toast.makeText(this,msg,Toast.LENGTH_LONG).show();finish();});
        } finally {
            if(track!=null) {
                try {track.pause();track.flush();} catch(IllegalStateException ignored){}
                track.release();
            }
            NativeNes.close();
            synchronized(this) {emulationThread=null;running.set(false);}
        }
    }
    private void stopEmulation() {
        running.set(false);
        Thread t=emulationThread;
        if(t!=null && t!=Thread.currentThread()) {
            try {t.join(2000);} catch(InterruptedException e) {Thread.currentThread().interrupt();}
        }
        input.releaseAll();
    }
    private void pauseMenu() {
        stopEmulation();
        pauseDialog=new AlertDialog.Builder(this)
            .setTitle("Game paused")
            .setItems(new String[]{"Resume","Controller settings (return to menu)","Exit game"},(dialog,which)->{
                if(which==0) startEmulation();
                else finish();
            })
            .setOnCancelListener(dialog->startEmulation())
            .create();
        pauseDialog.show();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key=event.getKeyCode();
        if(key==KeyEvent.KEYCODE_BACK) {
            if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0 && pauseDialog==null) pauseMenu();
            return true;
        }
        if(event.getAction()==KeyEvent.ACTION_DOWN || event.getAction()==KeyEvent.ACTION_UP) {
            // Allow TV D-pad as a fallback during gameplay; remote OK remains Android UI.
            boolean pad=(event.getSource() & InputDevice.SOURCE_GAMEPAD)==InputDevice.SOURCE_GAMEPAD;
            boolean remoteDpad=key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN||
                               key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT;
            if((pad||remoteDpad) && input.onKey(key,event.getAction()==KeyEvent.ACTION_DOWN)) return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override protected void onPause(){stopEmulation();super.onPause();}
    @Override protected void onResume(){
        super.onResume();
        if(surface!=null && surface.getHolder().getSurface().isValid() && pauseDialog==null)
            startEmulation();
    }
    @Override protected void onDestroy(){
        stopEmulation();
        if(pauseDialog!=null){pauseDialog.dismiss();pauseDialog=null;}
        super.onDestroy();
    }
}
