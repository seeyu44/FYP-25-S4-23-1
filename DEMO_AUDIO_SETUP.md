# Demo Audio Setup Instructions

## ✅ Implementation Complete

A **"Play Demo Audio"** button has been added to the call screen that will:
1. Play a deepfake audio file through the speaker
2. Your microphone picks it up
3. Both the callee hears it AND your detection system analyzes it
4. Perfect for live product demos!

---

## 📁 Required: Add Audio File

**Create this directory:**
```
app/src/main/assets/
```

**Add your deepfake audio file:**
- **Filename:** `deepfake_sample.wav`
- **Location:** `app/src/main/assets/deepfake_sample.wav`
- **Format Requirements:**
  - WAV format (PCM)
  - 16kHz sample rate (recommended)
  - Mono channel
  - 16-bit depth

---

## 🎤 How to Get Deepfake Audio Sample

### Option 1: Use Your Training Dataset
Copy a known deepfake sample from your training data:
```bash
cp ml/training/deepfake_sample_001.wav app/src/main/assets/deepfake_sample.wav
```

### Option 2: Generate Using TTS
Use a deepfake voice generator:
- ElevenLabs (free tier available)
- Resemble.ai
- Voice cloning tools
- Save as WAV, convert to 16kHz mono if needed

### Option 3: Convert Existing Audio
If you have MP3 or other formats:
```bash
# Using ffmpeg
ffmpeg -i your_audio.mp3 -ar 16000 -ac 1 app/src/main/assets/deepfake_sample.wav
```

---

## 🎭 How to Use in Your Demo

### During Product Presentation:

1. **Start the call** between two phones/devices
2. **Show your real voice first:**
   - Speak normally into mic
   - Show detection status: "REAL" or low score
3. **Press "Play Demo Audio" button:**
   - Orange button appears during active call
   - Button turns RED when playing
   - Audio plays through speaker
   - Mic picks it up automatically
4. **Watch detection trigger:**
   - Score increases
   - "DEEPFAKE DETECTED" alert appears
   - Both phones see the detection
5. **Press "Stop Demo Audio":**
   - Returns to your real voice
   - Detection normalizes

### Demo Script Example:
```
"As you can see, during a normal conversation, the detection shows 
my voice is authentic. Now, let me simulate what happens when a 
scammer tries to use deepfake audio..."

[Press Play Demo Audio button]

"Immediately, our AI model detects the synthetic voice and alerts 
both parties to the fraudulent call."
```

---

## 🔧 Technical Details

### What Happens When You Press the Button:

```
User presses button
    ↓
MediaPlayer plays WAV file through VOICE_CALL stream
    ↓
Phone's microphone picks up the audio
    ↓
Audio flows through two paths:
    1. WebRTC → Remote peer hears it
    2. Detection Service → Analyzes it
    ↓
Detection triggers if score > 0.7
    ↓
UI updates with "DEEPFAKE DETECTED" alert
```

### Why This Works for Demos:
- ✅ Simulates real attack vector (audio injection)
- ✅ 100% reliable for presentations
- ✅ No complex setup required
- ✅ Works on physical devices
- ✅ Visual feedback (button changes color)
- ✅ Callee actually hears the fake audio

---

## ⚠️ Important Notes

1. **Test before your presentation:**
   - Make a test call first
   - Verify audio file plays correctly
   - Check detection triggers as expected

2. **Audio volume:**
   - Set to 80% by default
   - Mic WILL pick it up at this level
   - If detection doesn't trigger, try louder

3. **File size:**
   - Keep audio file < 5MB
   - 10-30 seconds is ideal
   - File will loop automatically

4. **Fallback plan:**
   - If button doesn't appear, check file path
   - Check logcat for "DEMO_AUDIO" tag
   - Verify `deepfake_sample.wav` exists in assets/

---

## 🎯 Demo Checklist

- [ ] Created `app/src/main/assets/` directory
- [ ] Added `deepfake_sample.wav` file (16kHz, mono, WAV)
- [ ] Tested on physical device (not emulator)
- [ ] Verified button appears during active call
- [ ] Confirmed audio plays when button pressed
- [ ] Verified detection triggers correctly
- [ ] Practiced demo script
- [ ] Prepared backup device in case of issues

---

## 🐛 Troubleshooting

**Button doesn't appear:**
- Check that file exists at exact path: `app/src/main/assets/deepfake_sample.wav`
- Rebuild the app after adding the file

**Audio doesn't play:**
- Check logcat filter: `tag:DEMO_AUDIO`
- Look for error: "Make sure deepfake_sample.wav is in app/src/main/assets/"
- Verify WAV format (not MP3 or other formats)

**Detection doesn't trigger:**
- Increase volume in demo
- Check that deepfake detection is running (look for "DEEPFAKE_DETECT" logs)
- Verify the audio file contains actual deepfake voice (not silence)
- Check detection threshold (default 0.7, may need tuning)

**Callee can't hear the audio:**
- Make sure you're not muted
- Check that speaker is enabled
- Verify MediaPlayer audio stream type is VOICE_CALL

---

Good luck with your presentation! 🚀
