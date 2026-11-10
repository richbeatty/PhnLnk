package com.richbeatty.phnlnk;

import java.net.InetAddress;
import java.util.Arrays;

/** Self-ADB discovery must not use another LAN phone's advertised port. */
final class LocalAdbEndpoint {
    private LocalAdbEndpoint() { }
    static boolean isLocal(InetAddress advertised, Iterable<InetAddress> interfaces) {
        if(advertised==null)return false;
        if(advertised.isLoopbackAddress())return true;
        byte[] peer=normalized(advertised.getAddress());
        for(InetAddress own:interfaces)
            if(Arrays.equals(peer,normalized(own.getAddress())))return true;
        return false;
    }
    private static byte[] normalized(byte[] value) {
        if(value.length==16){
            boolean mapped=true;
            for(int i=0;i<10;i++)mapped &= value[i]==0;
            if(mapped && value[10]==(byte)255 && value[11]==(byte)255)
                return Arrays.copyOfRange(value,12,16);
        }
        return value;
    }
}
