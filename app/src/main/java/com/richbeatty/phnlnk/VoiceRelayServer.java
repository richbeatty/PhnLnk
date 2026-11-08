package com.richbeatty.phnlnk;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.util.Log;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.net.SocketTimeoutException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

/**
 * Single-session, authenticated AES-GCM voice transport on trusted private LAN.
 * The shell daemon remains loopback-only. Raw PCM is NEVER served from HTTP.
 * The pinned HTTPS call-control listener is separate and remains private.
 */
final class VoiceRelayServer {
    private static final String TAG="PhnLnkVoice";
    private static final AtomicBoolean BUSY=new AtomicBoolean(false);
    private static volatile VoiceRelayServer current;
    private static volatile String lastStage="NOT_STARTED";
    private static volatile String lastError="No voice attempt yet";
    private static volatile long lastErrorAt;
    private final Context context;
    private final ExecutorService clients=new ThreadPoolExecutor(0,4,30,TimeUnit.SECONDS,
            new SynchronousQueue<>());
    private final Set<Socket> connections=ConcurrentHashMap.newKeySet();
    private volatile boolean closed;
    private volatile boolean streaming;
    private volatile String failure="Not started";
    private ServerSocket listener;
    private Thread loop;
    private volatile Socket activeClient;
    private volatile Socket activeShell;

    VoiceRelayServer(Context c) {context=c.getApplicationContext();}

    static boolean isBusy() {return BUSY.get();}

    static String lastStage() {return lastStage;}
    static String lastError() {return lastError;}
    static long lastErrorAt() {return lastErrorAt;}

    private static void stage(String value) {lastStage=value;}

    private static void fail(String value) {
        lastError=value == null ? "Unknown voice failure" :
                value.length()>240?value.substring(0,240):value;
        lastErrorAt=System.currentTimeMillis();
    }

