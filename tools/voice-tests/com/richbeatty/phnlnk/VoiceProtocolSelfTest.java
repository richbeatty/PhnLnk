package com.richbeatty.phnlnk;

import android.content.Context;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Real production VoiceSecureTransport code is compiled against only minimal
 * Android app stubs and exercised over actual loopback TCP sockets.
 * No microphone, recordings, cellular network, or Android device is needed.
 */
public final class VoiceProtocolSelfTest {
    private static final Context CONTEXT=new Context();
    private static int passed;

    private VoiceProtocolSelfTest(){}

    private interface ServerCase {
        void handle(Socket socket)throws Exception;
    }
    private interface ClientCase {
        void handle(Socket socket)throws Exception;
    }

    private static void exchange(String name,ServerCase serverCase,ClientCase clientCase)throws Exception {
        try(ServerSocket listener=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            listener.setSoTimeout(5000);
            ExecutorService executor=Executors.newSingleThreadExecutor();
            Future<Void> remote=executor.submit(new Callable<Void>() {
                @Override public Void call()throws Exception {
                    try(Socket socket=listener.accept()){
                        socket.setSoTimeout(5000);
                        serverCase.handle(socket);
                        return null;
                    }
                }
            });
            try(Socket client=new Socket("127.0.0.1",listener.getLocalPort())){
                client.setSoTimeout(5000);
                clientCase.handle(client);
                remote.get(7,TimeUnit.SECONDS);
                passed++;
                System.out.println("PASS "+name);
            }finally{
                executor.shutdownNow();
            }
        }
    }

    private static void expect(boolean condition,String error){
        if(!condition)throw new AssertionError(error);
    }

    private static void expectException(String phrase,RunnableWithException test)throws Exception{
        try{
            test.run();
        }catch(Exception error){
            String message=String.valueOf(error.getMessage());
            expect(message.contains(phrase),"Expected "+phrase+", got "+message);
            return;
        }
        throw new AssertionError("Expected error "+phrase);
    }

    private interface RunnableWithException {void run()throws Exception;}

    private static byte[] pcm(int seed){
        byte[] data=new byte[VoiceSecureTransport.FRAME_BYTES];
        for(int i=0;i<data.length;i++)data[i]=(byte)(seed+i*17);
        return data;
    }

    private static void roundTrip()throws Exception{
        byte[] fromClient=pcm(13);
        byte[] fromHost=pcm(97);
        exchange("encrypted full duplex roundtrip",socket->{
            VoiceSecureTransport.Session host=VoiceSecureTransport.onHost(CONTEXT,socket);
            byte[] received=host.receive();
            expect(Arrays.equals(received,fromClient),"Host received corrupted audio");
            host.send(fromHost);
        },socket->{
            VoiceSecureTransport.Session client=VoiceSecureTransport.onClient(CONTEXT,socket);
            client.send(fromClient);
            expect(Arrays.equals(client.receive(),fromHost),
                    "Companion received corrupted audio");
        });
    }

    private static void handshakeEofAtGreeting()throws Exception {
        exchange("early EOF names handshake stage",socket->{
            socket.getOutputStream().flush();
            socket.shutdownOutput();
        },socket->expectException("VOICE_HANDSHAKE_CLOSED at WAIT_HOST_GREETING",()->
                VoiceSecureTransport.onClient(CONTEXT,socket)));
    }

    private static void handshakeEofAfterClientProof()throws Exception {
        exchange("EOF after proof names authorization stage",socket->{
            DataOutputStream output=new DataOutputStream(socket.getOutputStream());
            output.writeInt(0x50484E31);
            output.write(new byte[32]);
            output.flush();
            DataInputStream input=new DataInputStream(socket.getInputStream());
            byte[] challenge=new byte[48];
            input.readFully(challenge);
            socket.shutdownOutput();
        },socket->expectException("VOICE_HANDSHAKE_CLOSED at WAIT_HOST_AUTHORIZATION",()->
                VoiceSecureTransport.onClient(CONTEXT,socket)));
    }

    private static void mismatchedToken()throws Exception {
        String original=PairingManager.HOST_TOKEN;
        PairingManager.HOST_TOKEN=java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(new byte[32]);
        try{
            exchange("mismatched stored pairing is refused",socket->
                    expectException("Voice device isn't paired",()->
                            VoiceSecureTransport.onHost(CONTEXT,socket)),
                    socket->expectException("VOICE_HANDSHAKE_CLOSED at WAIT_HOST_AUTHORIZATION",()->
                            VoiceSecureTransport.onClient(CONTEXT,socket)));
        }finally{PairingManager.HOST_TOKEN=original;}
    }

    private static void mediaEof()throws Exception {
        exchange("media EOF is explicit not bare EOFException",socket->{
            VoiceSecureTransport.onHost(CONTEXT,socket);
            socket.shutdownOutput();
        },socket->{
            VoiceSecureTransport.Session client=VoiceSecureTransport.onClient(CONTEXT,socket);
            expectException("VOICE_MEDIA_CLOSED",client::receive);
        });
    }

    private static void rejectTamper()throws Exception{
        exchange("GCM rejects modified encrypted media",socket->{
            VoiceSecureTransport.onHost(CONTEXT,socket);
            DataOutputStream output=new DataOutputStream(socket.getOutputStream());
            output.writeLong(0);
            output.writeShort(VoiceSecureTransport.FRAME_BYTES+16);
            byte[] tampered=new byte[VoiceSecureTransport.FRAME_BYTES+16];
            new SecureRandom().nextBytes(tampered);
            output.write(tampered);
            output.flush();
        },socket->{
            VoiceSecureTransport.Session client=VoiceSecureTransport.onClient(CONTEXT,socket);
            expectException("Tag mismatch",client::receive);
        });
    }

    private static void rejectSequenceReplay()throws Exception {
        exchange("out-of-order and duplicate sequence rejected",socket->{
            VoiceSecureTransport.Session host=VoiceSecureTransport.onHost(CONTEXT,socket);
            host.send(pcm(53));
            DataOutputStream output=new DataOutputStream(socket.getOutputStream());
            output.writeLong(0);
            output.writeShort(VoiceSecureTransport.FRAME_BYTES+16);
            output.write(new byte[VoiceSecureTransport.FRAME_BYTES+16]);
            output.flush();
        },socket->{
            VoiceSecureTransport.Session client=VoiceSecureTransport.onClient(CONTEXT,socket);
            expect(Arrays.equals(client.receive(),pcm(53)),"First frame should decrypt");
            expectException("sequence or size invalid",client::receive);
        });
    }

    public static void main(String[] ignored)throws Exception{
        roundTrip();
        handshakeEofAtGreeting();
        handshakeEofAfterClientProof();
        mismatchedToken();
        mediaEof();
        rejectTamper();
        rejectSequenceReplay();
        expect(passed==7,"Not all protocol tests ran");
        System.out.println("All "+passed+" voice transport tests passed.");
    }
}
