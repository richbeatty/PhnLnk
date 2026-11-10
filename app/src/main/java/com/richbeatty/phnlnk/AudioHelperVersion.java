package com.richbeatty.phnlnk;

/** Recognize authenticated helper replies without dropping saved ADB identity. */
final class AudioHelperVersion {
    static final String CURRENT = "READY:V4:";
    private AudioHelperVersion() { }
    static boolean isCurrent(String reply) {
        return reply != null && reply.startsWith(CURRENT);
    }
    static boolean isObsolete(String reply) {
        return reply != null && reply.matches("READY:V[123]:.*");
    }
}
