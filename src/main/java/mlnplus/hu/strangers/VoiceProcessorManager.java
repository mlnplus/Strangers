package mlnplus.hu.strangers;

import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings({"null", "unchecked"})
public class VoiceProcessorManager {

    private final VoicechatApi api;
    private volatile double pitchRatio;
    private volatile int windowMs;
    private final ConcurrentHashMap<UUID, PlayerAudioState> states = new ConcurrentHashMap<>();

    public VoiceProcessorManager(VoicechatApi api, double pitchRatio, int windowMs) {
        this.api = api;
        this.pitchRatio = pitchRatio;
        this.windowMs = windowMs;
    }

    public void updateSettings(double pitchRatio, int windowMs) {
        this.pitchRatio = pitchRatio;
        this.windowMs = windowMs;
        clear();
    }

    public byte[] process(UUID playerId, byte[] opusData) {
        PlayerAudioState state = states.get(playerId);
        if (state == null) {
            state = new PlayerAudioState(api, pitchRatio, windowMs);
            states.put(playerId, state);
        }
        return state.process(opusData);
    }

    public void removePlayer(UUID playerId) {
        PlayerAudioState state = states.remove(playerId);
        if (state != null) {
            state.close();
        }
    }

    public void clear() {
        for (PlayerAudioState state : states.values()) {
            if (state != null) {
                state.close();
            }
        }
        states.clear();
    }

    private static class PlayerAudioState {
        private final OpusDecoder decoder;
        private final OpusEncoder encoder;

        // Circular buffer parameters
        private static final int BUF_SIZE = 16384;
        private final float[] buffer = new float[BUF_SIZE];
        private int writePtr = 0;
        private double phase = 0.0;

        // Config values
        private final double pitchRatio;
        private final int windowSize; // in samples

        public PlayerAudioState(VoicechatApi api, double pitchRatio, int windowMs) {
            this.decoder = api.createDecoder();
            this.encoder = api.createEncoder();
            this.pitchRatio = pitchRatio;
            // 48000 Hz sample rate
            this.windowSize = (int) (windowMs * 48.0);
        }

        public byte[] process(byte[] opusData) {
            if (decoder == null || encoder == null) {
                return opusData;
            }

            try {
                short[] pcm = decoder.decode(opusData);
                if (pcm == null || pcm.length == 0) {
                    return opusData;
                }

                short[] processedPcm = pitchShift(pcm);
                return encoder.encode(processedPcm);
            } catch (Exception e) {
                return opusData;
            }
        }

        private short[] pitchShift(short[] input) {
            short[] output = new short[input.length];
            double relativeSpeed = 1.0 - pitchRatio;
            double windowDiv2 = windowSize / 2.0;

            for (int i = 0; i < input.length; i++) {
                buffer[writePtr] = input[i] / 32768.0f;

                phase += relativeSpeed;
                if (phase >= windowSize) {
                    phase -= windowSize;
                } else if (phase < 0.0) {
                    phase += windowSize;
                }

                double delay1 = phase;
                double delay2 = phase + windowDiv2;
                if (delay2 >= windowSize) {
                    delay2 -= windowSize;
                }

                double readPtr1 = writePtr - delay1;
                double readPtr2 = writePtr - delay2;

                // Inline interpolate 1
                if (readPtr1 < 0) readPtr1 += BUF_SIZE;
                int idx1_1 = (int) readPtr1;
                if (idx1_1 >= BUF_SIZE) idx1_1 -= BUF_SIZE;
                int idx1_2 = idx1_1 + 1;
                if (idx1_2 >= BUF_SIZE) idx1_2 -= BUF_SIZE;
                float frac1 = (float) (readPtr1 - (int)readPtr1);
                float sample1 = buffer[idx1_1] + (buffer[idx1_2] - buffer[idx1_1]) * frac1;

                // Inline interpolate 2
                if (readPtr2 < 0) readPtr2 += BUF_SIZE;
                int idx2_1 = (int) readPtr2;
                if (idx2_1 >= BUF_SIZE) idx2_1 -= BUF_SIZE;
                int idx2_2 = idx2_1 + 1;
                if (idx2_2 >= BUF_SIZE) idx2_2 -= BUF_SIZE;
                float frac2 = (float) (readPtr2 - (int)readPtr2);
                float sample2 = buffer[idx2_1] + (buffer[idx2_2] - buffer[idx2_1]) * frac2;

                double w1 = (phase < windowDiv2) ? (phase / windowDiv2) : (2.0 - (phase / windowDiv2));
                float fW1 = (float) w1;
                float fW2 = 1.0f - fW1;

                float outVal = sample1 * fW1 + sample2 * fW2;
                float scaledOut = outVal * 1.1f;
                if (scaledOut > 1.0f) scaledOut = 1.0f;
                else if (scaledOut < -1.0f) scaledOut = -1.0f;

                output[i] = (short) (scaledOut * 32767.0f);

                writePtr++;
                if (writePtr >= BUF_SIZE) writePtr = 0;
            }

            return output;
        }

        public void close() {
            if (decoder != null) {
                decoder.close();
            }
            if (encoder != null) {
                encoder.close();
            }
        }
    }
}
