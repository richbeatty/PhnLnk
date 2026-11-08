package com.richbeatty.phnlnk;

import android.content.Context;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

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

    private static final class FragmentedSocket extends Socket {
        private final Socket socket;
        FragmentedSocket(Socket socket) { this.socket = socket; }
        @Override public void setSoTimeout(int timeout) throws java.net.SocketException { socket.setSoTimeout(timeout); }
        @Override public InputStream getInputStream() throws IOException {
            return new FilterInputStream(socket.getInputStream()) {
                @Override public int read(byte[] b, int off, int len) throws IOException {
                    return super.read(b, off, Math.min(len, 3));
                }
            };
        }
        @Override public OutputStream getOutputStream() throws IOException {
            return new FilterOutputStream(socket.getOutputStream()) {
                @Override public void write(byte[] b, int off, int len) throws IOException {
                    for (int i = 0; i < len; i += 3) out.write(b, off+i, Math.min(3, len-i));
                }
            };
        }
    }

    private static void fragmentedDuplex() throws Exception {
        exchange("fragmented handshake and simultaneous duplex media", socket -> {
            VoiceSecureTransport.Session host = VoiceSecureTransport.onHost(CONTEXT, new FragmentedSocket(socket));
            simultaneous(host, 7, 31);
        }, socket -> {
            VoiceSecureTransport.Session client = VoiceSecureTransport.onClient(CONTEXT, new FragmentedSocket(socket));
            simultaneous(client, 31, 7);
        });
    }

    private static void simultaneous(VoiceSecureTransport.Session wire, int sending, int receiving)
            throws Exception {
        ExecutorService sender = Executors.newSingleThreadExecutor();
        Future<?> sent = sender.submit(() -> {
            try { for (int i = 0; i < 30; i++) wire.send(pcm(sending+i)); }
            catch (Exception error) { throw new RuntimeException(error); }
        });
        try {
            for (int i = 0; i < 30; i++)
                expect(Arrays.equals(wire.receive(), pcm(receiving+i)), "Duplex ordering or content corrupted");
            sent.get(5, TimeUnit.SECONDS);
        } finally { sender.shutdownNow(); }
    }

    private static void concurrentSenders() throws Exception {
        exchange("concurrent sends allocate unique ordered GCM counters", socket -> {
            VoiceSecureTransport.Session host = VoiceSecureTransport.onHost(CONTEXT, socket);
            Set<Integer> seen = new HashSet<>();
            for (int i = 0; i < 240; i++) {
                int id = java.nio.ByteBuffer.wrap(host.receive()).getInt();
                expect(id >= 0 && id < 240 && seen.add(id), "Concurrent frame lost, duplicated or corrupted");
            }
            host.send(pcm(88));
        }, socket -> {
            VoiceSecureTransport.Session client = VoiceSecureTransport.onClient(CONTEXT, socket);
            ExecutorService writers = Executors.newFixedThreadPool(6);
            CountDownLatch start = new CountDownLatch(1);
            ArrayList<Future<?>> tasks = new ArrayList<>();
            try {
                for (int w = 0; w < 6; w++) {
                    final int worker = w;
                    tasks.add(writers.submit(() -> {
                        try {
                            start.await();
                            for (int i = 0; i < 40; i++) {
                                byte[] frame = pcm(worker);
                                java.nio.ByteBuffer.wrap(frame).putInt(worker*40+i);
                                client.send(frame);
                            }
                        } catch (Exception error) { throw new RuntimeException(error); }
                    }));
                }
                start.countDown();
                for (Future<?> task : tasks) task.get(5, TimeUnit.SECONDS);
                expect(Arrays.equals(client.receive(), pcm(88)), "Missing concurrent send acknowledgement");
            } finally { writers.shutdownNow(); }
        });
    }

    private static void truncatedMedia() throws Exception {
        for (int bytes : new int[]{1, 8, 9, 10, 200, 665}) {
            exchange("truncated media at byte " + bytes, socket -> {
                VoiceSecureTransport.onHost(CONTEXT, socket);
                java.io.ByteArrayOutputStream packet = new java.io.ByteArrayOutputStream();
                DataOutputStream frame = new DataOutputStream(packet);
                frame.writeLong(0); frame.writeShort(656); frame.write(new byte[656]);
                socket.getOutputStream().write(packet.toByteArray(), 0, bytes);
                socket.shutdownOutput();
            }, socket -> {
                VoiceSecureTransport.Session client = VoiceSecureTransport.onClient(CONTEXT, socket);
                expectException("VOICE_MEDIA_CLOSED", client::receive);
            });
        }
    }

    private static void invalidLengths() throws Exception {
        for (int length : new int[]{0, 655, 657, 65535}) {
            exchange("invalid encrypted length " + length + " rejected before allocation", socket -> {
                VoiceSecureTransport.onHost(CONTEXT, socket);
                DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                output.writeLong(0); output.writeShort(length); output.flush();
            }, socket -> {
                VoiceSecureTransport.Session client = VoiceSecureTransport.onClient(CONTEXT, socket);
                expectException("sequence or size invalid", client::receive);
            });
        }
    }

    private static void mediaTimeout() throws Exception {
        exchange("media read timeout is named and bounded", socket -> {
            VoiceSecureTransport.onHost(CONTEXT, socket);
            expect(socket.getInputStream().read() == -1, "Expected client close after timeout");
        }, socket -> {
            VoiceSecureTransport.Session client = VoiceSecureTransport.onClient(CONTEXT, socket);
            socket.setSoTimeout(80);
            expectException("VOICE_MEDIA_TIMEOUT", client::receive);
            socket.shutdownOutput();
        });
    }

    private static void hostHandshakeEof() throws Exception {
        exchange("host names truncated companion proof", socket ->
                expectException("VOICE_HANDSHAKE_CLOSED at WAIT_COMPANION_PROOF", () ->
                        VoiceSecureTransport.onHost(CONTEXT, socket)), socket -> {
            byte[] greeting = new byte[36];
            new DataInputStream(socket.getInputStream()).readFully(greeting);
            socket.getOutputStream().write(new byte[17]); socket.shutdownOutput();
        });
    }

    private static void forgedServerProof() throws Exception {
        exchange("forged host authorization is rejected", socket -> {
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            output.writeInt(0x50484E31); output.write(new byte[32]); output.flush();
            new DataInputStream(socket.getInputStream()).readFully(new byte[48]);
            output.write(new byte[32]); output.flush();
        }, socket -> expectException("PAIRING_REJECTED", () -> VoiceSecureTransport.onClient(CONTEXT, socket)));
    }

    public static void main(String[] ignored)throws Exception{
        roundTrip();
        handshakeEofAtGreeting();
        handshakeEofAfterClientProof();
        mismatchedToken();
        mediaEof();
        rejectTamper();
        rejectSequenceReplay();
        fragmentedDuplex();
        concurrentSenders();
        truncatedMedia();
        invalidLengths();
        mediaTimeout();
        hostHandshakeEof();
        forgedServerProof();
        expect(passed==22,"Not all protocol tests ran: " + passed);
        System.out.println("All "+passed+" voice transport tests passed.");
    }
}
