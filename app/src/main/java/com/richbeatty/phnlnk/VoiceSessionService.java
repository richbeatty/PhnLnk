package com.richbeatty.phnlnk;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.NoiseSuppressor;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import org.json.JSONObject;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Actual two-way encrypted PCM transport from companion mic/speaker to host.
 * Hardware availability of cellular audio on the host is not guaranteed.
 * This runs as an opt-in microphone foreground service started from activity.
 */
public final class VoiceSessionService extends Service {
    static final String ACTION_START="com.richbeatty.phnlnk.voice.START";
    static final String ACTION_STOP="com.richbeatty.phnlnk.voice.STOP";
    private static final String CHANNEL="phnlnk_voice_media_v1";
    private static final String TAG="PhnLnkVoice";
    private static final int NOTICE=414;
    private static final int RATE=16000;
    private static final int FRAME=VoiceSecureTransport.FRAME_BYTES;
    private static volatile boolean active;
    private static volatile boolean muted;
    private static volatile boolean speaker=true;
    private static volatile char toneDigit;
    private static volatile int toneSampleIndex;
    private static volatile String state="Not connected";
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile boolean running;
    private volatile Socket activeSocket;
    private volatile AudioManager audioManager;

    static boolean active() {return active;}
    static boolean muted() {return muted;}
    static boolean speaker() {return speaker;}
    static String state() {return state;}

    static void setMuted(boolean value) {muted=value;}

    static void sendTone(char digit) {
        if("0123456789*#".indexOf(digit)<0)
            throw new IllegalArgumentException("Invalid keypad digit");
        toneSampleIndex=0;
        toneDigit=digit;
    }

    private static short inBandDtmf(char key,int offset) {
        String rows="123A456B789C*0#D";
        int index=rows.indexOf(key);
        if(index<0)return 0;
        int[] low={697,770,852,941};
        int[] high={1209,1336,1477,1633};
        int row=index/4, col=index%4;
        double time=(double)offset/RATE;
        return (short)(2800*Math.sin(2*Math.PI*low[row]*time)
                +2800*Math.sin(2*Math.PI*high[col]*time));
    }

    static void setSpeaker(boolean value) {
        speaker=value;
        // Routing change is applied by the live service's network worker
        // when it next opens the audio devices; persistent state remains.
    }

