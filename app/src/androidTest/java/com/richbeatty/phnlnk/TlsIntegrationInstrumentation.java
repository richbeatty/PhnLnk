package com.richbeatty.phnlnk;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.math.BigInteger;
import java.net.InetAddress;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Date;
import java.util.concurrent.*;
import javax.net.ssl.*;
import javax.security.auth.x500.X500Principal;

/** Destructive fixtures ONLY in a fresh disposable emulator/debug installation. */
public final class TlsIntegrationInstrumentation extends Instrumentation {
    private static final String OLD="phnlnk_control_tls_v1", NEW="phnlnk_control_tls_v2";
    private boolean isolated;
    private int passed;
    @Override public void onCreate(Bundle args){
        super.onCreate(args);
        isolated=args!=null && "1".equals(args.getString("isolated_fixture"));
        start();
    }
    private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private void pass(String name){
        passed++;
        Bundle status=new Bundle();status.putString("stream","PASS "+name+"\n");sendStatus(0,status);
    }
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            check(isolated,"Refusing to modify keys without isolated_fixture=1");
            Context context=getTargetContext();
            check((context.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0,
                    "Requires disposable debug installation");
            check("ranchu".equals(android.os.Build.HARDWARE) || "goldfish".equals(android.os.Build.HARDWARE),
                    "Refusing destructive fixtures outside an emulator");
            run(context);
            result.putString("stream","PHNLNK_ANDROID_TLS_PASS cases="+passed+"\n");
            finish(Activity.RESULT_OK,result);
        }catch(Throwable error){
            result.putString("stream","PHNLNK_ANDROID_TLS_FAIL: "+error.getClass().getSimpleName()+": "+error.getMessage()+"\n");
            finish(Activity.RESULT_CANCELED,result);
        }
    }
    private void run(Context context)throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(OLD))store.deleteEntry(OLD);
        if(store.containsAlias(NEW))store.deleteEntry(NEW);
        KeyPairGenerator generator=KeyPairGenerator.getInstance("EC","AndroidKeyStore");
        generator.initialize(new KeyGenParameterSpec.Builder(OLD,KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setCertificateSubject(new X500Principal("CN=PhnLnk disposable legacy fixture"))
                .setCertificateSerialNumber(BigInteger.ONE)
                .setCertificateNotBefore(new Date(System.currentTimeMillis()-86400000L))
                .setCertificateNotAfter(new Date(System.currentTimeMillis()+86400000L)).build());
        generator.generateKeyPair();
        String oldPin=ControlTls.fingerprint((X509Certificate)store.getCertificate(OLD));
        boolean denied=false;
        try{Signature raw=Signature.getInstance("NONEwithECDSA");raw.initSign((java.security.PrivateKey)store.getKey(OLD,null));raw.update(new byte[32]);raw.sign();}
        catch(Exception expected){denied=true;}
        check(denied,"Legacy key unexpectedly authorizes raw EC signing");
        pass("AndroidKeyStore legacy key rejects NONEwithECDSA");
        check(HostTlsIdentity.needsRepair(),"Legacy authorization not detected");
        check(oldPin.equals(HostTlsIdentity.fingerprint()),"Identity silently rotated");
        check(!store.containsAlias(NEW),"New identity created without confirmation");
        pass("legacy fingerprint retained until explicit repair");

        ExecutorService worker=Executors.newSingleThreadExecutor();
        SSLContext legacy=ControlTls.serverContext(store,null,OLD);
        try(SSLServerSocket listener=(SSLServerSocket)legacy.getServerSocketFactory().createServerSocket(0,4,InetAddress.getByName("127.0.0.1"))){
            listener.setSoTimeout(6000);
            Future<?> accepted=worker.submit(()->{
                try(SSLSocket socket=(SSLSocket)listener.accept()){
                    socket.setSoTimeout(5000);socket.startHandshake();
                }catch(Exception expected){ }
            });
            boolean closed=false;
            try{ControlTls.discoverUntrustedFingerprint("127.0.0.1",listener.getLocalPort());}
            catch(Exception expected){
                check(expected instanceof SSLException || expected.getCause() instanceof SSLException,
                        "Legacy connection failed before TLS: "+expected.getClass().getSimpleName());
                closed=true;
            }
            check(closed,"Legacy Android TLS handshake unexpectedly succeeded");
            accepted.get(7,TimeUnit.SECONDS);
        }finally{worker.shutdownNow();}
        pass("legacy Android TLS server reproduces pre-certificate closure");

        PairingManager manager=PairingManager.get(context);
        String token=manager.pair(manager.begin());
        context.getSharedPreferences("phnlnk_companion",Context.MODE_PRIVATE).edit()
                .putString("host","https://10.0.2.15:47321").putString("token",token).putString("tls_pin",oldPin).commit();
        context.getSharedPreferences("phnlnk_audio_bootstrap",Context.MODE_PRIVATE).edit().putBoolean("adb_paired",true).commit();
        HostTlsIdentity.repairAfterConfirmation();
        String pin=HostTlsIdentity.fingerprint();
        check(!pin.equals(oldPin) && !HostTlsIdentity.needsRepair(),"Repair did not create an authorized identity");
        check(store.containsAlias(OLD),"Legacy identity was deleted");
        check(manager.authorized("Bearer "+token),"Host pairing token was lost");
        check(token.equals(context.getSharedPreferences("phnlnk_companion",Context.MODE_PRIVATE).getString("token","")),"Companion token lost");
        check(context.getSharedPreferences("phnlnk_audio_bootstrap",Context.MODE_PRIVATE).getBoolean("adb_paired",false),"ADB pairing cleared");
        HostTlsIdentity.repairAfterConfirmation();
        check(pin.equals(HostTlsIdentity.fingerprint()),"Repeated repair rotated identity");
        pass("explicit repair preserves tokens/ADB pairing, retains legacy key and is idempotent");

        HttpBridge bridge=new HttpBridge(context);bridge.start();
        try{
            check(pin.equals(ControlTls.discoverUntrustedFingerprint("127.0.0.1",47321)),"Discovery served wrong alias");
            pass("real Android discovery serves repaired identity while legacy alias exists");
            boolean mismatch=false;
            try{RemoteClient.status(context);}catch(Exception expected){mismatch=true;}
            check(mismatch,"Old companion pin silently trusted new identity");
            RemoteClient.trustSavedHost(context,pin);
            check(RemoteClient.status(context).has("callState"),"Pinned Android HTTP status failed");
            check(token.equals(context.getSharedPreferences("phnlnk_companion",Context.MODE_PRIVATE).getString("token","")),"Trust migration replaced token");
            pass("Android HttpsURLConnection requires explicit new-pin verification and retains token");
            RemoteClient.forget(context);
            RemoteClient.pair(context,"10.0.2.15",manager.begin(),pin);
            check(RemoteClient.isPaired(context) && RemoteClient.isTrusted(context),"Real HTTPS pairing failed");
            check(RemoteClient.status(context).has("callState"),"New pairing not authorized");
            pass("production Android HTTPS pairing and authorized status complete");
        }finally{bridge.close();}
        check(passed==7,"Missing Android integration case");
    }
}
