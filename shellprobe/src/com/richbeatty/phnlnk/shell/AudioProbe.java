package com.richbeatty.phnlnk.shell;

import android.content.AttributionSource;
import android.content.Context;
import android.content.ContextWrapper;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Process;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Opt-in, local shell-UID cellular audio bridge.
 *
 * The caller's 16kHz, 16-bit mono PCM is sent over an authenticated loopback
 * socket to the PhnLnk host app; companion network encryption happens in the
 * app, never in the shell. No recordings are persisted.
 *
 * Routes depend on the actual firmware and SELinux/audio policy. A successful
 * PING never implies that any of the capture or injection backends work.
 */
public final class AudioProbe {
    private static final int PORT = 47327;
    private static final int RATE = 16000;
    private static final int FRAME_BYTES = 640; // 20ms
    private static final long TEST_MS = 1100;
    private static final int TONE_MS = 550;
    private final byte[] secret;
    private final AtomicBoolean occupied = new AtomicBoolean();
    private volatile boolean running = true;
    private volatile Socket streamingSocket;
    private volatile ServerSocket listener;

    private AudioProbe(byte[] key) { secret = key; }

    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !args[0].matches("[0-9a-f]{64}")) return;
        if (Process.myUid() != 2000) {
            System.err.println("Expected shell UID 2000");
            return;
        }
        AudioProbe daemon = new AudioProbe(args[0].getBytes(StandardCharsets.US_ASCII));
        try (ServerSocket server = new ServerSocket(PORT, 8,
                InetAddress.getByName("127.0.0.1"))) {
            daemon.listener = server;
            while (daemon.running) {
                try {
                    Socket socket = server.accept();
                    Thread worker = new Thread(() -> daemon.handle(socket),"phnlnk-audio");
                    worker.setDaemon(true);
                    worker.start();
                } catch (IOException error) {
                    if (daemon.running) System.err.println("audio accept: " + brief(error));
                }
            }
        }
    }

    private void handle(Socket socket) {
        try (Socket client = socket) {
            client.setSoTimeout(9000);
            DataInputStream input = new DataInputStream(client.getInputStream());
            DataOutputStream output = new DataOutputStream(client.getOutputStream());
            byte[] received = input.readUTF().getBytes(StandardCharsets.US_ASCII);
            if (!MessageDigest.isEqual(secret, received)) {
                output.writeUTF("ERROR:Invalid authorization"); output.flush(); return;
            }
            String command = input.readUTF();
            String operation=command.startsWith("STREAM:")?"STREAM":
                    command.startsWith("INJECT:")?"INJECT":command;
            switch (operation) {
                case "PING":
                    output.writeUTF("READY:V2:shell UID " + Process.myUid()
                            + (occupied.get() ? ":streaming" : ":idle"));
                    output.flush();
                    break;
                case "CAPTURE":
                    output.writeUTF(probeCapture());
                    output.flush();
                    break;
                case "INJECT":
                    output.writeUTF(probeInjection(command));
                    output.flush();
                    break;
                case "STREAM":
                    if (!occupied.compareAndSet(false,true)) {
                        output.writeUTF("ERROR:Another voice session is active");output.flush();break;
                    }
                    streamingSocket = client;
                    final AtomicBoolean replyStarted=new AtomicBoolean(false);
                    try {
                        // Always answer the host once for setup failures.
                        // Previously shellContext or a vendor audio API could
                        // throw before STREAM_READY, leaving the app with only
                        // an unhelpful EOFException from readUTF().
                        try {
                            stream(client,input,output,command,replyStarted);
                        } catch (Throwable startup) {
                            System.err.println("stream failure: " + brief(startup));
                            try {
                                // Never inject a UTF error into an already
                                // running raw PCM stream. That would corrupt
                                // audio framing after STREAM_READY.
                                if (!replyStarted.get() && !client.isClosed()) {
                                    output.writeUTF("ERROR:AUDIO_INIT:" + brief(startup));
                                    output.flush();
                                }
                            } catch (Exception ignored) { }
                        }
                    } finally {
                        streamingSocket=null;
                        occupied.set(false);
                    }
                    break;
                case "STOP":
                    running=false;
                    output.writeUTF("STOPPED");output.flush();
                    try { if(streamingSocket!=null) streamingSocket.close(); } catch(Exception ignored) {}
                    try { if(listener!=null) listener.close(); } catch(Exception ignored) {}
                    break;
                default:
                    output.writeUTF("ERROR:Unknown request");output.flush();
            }
        } catch (Throwable error) {
            // Do not leak tokens or captured speech into logs.
            System.err.println("audio command failed: " + brief(error));
        }
    }

    /**
     * Android's shell process must attribute audio capture to com.android.shell,
     * not to the system package "android". The builder honors this context on
     * modern Android. Failure here is treated as an unsupported backend.
     */
    private static Context shellContext() throws Exception {
        Class<?> at=Class.forName("android.app.ActivityThread");
        Object thread=at.getMethod("currentActivityThread").invoke(null);
        if(thread==null)thread=at.getMethod("systemMain").invoke(null);
        final Context base=(Context)at.getMethod("getSystemContext").invoke(thread);
        if(Build.VERSION.SDK_INT<31)return base;
        final AttributionSource source=new AttributionSource.Builder(Process.myUid())
                .setPackageName("com.android.shell").build();
        return new ContextWrapper(base) {
            @Override public String getPackageName() {return "com.android.shell";}
            @Override public String getOpPackageName() {return "com.android.shell";}
            @Override public AttributionSource getAttributionSource() {return source;}
            @Override public Context getApplicationContext() {return this;}
        };
    }

    private static AudioFormat format() {
        return new AudioFormat.Builder().setSampleRate(RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build();
    }

    private static AudioRecord openRecorder(String backend,Context context) throws Exception {
        AudioRecord record;
        if("REDIRECT".equals(backend)) {
            AudioManager manager=(AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            if(manager==null)throw new IllegalStateException("AudioManager unavailable");
            Method helper=AudioManager.class.getMethod("getCallDownlinkExtractionAudioRecord",
                    AudioFormat.class);
            record=(AudioRecord)helper.invoke(manager,format());
        } else {
            int source="DOWNLINK".equals(backend) ?
                    MediaRecorder.AudioSource.VOICE_DOWNLINK : MediaRecorder.AudioSource.VOICE_CALL;
            int min=AudioRecord.getMinBufferSize(RATE,AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            if(min<=0)throw new IllegalStateException("No supported PCM input format");
            AudioRecord.Builder builder=new AudioRecord.Builder()
                    .setAudioSource(source).setAudioFormat(format())
                    .setBufferSizeInBytes(Math.max(min*2,FRAME_BYTES*12));
            if(Build.VERSION.SDK_INT>=31)builder.setContext(context);
            record=builder.build();
        }
        if(record==null || record.getState()!=AudioRecord.STATE_INITIALIZED) {
            if(record!=null)record.release();
            throw new IllegalStateException(backend + " capture not initialized");
        }
        try {
            record.startRecording();
            if(record.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)
                throw new IllegalStateException(backend + " capture refused");
            return record;
        }catch(Exception error){record.release();throw error;}
    }

    private static final String[] BACKENDS={"REDIRECT","DOWNLINK","CALL"};

    private static String probeCapture() {
        StringBuilder results=new StringBuilder();
        try {
            Context context=shellContext();
            for(String backend:BACKENDS) {
                AudioRecord record=null;
                try {
                    record=openRecorder(backend,context);
                    long count=0, energy=0;
                    int peak=0;
                    short[] data=new short[FRAME_BYTES/2];
                    long until=System.nanoTime()+TEST_MS*1000000L;
                    while(System.nanoTime()<until && count<RATE*2) {
                        int n=record.read(data,0,data.length,AudioRecord.READ_BLOCKING);
                        if(n<0)throw new IOException("read failed "+n);
                        for(int i=0;i<n;i++) {
                            int sample=data[i];
                            int abs=Math.abs(sample);
                            if(abs>peak)peak=abs;
                            energy+=(long)sample*sample;
                        }
                        count+=n;
                    }
                    long rms=count==0?0:(long)Math.sqrt((double)energy/count);
                    if(results.length()>0)results.append("; ");
                    results.append(backend).append(":samples=").append(count)
                            .append(",rms=").append(rms).append(",peak=").append(peak);
                    if(peak>150 && rms>80) {
                        return "CAPTURE:BEST="+backend+"; "+results.toString()+"; SIGNAL_DETECTED";
                    }
                } catch(Exception error) {
                    if(results.length()>0)results.append("; ");
                    results.append(backend).append(":").append(brief(error));
                } finally {
                    if(record!=null){try{record.stop();}catch(Exception ignored){}record.release();}
                }
            }
            return "CAPTURE:"+results.toString()+"; SILENT_OR_UNAVAILABLE";
        }catch(Exception error) {
            return "CAPTURE_ERROR:"+brief(error);
        }
    }

    private static AudioTrack openUplink(Context context,String backend) throws Exception {
        AudioTrack track;
        AudioFormat outFormat=new AudioFormat.Builder().setSampleRate(RATE)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build();
        if("REDIRECT".equals(backend)) {
            AudioManager manager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
            if(manager==null)throw new IllegalStateException("AudioManager unavailable");
            track=(AudioTrack)AudioManager.class.getMethod("getCallUplinkInjectionAudioTrack",
                    AudioFormat.class).invoke(manager,outFormat);
        } else {
            int min=AudioTrack.getMinBufferSize(RATE,AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            if(min<=0)throw new IllegalStateException("No PCM output buffer");
            AudioTrack.Builder builder=new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(outFormat)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(Math.max(min*2,FRAME_BYTES*12));
            // AudioTrack.Builder.setContext is available on some APIs/vendors.
            try {AudioTrack.Builder.class.getMethod("setContext",Context.class).invoke(builder,context);}
            catch(NoSuchMethodException ignored) {}
            track=builder.build();
            if(track!=null && track.getState()==AudioTrack.STATE_INITIALIZED) {
                AudioManager manager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
                AudioDeviceInfo destination=null;
                if(manager!=null)for(AudioDeviceInfo device:
                        manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                    if(device.getType()==AudioDeviceInfo.TYPE_TELEPHONY){destination=device;break;}
                }
                if(destination==null || !track.setPreferredDevice(destination)) {
                    track.release();
                    throw new IllegalStateException("Telephony uplink route unavailable");
                }
            }
        }
        if(track==null || track.getState()!=AudioTrack.STATE_INITIALIZED) {
            if(track!=null)track.release();
            throw new IllegalStateException(backend+" output not initialized");
        }
        try{track.play();return track;}
        catch(Exception e){track.release();throw e;}
    }

    private static String probeInjection(String command) {
        AudioTrack track=null;
        try {
            Context context=shellContext();
            StringBuilder failed=new StringBuilder();
            String backend="NONE";
            String requested=command.startsWith("INJECT:")?
                    command.substring("INJECT:".length()):"AUTO";
            String[] modes="REDIRECT".equals(requested)?new String[]{"REDIRECT"}:
                    "TELEPHONY".equals(requested)?new String[]{"TELEPHONY"}:
                    new String[]{"REDIRECT","TELEPHONY"};
            for(String mode:modes) {
                try {track=openUplink(context,mode);backend=mode;break;}
                catch(Exception error){failed.append(mode).append(":").append(brief(error)).append("; ");}
            }
            if(track==null)return "INJECT_ERROR:"+failed;
            short[] pcm=new short[RATE*TONE_MS/1000];
            for(int i=0;i<pcm.length;i++)pcm[i]=(short)(1600*Math.sin(2*Math.PI*660*i/RATE));
            int written=0;
            while(written<pcm.length) {
                int n=track.write(pcm,written,pcm.length-written,AudioTrack.WRITE_BLOCKING);
                if(n<=0)return "INJECT_ERROR:AudioTrack write "+n;
                written+=n;
            }
            Thread.sleep(TONE_MS+120L);
            return "INJECT:"+backend+":WROTE="+written+":Ask caller if they heard it";
        }catch(Exception error){return "INJECT_ERROR:"+brief(error);}
        finally{if(track!=null){try{track.stop();}catch(Exception ignored){}track.release();}}
    }

    private static void stream(Socket socket,DataInputStream input,
                               DataOutputStream output,String command,
                               AtomicBoolean replyStarted) throws Exception {
        String captureChoice="AUTO",uplinkChoice="AUTO";
        String[] options=command.split(":",-1);
        if(options.length==3){
            captureChoice=options[1];
            uplinkChoice=options[2];
        }
        if(!("AUTO".equals(captureChoice)||"REDIRECT".equals(captureChoice)
                ||"DOWNLINK".equals(captureChoice)||"CALL".equals(captureChoice))
                ||!("AUTO".equals(uplinkChoice)||"REDIRECT".equals(uplinkChoice)
                ||"TELEPHONY".equals(uplinkChoice))) {
            output.writeUTF("ERROR:Unknown audio backend settings");output.flush();return;
        }
        Context context=shellContext();
        AudioRecord record=null;
        AudioTrack track=null;
        String recordMethod="NONE",uplinkMethod="NONE";
        StringBuilder captureErrors=new StringBuilder();
        final String[] recordPlan="AUTO".equals(captureChoice)?BACKENDS:
                new String[]{captureChoice};
        for(String method:recordPlan) {
            try{record=openRecorder(method,context);recordMethod=method;break;}
            catch(Exception error){captureErrors.append(method).append(":").append(brief(error)).append("; ");}
        }
        if(record==null) {
            output.writeUTF("ERROR:No downlink capture backend: "+captureErrors);output.flush();return;
        }
        StringBuilder injectionErrors=new StringBuilder();
        String[] uplinkPlan="AUTO".equals(uplinkChoice) ?
                new String[]{"REDIRECT","TELEPHONY"}:new String[]{uplinkChoice};
        for(String method:uplinkPlan) {
            try{track=openUplink(context,method);uplinkMethod=method;break;}
            catch(Exception error){injectionErrors.append(method).append(":").append(brief(error)).append("; ");}
        }
        if(track==null) {
            try{record.stop();}catch(Exception ignored){}record.release();
            output.writeUTF("ERROR:No uplink injection backend: "+injectionErrors);output.flush();return;
        }
        final AudioRecord[] fromCall={record};
        final AudioTrack toCall=track;
        final AtomicBoolean connected=new AtomicBoolean(true);
        final boolean automatic="AUTO".equals(captureChoice);
        final int[] sourceIndex={0};
        for(int i=0;i<recordPlan.length;i++) {
            if(recordPlan[i].equals(recordMethod))sourceIndex[0]=i;
        }
        try {
            replyStarted.set(true);
            output.writeUTF("STREAM_READY:DOWN="+recordMethod+":UP="+uplinkMethod);
            output.flush();
            // The companion can still be initializing its microphone after
            // our STREAM_READY reply. A short 9s socket timeout caused the
            // shell receiver to close the whole media connection prematurely.
            socket.setSoTimeout(30000);
            Thread sender=new Thread(() -> {
                short[] samples=new short[FRAME_BYTES/2];
                byte[] pcm=new byte[FRAME_BYTES];
                int silentFrames=0;
                try {
                    while(connected.get()) {
                        int read=0;
                        while(read<samples.length && connected.get()) {
                            int n=fromCall[0].read(samples,read,samples.length-read,
                                    AudioRecord.READ_BLOCKING);
                            if(n<=0)throw new IOException("capture read "+n);
                            read+=n;
                        }
                        if(!connected.get())break;
                        int peak=0;
                        for(int i=0;i<read;i++) {
                            int v=Math.abs((int)samples[i]);
                            if(v>peak)peak=v;
                            pcm[i*2]=(byte)samples[i];
                            pcm[i*2+1]=(byte)(samples[i]>>>8);
                        }
                        if(peak<32)silentFrames++;else silentFrames=0;
                        synchronized(output){output.write(pcm);output.flush();}
                        if(automatic && silentFrames>175 && sourceIndex[0]<recordPlan.length-1) {
                            // Switch once the currently selected backend has
                            // delivered digital silence for over 3.5 seconds.
                            // A caller pause can also cause this, so never call
                            // it proof that the original backend was invalid.
                            for(int next=sourceIndex[0]+1;next<recordPlan.length;next++) {
                                sourceIndex[0]=next;
                                try {
                                    AudioRecord replacement=openRecorder(recordPlan[next],context);
                                    AudioRecord old=fromCall[0];
                                    fromCall[0]=replacement;
                                    try{old.stop();}catch(Exception ignored){}
                                    old.release();
                                    silentFrames=0;
                                    System.err.println("Downlink silence; testing "+recordPlan[next]);
                                    break;
                                }catch(Exception fail){
                                    System.err.println("Downlink fallback unavailable: "+brief(fail));
                                }
                            }
                        }
                    }
                }catch(Exception error){
                    System.err.println("Downlink stopped: "+brief(error));
                    connected.set(false);
                    try{socket.close();}catch(Exception ignored){}
                }
            },"phnlnk-call-to-host");
            Thread receiver=new Thread(() -> {
                byte[] pcm=new byte[FRAME_BYTES];
                short[] samples=new short[FRAME_BYTES/2];
                try{
                    while(connected.get()){
                        input.readFully(pcm);
                        for(int i=0;i<samples.length;i++)
                            samples[i]=(short)((pcm[i*2]&255)|(pcm[i*2+1]<<8));
                        int offset=0;
                        while(offset<samples.length && connected.get()){
                            int n=toCall.write(samples,offset,samples.length-offset,
                                    AudioTrack.WRITE_BLOCKING);
                            if(n<=0)throw new IOException("uplink write "+n);
                            offset+=n;
                        }
                    }
                }catch(Exception error){
                    System.err.println("Uplink stopped: "+brief(error));
                    connected.set(false);
                    try{socket.close();}catch(Exception ignored){}
                }
            },"phnlnk-host-to-call");
            sender.setDaemon(true);receiver.setDaemon(true);
            sender.start();receiver.start();
            while(connected.get() && sender.isAlive() && receiver.isAlive()){
                Thread.sleep(250);
            }
            connected.set(false);
            try{socket.close();}catch(Exception ignored){}
            sender.join(1200);receiver.join(1200);
        }finally{
            connected.set(false);
            try{fromCall[0].stop();}catch(Exception ignored){}
            try{track.stop();}catch(Exception ignored){}
            fromCall[0].release();track.release();
        }
    }

    private static String brief(Throwable error){
        Throwable current=error;
        while(current.getCause()!=null && current.getCause()!=current)current=current.getCause();
        String message=current.getMessage();
        String text=current.getClass().getSimpleName()+(message==null?"":": "+message);
        return text.length()>160?text.substring(0,160):text;
    }
}
