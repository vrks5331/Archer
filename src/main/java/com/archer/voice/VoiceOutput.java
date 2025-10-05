package com.archer.voice;

import com.sun.speech.freetts.Voice;
import com.sun.speech.freetts.VoiceManager;

public class VoiceOutput {
     private Voice voice;

     public VoiceOutput() {
        System.setProperty("freetts.voices",
                "com.sun.speech.freetts.en.us.cmu_us_kal.KevinVoiceDirectory");

        VoiceManager vm = VoiceManager.getInstance();
        voice = vm.getVoice("kevin16"); // male voice
        if (voice != null) voice.allocate();
     }

    public void speak(String text) {
        if (voice != null) voice.speak(text);
    }

    public void shutdown() {
        if (voice != null) voice.deallocate();
    }
    
}
