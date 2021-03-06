package de.maxhenkel.voicechat.voice.client;

import de.maxhenkel.voicechat.Main;
import de.maxhenkel.voicechat.voice.common.MicPacket;
import de.maxhenkel.voicechat.voice.common.NetworkMessage;
import de.maxhenkel.voicechat.voice.common.Utils;

import javax.sound.sampled.*;
import java.io.IOException;
import java.util.Arrays;

public class MicThread extends Thread {

    private Client client;
    private TargetDataLine mic;
    private boolean running;
    private boolean microphoneLocked;

    public MicThread(Client client) throws LineUnavailableException {
        this.client = client;
        this.running = true;
        setDaemon(true);
        setName("MicrophoneThread");
        AudioFormat af = AudioChannelConfig.getMonoFormat();
        mic = DataLines.getMicrophone();
        mic.open(af);
    }

    @Override
    public void run() {
        while (running) {
            // Checking here for timeouts, because we don't have any other looping thread
            client.checkTimeout();
            if (microphoneLocked) {
                Utils.sleep(10);
            } else {
                MicrophoneActivationType type = Main.CLIENT_CONFIG.microphoneActivationType.get();
                if (type.equals(MicrophoneActivationType.PTT)) {
                    ptt();
                } else if (type.equals(MicrophoneActivationType.VOICE)) {
                    voice();
                }
            }
        }
    }

    private boolean activating;
    private int deactivationDelay;
    private byte[] lastBuff;

    private void voice() {
        wasPTT = false;

        if (client.isMuted()) {
            activating = false;
            if (mic.isActive()) {
                mic.stop();
                mic.flush();
            }

            return;
        }

        int dataLength = AudioChannelConfig.getReadSize(mic);

        mic.start();

        if (mic.available() < dataLength) {
            Utils.sleep(1);
            return;
        }
        byte[] buff = new byte[dataLength];
        while (mic.available() >= dataLength) {
            mic.read(buff, 0, buff.length);
        }
        Utils.adjustVolumeMono(buff, Main.CLIENT_CONFIG.microphoneAmplification.get().floatValue());

        int offset = Utils.getActivationOffset(buff, Main.CLIENT_CONFIG.voiceActivationThreshold.get());
        if (activating) {
            if (offset < 0) {
                if (deactivationDelay >= 2) {
                    activating = false;
                    deactivationDelay = 0;
                } else {
                    sendAudioPacket(buff);
                    deactivationDelay++;
                }
            } else {
                sendAudioPacket(buff);
            }
        } else {
            if (offset > 0) {
                if (lastBuff != null) {
                    int lastPacketOffset = buff.length - offset;
                    sendAudioPacket(Arrays.copyOfRange(lastBuff, lastPacketOffset, lastBuff.length));
                }
                sendAudioPacket(buff);
                activating = true;
            }
        }
        lastBuff = buff;
    }

    private boolean wasPTT;

    private void ptt() {
        activating = false;
        int dataLength = AudioChannelConfig.getReadSize(mic);
        if (!Main.KEY_PTT.isKeyDown()) {
            if (wasPTT) {
                mic.stop();
                mic.flush();
                wasPTT = false;
            }
            Utils.sleep(10);
            return;
        } else {
            wasPTT = true;
        }

        mic.start();

        if (mic.available() < dataLength) {
            Utils.sleep(1);
            return;
        }
        byte[] buff = new byte[dataLength];
        while (mic.available() >= dataLength) {
            mic.read(buff, 0, buff.length);
        }
        Utils.adjustVolumeMono(buff, Main.CLIENT_CONFIG.microphoneAmplification.get().floatValue());
        sendAudioPacket(buff);
    }

    private void sendAudioPacket(byte[] data) {
        int dataLength = AudioChannelConfig.getDataLength();
        int packetAmount = (int) Math.ceil((double) data.length / (double) dataLength);
        int bytesPerPacket = packetAmount == 0 ? 0 : data.length / packetAmount;
        if (bytesPerPacket % 2 == 1) {
            bytesPerPacket--;
        }
        int rest = data.length - bytesPerPacket * packetAmount;
        for (int i = 0; i < packetAmount; i++) {
            try {
                client.sendToServer(new NetworkMessage(new MicPacket(Arrays.copyOfRange(data, i * bytesPerPacket, (i + 1) * bytesPerPacket + ((i >= packetAmount - 1) ? rest : 0))), client.getSecret()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public TargetDataLine getMic() {
        return mic;
    }

    public boolean isTalking() {
        return !microphoneLocked && (activating || wasPTT);
    }

    public void setMicrophoneLocked(boolean microphoneLocked) {
        this.microphoneLocked = microphoneLocked;
        activating = false;
        wasPTT = false;
        deactivationDelay = 0;
        lastBuff = null;
    }

    public void close() {
        running = false;
        mic.stop();
        mic.flush();
        mic.close();
    }
}