    static void disconnectActive() {
        VoiceRelayServer server=current;
        if(server!=null) {
            server.streaming=false;
            // Includes sockets still authenticating, so revocation cannot race
            // a completed key lookup before activeClient is published.
            for(Socket socket:server.connections)closeSilently(socket);
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
        stage("LISTENING");
        loop=new Thread(() -> {
            while(!closed) {
                try {
                    Socket socket=listener.accept();
                    connections.add(socket);
                    try {
                        clients.execute(() -> {
                            try {serve(socket);}
                            finally {connections.remove(socket);}
                        });
                    }catch(RejectedExecutionException full){
                        connections.remove(socket);socket.close();
                    }
                }catch(Exception error){
                    if(!closed)Log.w(TAG,"Voice connection failed",error);
                }
            }
        },"PhnLnk-voice-accept");
        loop.setDaemon(true);loop.start();
    }

    void close() {
        closed=true;
        closeSilently(listener);
        if(loop!=null) {
            loop.interrupt();
            try {loop.join(1000);}catch(InterruptedException stop){Thread.currentThread().interrupt();}
        }
        for(Socket socket:connections)closeSilently(socket);
        closeSilently(activeShell);
        clients.shutdownNow();
        try {clients.awaitTermination(1,TimeUnit.SECONDS);}
        catch(InterruptedException stop){Thread.currentThread().interrupt();}
        streaming=false;
        stage("STOPPED");
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
        Socket helper=null;
        VoiceSecureTransport.Session session=null;
        boolean readySent=false;
        boolean ownsSession=false;
        String phase="AUTH_HANDSHAKE";
        // Keep the socket open through the catch block: try-with-resources used
        // to close it BEFORE we could send an authenticated startup ERROR.
        try {
            Socket socket=remote;
            socket.setTcpNoDelay(true);
            try(SocketDeadline deadline=SocketDeadline.after(socket,9000)) {
                session=VoiceSecureTransport.onHost(context,socket);
            }
            if(!BUSY.compareAndSet(false,true)) {
                session.send(message("ERROR:HOST_BUSY:Another voice session is active"));
                return;
            }
            ownsSession=true;
            activeClient=remote;
            if(closed || remote.isClosed())return;
            phase="CHECK_ACTIVE_CALL";
            stage(phase);
            if(!isCallActive()) {
                fail("NO_CALL: No active cellular call on the main phone.");
                session.send(message("ERROR:NO_CALL:No active cellular call on the main phone"));
                return;
            }
            if(!AudioAccessState.autoReconnect(context)) {
                fail("ACCESS_OFF: The main phone has automatic audio access turned off.");
                session.send(message("ERROR:ACCESS_OFF:Audio access is switched off on the main phone"));
                return;
            }
            String key=context.getSharedPreferences("phnlnk_audio_bootstrap",Context.MODE_PRIVATE)
                    .getString("session_secret","");
            if(!key.matches("[0-9a-f]{64}")) {
                fail("HELPER_UNPAIRED: Local audio helper authorization is missing.");
                session.send(message("ERROR:HELPER_UNPAIRED:Audio helper needs guided setup on main phone"));
                return;
            }
            phase="CONNECT_SHELL_HELPER";
            stage(phase);
            helper=new Socket();
            helper.connect(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),47327),1500);
            helper.setSoTimeout(20000);
            helper.setTcpNoDelay(true);
            activeShell=helper;
            DataInputStream shellIn=new DataInputStream(helper.getInputStream());
            DataOutputStream shellOut=new DataOutputStream(helper.getOutputStream());
            shellOut.writeUTF(key);
            shellOut.writeUTF("STREAM:"+AudioAccessState.captureMethod(context)
                    +":"+AudioAccessState.uplinkMethod(context));
            shellOut.flush();
            phase="AWAIT_AUDIO_BACKEND";
            stage(phase);
            String opened;
            try {
                opened=shellIn.readUTF();
            } catch (EOFException closedEarly) {
                throw new IOException("HELPER_CLOSED: Local shell audio helper closed before reporting a capture or injection backend. Check Android audio attribution and firmware support.",closedEarly);
            } catch (SocketTimeoutException timedOut) {
                throw new IOException("HELPER_TIMEOUT: Local shell audio helper did not finish opening audio backends within 20 seconds.",timedOut);
            }
            if(!opened.startsWith("STREAM_READY:")) {
                String reason=opened.startsWith("ERROR:") ? opened.substring(6) :
                        "BACKEND_REJECTED:Host call audio backend refused";
                fail(reason);
                session.send(message("ERROR:"+reason));
                return;
            }
            session.send(message("READY:"+opened.substring("STREAM_READY:".length())));
            readySent=true;
            phase="STREAMING";
            stage(phase);
            // The secure channel and shell capture/injection are now set up,
            // but this alone does not prove the remote caller hears audio.
            streaming=true;
            failure="";
            lastError="No host media error reported";
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
                }catch(Exception e){failure="DOWNLINK_STOPPED: "+safe(e);fail(failure);}
                finally{streaming=false;closeSilently(closeRemote);closeSilently(closeHelper);}
            },"PhnLnk-downlink");
            Thread up=new Thread(() -> {
                try {
                    while(!closed && streaming){
                        byte[] frame=wire.receive();
                        toShell.write(frame);
                        toShell.flush();
                    }
                }catch(Exception e){failure="UPLINK_STOPPED: "+safe(e);fail(failure);}
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
            String detail=phase+": "+safe(error);
            if(ownsSession || !BUSY.get()) {
                failure=detail;
                fail(detail);
            }
            Log.w(TAG,"Voice relay failed at "+phase,error);
            // A startup failure gets one authenticated error frame. Never
            // append a second control frame once streaming has started.
            if(session!=null && !readySent) {
                try{session.send(message("ERROR:"+detail));}catch(Exception ignored){}
            }
        }finally{
            closeSilently(remote);
            closeSilently(helper);
            if(ownsSession) {
                streaming=false;
                activeShell=null;
                activeClient=null;
                BUSY.set(false);
                stage(closed?"STOPPED":"LISTENING");
            }
        }
    }

    private static String safe(Exception error){
        String msg=error.getMessage();
        if(msg==null)msg=error.getClass().getSimpleName();
        return msg.length()>150?msg.substring(0,150):msg;
    }
}
