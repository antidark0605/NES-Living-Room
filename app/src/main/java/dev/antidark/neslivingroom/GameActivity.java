package dev.antidark.neslivingroom;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Runs the NES core on one worker thread. Native calls are made only by that thread. */
public final class GameActivity extends Activity implements SurfaceHolder.Callback {
    private static final int MAX_ROM_BYTES=32*1024*1024;
    private static final int NONE=0, SAVE=1, LOAD=2;
    private NesInput input;
    private SurfaceView surface;
    private volatile boolean running;
    private volatile boolean paused;
    private volatile int pendingAction;
    private volatile boolean foreground=true;
    private Thread worker;
    private AlertDialog pauseDialog;
    private File slotFile;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        input=new NesInput(this);
        surface=new SurfaceView(this);
        surface.getHolder().addCallback(this);
        surface.setKeepScreenOn(true);
        setContentView(surface);
    }
    private byte[] readRom() throws IOException {
        String uri=getSharedPreferences("nes_rom",MODE_PRIVATE).getString("uri",null);
        if(uri==null) throw new IOException("Choose a ROM first");
        try(InputStream stream=getContentResolver().openInputStream(Uri.parse(uri));
            ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            if(stream==null) throw new IOException("ROM unavailable");
            byte[] buf=new byte[16384]; int n;
            while((n=stream.read(buf))!=-1) {
                if(out.size()+n>MAX_ROM_BYTES) throw new IOException("ROM exceeds 32 MiB");
                out.write(buf,0,n);
            }
            return out.toByteArray();
        }
    }
    private File statePath(byte[] rom) throws NoSuchAlgorithmException {
        byte[] hash=MessageDigest.getInstance("SHA-256").digest(rom);
        StringBuilder id=new StringBuilder();
        for(int i=0;i<16;i++) id.append(String.format(java.util.Locale.ROOT,"%02x",hash[i]&255));
        File dir=new File(getFilesDir(),"states");
        if(!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create state folder");
        return new File(dir,id+".state");
    }
    @Override public void surfaceCreated(SurfaceHolder holder) { startWorker(); }
    @Override public void surfaceDestroyed(SurfaceHolder holder) { stopWorker(); }
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height) {}

    private synchronized void startWorker() {
        if(worker!=null || !foreground) return;
        running=true;
        paused=false;
        worker=new Thread(this::emulate,"NES-Emulation");
        worker.start();
    }
    private void stopWorker() {
        running=false;
        Thread t;
        synchronized(this){t=worker;}
        if(t!=null && t!=Thread.currentThread()) {
            try {t.join(3000); } catch(InterruptedException e) {Thread.currentThread().interrupt();}
        }
        input.releaseAll();
    }
    private void toast(String message) {
        runOnUiThread(()->Toast.makeText(this,message,Toast.LENGTH_SHORT).show());
    }
    /** Called on the core worker only. */
    private void processStateAction() {
        int action=pendingAction;
        if(action==NONE) return;
        pendingAction=NONE;
        try {
            if(action==SAVE) {
                byte[] state=NativeNes.saveState();
                if(state==null) throw new IOException("Core did not support Save State");
                File temp=new File(slotFile.getParentFile(),slotFile.getName()+".tmp");
                try(FileOutputStream out=new FileOutputStream(temp)){out.write(state);out.getFD().sync();}
                if(!temp.renameTo(slotFile)) {
                    if(!slotFile.delete() || !temp.renameTo(slotFile)) throw new IOException("Cannot replace Save State");
                }
                toast("Save State stored");
            } else if(action==LOAD) {
                if(!slotFile.exists()) throw new IOException("No saved state for this ROM");
                if(slotFile.length()>16*1024*1024) throw new IOException("Save State is too large");
                byte[] state=new byte[(int)slotFile.length()];
                try(FileInputStream in=new FileInputStream(slotFile)) {
                    int total=0;
                    while(total<state.length) {
                        int n=in.read(state,total,state.length-total);
                        if(n<0) throw new IOException("Incomplete Save State");
                        total+=n;
                    }
                }
                if(!NativeNes.loadState(state)) throw new IOException("Core rejected Save State");
                toast("Save State loaded");
            }
        } catch(Exception failure) {
            toast("State operation failed: "+failure.getMessage());
        }
    }
    private void emulate() {
        AudioTrack track=null;
        try {
            byte[] rom=readRom();
            slotFile=statePath(rom);
            String path=getApplicationInfo().nativeLibraryDir+"/libretro.so";
            if(!NativeNes.open(path,getFilesDir().getAbsolutePath()))
                throw new IOException("Missing or incompatible ARMv7 NES core");
            if(!NativeNes.load(rom)) throw new IOException("Core rejected NES ROM");
            int hz=NativeNes.sampleRate();
            int min=AudioTrack.getMinBufferSize(hz,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT);
            if(min>0) {
                track=new AudioTrack(AudioManager.STREAM_MUSIC,hz,AudioFormat.CHANNEL_OUT_STEREO,
                        AudioFormat.ENCODING_PCM_16BIT,Math.max(min,16384),AudioTrack.MODE_STREAM);
                if(track.getState()==AudioTrack.STATE_INITIALIZED) track.play();
                else {track.release();track=null;}
            }
            Paint paint=new Paint();paint.setFilterBitmap(false);
            Bitmap bitmap=null;int lastW=0,lastH=0;
            long frameNs=Math.round(1_000_000_000.0/NativeNes.frameRate());
            long deadline=System.nanoTime();
            boolean audioPaused=false;
            while(running) {
                processStateAction();
                if(paused) {
                    if(track!=null && !audioPaused) { track.pause();audioPaused=true; }
                    SystemClock.sleep(16);
                    deadline=System.nanoTime();
                    continue;
                }
                if(track!=null && audioPaused) {track.play();audioPaused=false;}
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
                            int cw=canvas.getWidth(),ch=canvas.getHeight();
                            int tw=Math.min(cw,Math.round(ch*4f/3f));
                            int th=Math.min(ch,Math.round(cw*3f/4f));
                            canvas.drawBitmap(bitmap,null,new Rect((cw-tw)/2,(ch-th)/2,
                                (cw+tw)/2,(ch+th)/2),paint);
                        }
                    } finally { if(canvas!=null) surface.getHolder().unlockCanvasAndPost(canvas); }
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
            String error=failure.getMessage()==null?"Unable to start NES core":failure.getMessage();
            runOnUiThread(()->{Toast.makeText(this,error,Toast.LENGTH_LONG).show();finish();});
        } finally {
            if(track!=null) {
                try {track.pause();track.flush();} catch(IllegalStateException ignored){}
                track.release();
            }
            NativeNes.close();
            synchronized(this) {worker=null;running=false;}
        }
    }
    private void showPauseMenu() {
        if(pauseDialog!=null) return;
        paused=true;
        input.releaseAll();
        pauseDialog=new AlertDialog.Builder(this).setTitle("Game paused")
            .setItems(new String[]{"Resume","Save State (slot 1)","Load State (slot 1)","Exit game"},
                (dialog,option)->{
                    pauseDialog=null;
                    if(option==3) {finish();return;}
                    if(option==1) pendingAction=SAVE;
                    if(option==2) pendingAction=LOAD;
                    paused=false;
                })
            .setOnCancelListener(dialog->{pauseDialog=null;paused=false;})
            .create();
        pauseDialog.show();
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if(pauseDialog!=null) return super.dispatchKeyEvent(event);
        int key=event.getKeyCode();
        if(key==KeyEvent.KEYCODE_BACK) {
            if(event.getAction()==KeyEvent.ACTION_DOWN && event.getRepeatCount()==0) showPauseMenu();
            return true;
        }
        if(event.getAction()==KeyEvent.ACTION_DOWN || event.getAction()==KeyEvent.ACTION_UP) {
            boolean pad=(event.getSource() & InputDevice.SOURCE_GAMEPAD)==InputDevice.SOURCE_GAMEPAD;
            boolean remoteDpad=key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN||
                key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT;
            if((pad||remoteDpad)&&input.onKey(key,event.getAction()==KeyEvent.ACTION_DOWN))
                return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override public boolean onGenericMotionEvent(MotionEvent event) {
        if(pauseDialog==null && event.getAction()==MotionEvent.ACTION_MOVE &&
                input.onMotion(event)) return true;
        return super.onGenericMotionEvent(event);
    }
    @Override protected void onPause() {
        foreground=false;
        paused=true;
        input.releaseAll();
        super.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        foreground=true;
        if(pauseDialog==null) paused=false;
        if(surface!=null && surface.getHolder().getSurface().isValid()) startWorker();
    }
    @Override protected void onDestroy() {
        stopWorker();
        if(pauseDialog!=null){pauseDialog.dismiss();pauseDialog=null;}
        super.onDestroy();
    }
}
