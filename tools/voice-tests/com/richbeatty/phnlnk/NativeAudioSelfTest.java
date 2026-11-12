package com.richbeatty.phnlnk;

import com.richbeatty.phnlnk.shell.NativeUplinkPcm;
import java.util.Arrays;
import java.util.Random;

/** Production format conversion and helper-version logic, without Android mocks. */
public final class NativeAudioSelfTest {
    private static int passed;
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void pass(String name) { passed++; System.out.println("PASS " + name); }
    private static double amplitude(short[] stereo, double frequency) {
        double real = 0, imaginary = 0;
        int start = 480, frames = stereo.length / 2;
        for (int i = start; i < frames; i++) {
            double angle = 2 * Math.PI * frequency * i / 48000;
            real += stereo[2*i] * Math.cos(angle);
            imaginary += stereo[2*i] * Math.sin(angle);
        }
        return 2 * Math.hypot(real, imaginary) / (frames - start);
    }
    public static void main(String[] args) throws Exception {
        short[] frame = new NativeUplinkPcm().convert(new short[320]);
        check(frame.length == 1920, "20 ms must be 960 stereo frames, not wire bytes");
        for (short value : frame) check(value == 0, "Silence changed");
        pass("20 ms wire frame becomes 3840 bytes of silent native stereo PCM");

        short[] source = new short[16000];
        Random random = new Random(1489);
        for (int i = 0; i < source.length; i++) source[i] = (short)random.nextInt();
        short[] complete = new NativeUplinkPcm().convert(source);
        check(complete.length == 96000, "One second changed duration");
        NativeUplinkPcm chunked = new NativeUplinkPcm();
        int offset = 0;
        while (offset < source.length) {
            int count = Math.min(1 + random.nextInt(517), source.length - offset);
            check(chunked.convert(new short[0]).length == 0, "Empty block changed duration");
            short[] part = chunked.convert(Arrays.copyOfRange(source, offset, offset + count));
            for (int i = 0; i < part.length; i++)
                check(part[i] == complete[offset*6+i], "Frame boundary changed interpolation");
            offset += count;
        }
        pass("one-second duration and arbitrary chunk boundaries preserve filter state");
        for (int i = 0; i < complete.length; i += 2)
            check(complete[i] == complete[i+1], "Left/right channels differ");
        pass("every output stereo frame duplicates the mono channel");

        for (short level : new short[]{12345, -12345, Short.MAX_VALUE, Short.MIN_VALUE}) {
            short[] dc = new short[320]; Arrays.fill(dc, level);
            short[] converted = new NativeUplinkPcm().convert(dc);
            for (int i = 198; i < converted.length; i++)
                check(converted[i] == level, "DC gain/sign/clipping regression");
        }
        pass("positive and negative DC retain unity gain including full scale");

        short[] impulse = new short[320]; impulse[0] = 10000;
        short[] response = new NativeUplinkPcm().convert(impulse);
        int peak = 0;
        for (int i = 0; i < response.length/2; i++)
            if (Math.abs((int)response[2*i]) > Math.abs((int)response[2*peak])) peak = i;
        check(peak == 48, "Filter group delay must be 48 native frames / 1 ms");
        for (int i = 194; i < response.length; i++) check(response[i] == 0, "Impulse did not settle");
        pass("impulse has bounded support and measured 1 ms group delay");

        short[] sine = new short[16000];
        for (int i = 0; i < sine.length; i++)
            sine[i] = (short)Math.round(10000 * Math.sin(2*Math.PI*1000*i/16000));
        short[] nativeSine = new NativeUplinkPcm().convert(sine);
        double fundamental = amplitude(nativeSine, 1000);
        check(fundamental > 9900 && fundamental < 10100, "Speech-band amplitude changed");
        check(amplitude(nativeSine, 15000) < fundamental/100,
                "Lower upsampling image exceeds -40 dB");
        check(amplitude(nativeSine, 17000) < fundamental/100,
                "Upper upsampling image exceeds -40 dB");
        pass("1 kHz tone retains amplitude and 15/17 kHz images stay below -40 dB");

        short[] step = new short[320]; Arrays.fill(step, Short.MIN_VALUE);
        Arrays.fill(step, 160, step.length, Short.MAX_VALUE);
        short[] transition = new NativeUplinkPcm().convert(step);
        for (int i = (160+33)*6; i < transition.length; i++)
            check(transition[i] == Short.MAX_VALUE, "Full-scale step wrapped or failed to settle");
        short[] fresh = new NativeUplinkPcm().convert(new short[320]);
        for (short sample : fresh) check(sample == 0, "Filter state leaked between sessions");
        pass("full-scale transition settles without wrapping and sessions isolate filter state");

        check(AudioHelperVersion.isCurrent("READY:V4:shell UID 2000:idle"), "V4 not current");
        for (String old : new String[]{"READY:V1:idle", "READY:V2:idle", "READY:V3:idle"}) {
            check(AudioHelperVersion.isObsolete(old), "Older helper will retain port");
            check(!AudioHelperVersion.isCurrent(old), "Old helper accepted as current");
        }
        for (String invalid : new String[]{null, "", "ERROR:denied", "READY:V5:idle", "READY:V4:idle"})
            check(!AudioHelperVersion.isObsolete(invalid), "Unexpected helper stopped");
        pass("V1/V2/V3 replacement and V4 reuse preserve upgrade version boundaries");
        java.net.InetAddress own=java.net.InetAddress.getByName("192.168.5.12");
        java.util.List<java.net.InetAddress> addresses=java.util.List.of(own);
        check(LocalAdbEndpoint.isLocal(own,addresses),"Own IPv4 rejected");
        check(!LocalAdbEndpoint.isLocal(java.net.InetAddress.getByName("192.168.5.13"),addresses),"Another phone's ADB port accepted");
        check(!LocalAdbEndpoint.isLocal(null,addresses),"Unresolved peer accepted");
        check(LocalAdbEndpoint.isLocal(java.net.InetAddress.getByName("127.0.0.1"),java.util.List.of()),"Loopback rejected");
        pass("self-ADB accepts local/loopback addresses and rejects foreign LAN services");
        byte[] v6=java.net.InetAddress.getByName("fe80::1234").getAddress();
        check(LocalAdbEndpoint.isLocal(java.net.Inet6Address.getByAddress(null,v6,7),
                java.util.List.of(java.net.Inet6Address.getByAddress(null,v6,3))),"IPv6 scope mismatch rejected own host");
        byte[] mapped=new byte[16];mapped[10]=(byte)255;mapped[11]=(byte)255;
        System.arraycopy(own.getAddress(),0,mapped,12,4);
        check(LocalAdbEndpoint.isLocal(java.net.Inet6Address.getByAddress(null,mapped,0),addresses),"Mapped IPv4 rejected");
        pass("self-ADB handles scoped IPv6 and IPv4-mapped advertisements");
        check(passed == 10, "Missing native audio/discovery tests");
        System.out.println("All " + passed + " native audio tests passed.");
    }
}
