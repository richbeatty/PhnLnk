package com.richbeatty.phnlnk;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.util.Log;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Single-session, authenticated AES-GCM voice transport on trusted private LAN.
 * The shell daemon remains loopback-only. Raw PCM is NEVER served from HTTP.
 * The HTTP call-control listener is separate and must not be exposed online.
 */
final class VoiceRelayServer {
    private static final String TAG="PhnLnkVoice";
    private static final AtomicBoolean BUSY=new AtomicBoolean(false);
    private static volatile VoiceRelayServer current;
    private final Context context;
    private final ExecutorService clients=Executors.newCachedThreadPool();
    private volatile boolean closed;
    private volatile boolean streaming;
    private volatile String failure="Not started";
    private ServerSocket listener;
    private Thread loop;
    private volatile Socket activeClient;
    private volatile Socket activeShell;

    VoiceRelayServer(Context c) {context=c.getApplicationContext();}

    static boolean isBusy() {return BUSY.get();}

    static void disconnectActive() {
        VoiceRelayServer server=current;
        if(server!=null) {
            server.streaming=false;
            closeSilently(server.activeClient);
            closeSilently(server.activeShell);
        }
    }

    boolean isStreaming() {return streaming;}
    String failure() {return failure;}

    void start() throws IOException {
        listener=new ServerSocket();
        listener.setReuseAddress(true);
        listener.bind(new InetSocketAddress(VoiceSecureTransport.PORT));
        closed=false;
        current=this;
        loop=new Thread(() -> {
            while(!closed) {
                try {
                    Socket socket=listener.accept();
                    if(!BUSY.compareAndSet(false,true)){
                        socket.close();continue;
                    }
                    clients.execute(() -> {
                        try {serve(socket);}
                        finally {BUSY.set(false);}
                    });
                }catch(Exception error){
                    if(!closed)Log.w(TAG,"Voice connection failed",error);
                }
            }
        },"PhnLnk-voice-accept");
        loop.setDaemon(true);loop.start();
    }

    void close() {
        closed=true;
        closeSilently(activeClient);
        closeSilently(activeShell);
        closeSilently(listener);
        clients.shutdownNow();
        if(loop!=null)loop.interrupt();
        streaming=false;
        if(current==this)current=null;
    }

    private boolean isCallActive() {
        try {
            TelephonyManager phone=(TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
            return phone!=null && phone.getCallState()==TelephonyManager.CALL_STATE_OFFHOOK;
        }catch(Exception e){return false;}
    }

    private static void closeSilently(AutoCloseable closeable){
        if(closeable!=null)try{closeable.close();}catch(Exception ignored){}
    }

    private static byte[] message(String text) {
        byte[] frame=new byte[VoiceSecureTransport.FRAME_BYTES];
        byte[] bytes=("PHNLNK:" + text).getBytes(StandardCharsets.UTF_8);
        int n=Math.min(bytes.length,frame.length-2);
        frame[0]=(byte)(n&255);
        frame[1]=(byte)((n>>>8)&255);
        System.arraycopy(bytes,0,frame,2,n);
        return frame;
    }

    private void serve(Socket remote) {
        activeClient=remote;
        Socket helper=null;
        VoiceSecureTransport.Session session=null;
        try (Socket socket=remote) {
            socket.setTcpNoDelay(true);
            session=VoiceSecureTransport.onHost(context,socket);
            if(!isCallActive()) {
                session.send(message("ERROR:No active cellular call on the main phone"));
                return;
            }
            if(!AudioAccessState.autoReconnect(context)) {
                session.send(message("ERROR:Audio access is switched off on the main phone"));
                return;
            }
            String key=context.getSharedPreferences("phnlnk_audio_bootstrap",Context.MODE_PRIVATE)
                    .getString("session_secret","");
            if(!key.matches("[0-9a-f]{64}")) {
                session.send(message("ERROR:Audio helper needs guided setup on main phone"));
                return;
            }
            helper=new Socket();
            helper.connect(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),47327),1500);
            helper.setSoTimeout(6500);
            helper.setTcpNoDelay(true);
            activeShell=helper;
            DataInputStream shellIn=new DataInputStream(helper.getInputStream());
            DataOutputStream shellOut=new DataOutputStream(helper.getOutputStream());
            shellOut.writeUTF(key);
            shellOut.writeUTF("STREAM:"+AudioAccessState.captureMethod(context)
                    +":"+AudioAccessState.uplinkMethod(context));
            shellOut.flush();
            String opened=shellIn.readUTF();
            if(!opened.startsWith("STREAM_READY:")) {
                session.send(message("ERROR:" + (opened.startsWith("ERROR:")?
                        opened.substring(6):"Host call audio backend refused")));
                return;
            }
            session.send(message("READY:"+opened.substring("STREAM_READY:".length())));
            // The secure channel and shell capture/injection are now set up,
            // but this alone does not prove the remote caller hears audio.
            streaming=true;
            failure="";
            helper.setSoTimeout(14000);
            final Socket closeRemote=socket;
            final Socket closeHelper=helper;
            final VoiceSecureTransport.Session wire=session;
            final DataInputStream fromShell=shellIn;
            final DataOutputStream toShell=shellOut;
            Thread down=new Thread(() -> {
                byte[] frame=new byte[VoiceSecureTransport.FRAME_BYTES];
                try{
                    while(!closed && streaming) {
                        fromShell.readFully(frame);
                        wire.send(frame);
                    }
                }catch(Exception e){failure="Downlink stopped: "+safe(e);}
                finally{streaming=false;closeSilently(closeRemote);closeSilently(closeHelper);}
            },"PhnLnk-downlink");
            Thread up=new Thread(() -> {
                try {
                    while(!closed && streaming){
                        byte[] frame=wire.receive();
                        toShell.write(frame);
                        toShell.flush();
                    }
                }catch(Exception e){failure="Uplink stopped: "+safe(e);}
                finally{streaming=false;closeSilently(closeRemote);closeSilently(closeHelper);}
            },"PhnLnk-uplink");
            down.setDaemon(true);up.setDaemon(true);
            down.start();up.start();
            while(streaming && !closed && isCallActive()
                    && down.isAlive() && up.isAlive()) {
                try{Thread.sleep(350);}catch(InterruptedException stop){break;}
            }
            streaming=false;
            closeSilently(socket);closeSilently(helper);
            down.join(800);up.join(800);
        }catch(Exception error){
            failure=safe(error);
            Log.w(TAG,"Voice relay could not start",error);
            if(session!=null && !streaming) {
                try{session.send(message("ERROR:"+failure));}catch(Exception ignored){}
            }
        }finally{
            streaming=false;
            closeSilently(helper);
            activeShell=null;
            activeClient=null;
        }
    }

    private static String safe(Exception error){
        String msg=error.getMessage();
        if(msg==null)msg=error.getClass().getSimpleName();
        return msg.length()>150?msg.substring(0,150):msg;
    }
}