    @Override public void onCreate() {
        super.onCreate();
        NotificationManager manager=getSystemService(NotificationManager.class);
        if(manager!=null) manager.createNotificationChannel(new NotificationChannel(
                CHANNEL,"PhnLnk voice audio",NotificationManager.IMPORTANCE_LOW));
    }

    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent!=null && ACTION_STOP.equals(intent.getAction())) {
            shutdown();
            stopSelf();
            return START_NOT_STICKY;
        }
        if(running)return START_NOT_STICKY;
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
            state="Microphone permission is needed on this device";
            stopSelf();
            return START_NOT_STICKY;
        }
        running=true;
        state="Connecting encrypted voice...";
        Intent open=new Intent(this,ClientActivity.class);
        PendingIntent tap=PendingIntent.getActivity(this,415,open,
                PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,416,
                new Intent(this,VoiceSessionService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification=new Notification.Builder(this,CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentTitle("PhnLnk voice session")
                .setContentText("Using companion microphone and speaker")
                .setContentIntent(tap)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel,"Disconnect voice",stop).build())
                .setOngoing(true)
                .build();
        if(Build.VERSION.SDK_INT>=34)
            startForeground(NOTICE,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        else startForeground(NOTICE,notification);
        worker.execute(this::connectAndStream);
        return START_NOT_STICKY;
    }

    private void connectAndStream() {
        Socket socket=null;
        AudioRecord mic=null;
        AudioTrack speakerTrack=null;
        AcousticEchoCanceler aec=null;
        NoiseSuppressor suppressor=null;
        int oldMode=AudioManager.MODE_NORMAL;
        String phase="CHECK_COMPANION_PAIRING";
        try {
            if(!RemoteClient.isPaired(this))
                throw new IllegalStateException("Pair this device with your main phone");
            phase="CONNECT_HOST_PRIVATE_NETWORK";
            String host=new URL(RemoteClient.address(this)).getHost();
            socket=new Socket();
            socket.connect(new InetSocketAddress(host,VoiceSecureTransport.PORT),5000);
            socket.setTcpNoDelay(true);
            activeSocket=socket;
            phase="VOICE_AUTH_HANDSHAKE";
            VoiceSecureTransport.Session wire=VoiceSecureTransport.onClient(this,socket);
            phase="WAIT_HOST_AUDIO_BACKEND";
            byte[] opening=wire.receive();
            String banner=banner(opening);
            if(!banner.startsWith("PHNLNK:READY:"))
                throw new IllegalStateException(banner.startsWith("PHNLNK:ERROR:") ?
                        banner.substring("PHNLNK:ERROR:".length()) :
                        "Main phone did not start the voice bridge");
            String method=banner.substring("PHNLNK:READY:".length());
            // Initialization may take longer than one voice packet, but
            // steady-state media should not stall indefinitely.
            socket.setSoTimeout(15000);
            phase="OPEN_COMPANION_AUDIO";
            state="Voice connected ("+method+"). Caller audio needs verification.";

            audioManager=(AudioManager)getSystemService(Context.AUDIO_SERVICE);
            if(audioManager==null)throw new IllegalStateException("Android audio output unavailable");
            oldMode=audioManager.getMode();
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            setRoute();
            int micMin=AudioRecord.getMinBufferSize(RATE,AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            int outMin=AudioTrack.getMinBufferSize(RATE,AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            if(micMin<=0 || outMin<=0)
                throw new IllegalStateException("16 kHz voice audio format unavailable");
            AudioFormat input=new AudioFormat.Builder()
                    .setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build();
            AudioFormat output=new AudioFormat.Builder()
                    .setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build();
            mic=new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                    .setAudioFormat(input)
                    .setBufferSizeInBytes(Math.max(micMin*2,FRAME*12)).build();
            if(mic.getState()!=AudioRecord.STATE_INITIALIZED)
                throw new IllegalStateException("Android could not initialize microphone");
            if(AcousticEchoCanceler.isAvailable()) {
                aec=AcousticEchoCanceler.create(mic.getAudioSessionId());
                if(aec!=null)aec.setEnabled(true);
            }
            if(NoiseSuppressor.isAvailable()) {
                suppressor=NoiseSuppressor.create(mic.getAudioSessionId());
                if(suppressor!=null)suppressor.setEnabled(true);
            }
            speakerTrack=new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    .setAudioFormat(output)
                    .setBufferSizeInBytes(Math.max(outMin*2,FRAME*12))
                    .setTransferMode(AudioTrack.MODE_STREAM).build();
            if(speakerTrack.getState()!=AudioTrack.STATE_INITIALIZED)
                throw new IllegalStateException("Android could not initialize companion speaker");
            mic.startRecording();
            speakerTrack.play();
            phase="VOICE_STREAMING";
            final AudioRecord source=mic;
            final AudioTrack destination=speakerTrack;
            final VoiceSecureTransport.Session session=wire;
            active=true;
            Thread uplink=new Thread(() -> {
                short[] samples=new short[FRAME/2];
                byte[] frame=new byte[FRAME];
                try {
                    while(running) {
                        int got=0;
                        while(got<samples.length && running){
                            int n=source.read(samples,got,samples.length-got,
                                    AudioRecord.READ_BLOCKING);
                            if(n<=0)throw new IllegalStateException("Mic read "+n);
                            got+=n;
                        }
                        if(!running)break;
                        char dtmf=toneDigit;
                        for(int i=0;i<samples.length;i++){
                            short sample=muted?0:samples[i];
                            if(dtmf!=0){
                                int position=toneSampleIndex+i;
                                sample=position<RATE/5?inBandDtmf(dtmf,position):(short)0;
                            }
                            frame[2*i]=(byte)sample;
                            frame[2*i+1]=(byte)(sample>>>8);
                        }
                        if(dtmf!=0){
                            toneSampleIndex+=samples.length;
                            if(toneSampleIndex>=RATE/5)toneDigit=0;
                        }
                        session.send(frame);
                    }
                } catch(Exception error) {
                    if(running)state="Companion microphone stream stopped: "+safe(error);
                    shutdown();
                }
            },"PhnLnk-voice-mic");
            Thread downlink=new Thread(() -> {
                short[] samples=new short[FRAME/2];
                try {
                    while(running) {
                        byte[] pcm=session.receive();
                        for(int i=0;i<samples.length;i++)
                            samples[i]=(short)((pcm[2*i]&255)|((pcm[2*i+1]&255)<<8));
                        int offset=0;
                        while(offset<samples.length && running){
                            int n=destination.write(samples,offset,samples.length-offset,
                                    AudioTrack.WRITE_BLOCKING);
                            if(n<=0)throw new IllegalStateException("Speaker write "+n);
                            offset+=n;
                        }
                    }
                } catch(Exception error) {
                    if(running)state="Companion speaker stream stopped: "+safe(error);
                    shutdown();
                }
            },"PhnLnk-voice-speaker");
            uplink.setDaemon(true);downlink.setDaemon(true);
            uplink.start();downlink.start();
            while(running && uplink.isAlive() && downlink.isAlive()) {
                setRoute();
                Thread.sleep(500);
            }
            shutdown();
            uplink.join(900);downlink.join(900);
        } catch(Exception error){
            String message=safe(error);
            // If Android / the local helper closed before delivering its
            // error frame, fetch the host's most recent stage through the
            // existing authenticated control API. Never expose tokens.
            if(message.contains("VOICE_MEDIA_CLOSED") ||
                    message.contains("VOICE_HANDSHAKE_CLOSED") ||
                    message.contains("EOFException") ||
                    message.contains("Connection reset")) {
                try {
                    JSONObject hostStatus=RemoteClient.status(this);
                    String hostStage=hostStatus.optString("voiceStage","");
                    String hostError=hostStatus.optString("voiceLastError","");
                    if(!hostError.isEmpty() && !hostError.equals("No host media error reported"))
                        message += " | Main phone ["+hostStage+"]: "+hostError;
                }catch(Exception ignored) { }
            }
            state="Voice failed at "+phase+": "+message;
            Log.w(TAG,"Companion voice failed at "+phase,error);
        } finally {
            shutdown();
            if(aec!=null)aec.release();
            if(suppressor!=null)suppressor.release();
            if(mic!=null){try{mic.stop();}catch(Exception ignored){}mic.release();}
            if(speakerTrack!=null){try{speakerTrack.stop();}catch(Exception ignored){}speakerTrack.release();}
            if(audioManager!=null) {
                try{audioManager.clearCommunicationDevice();}catch(Exception ignored){}
                try{audioManager.setMode(oldMode);}catch(Exception ignored){}
            }
            active=false;
            stopSelf();
        }
    }

    private void setRoute() {
        AudioManager audio=audioManager;
        if(audio==null)return;
        int selected=speaker?AudioDeviceInfo.TYPE_BUILTIN_SPEAKER:
                AudioDeviceInfo.TYPE_BUILTIN_EARPIECE;
        for(AudioDeviceInfo device:audio.getAvailableCommunicationDevices()){
            if(device.getType()==selected){
                try{audio.setCommunicationDevice(device);}catch(Exception ignored){}
                break;
            }
        }
    }

    private static String banner(byte[] frame) throws Exception {
        int size=(frame[0]&255)|((frame[1]&255)<<8);
        if(size<1 || size>frame.length-2)
            throw new IllegalStateException("Invalid encrypted voice acknowledgement");
        return new String(frame,2,size,java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String safe(Exception error) {
        String msg=error.getMessage();
        if(msg==null)msg=error.getClass().getSimpleName();
        return msg.length()>170?msg.substring(0,170):msg;
    }

    private void shutdown() {
        running=false;
        active=false;
        Socket socket=activeSocket;
        activeSocket=null;
        if(socket!=null)try{socket.close();}catch(Exception ignored){}
    }

    @Override public void onDestroy() {
        shutdown();
        worker.shutdownNow();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) {return null;}
}
