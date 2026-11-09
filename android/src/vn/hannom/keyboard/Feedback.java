package vn.hannom.keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.HapticFeedbackConstants;
import android.view.View;

/** Key-press vibration and click sound, driven by the settings. */
final class Feedback {
    static final int KIND_NORMAL = 0, KIND_DELETE = 1, KIND_SPACE = 2, KIND_RETURN = 3;

    private final AudioManager audio;
    private final Vibrator vibrator;
    private boolean vibrate, sound;
    private int strength;
    private float volume;

    Feedback(Context c) {
        audio = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        vibrator = (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
    }

    void reload(SharedPreferences p) {
        vibrate = p.getBoolean(Prefs.VIBRATE, true);
        strength = p.getInt(Prefs.VIBRATE_STRENGTH, 1);
        sound = p.getBoolean(Prefs.SOUND, false);
        volume = p.getInt(Prefs.SOUND_VOLUME, 50) / 100f;
    }

    void key(View v, int kind) {
        if (vibrate) vibrate(v);
        if (sound && audio != null && volume > 0f) {
            int fx;
            switch (kind) {
                case KIND_DELETE: fx = AudioManager.FX_KEYPRESS_DELETE; break;
                case KIND_SPACE: fx = AudioManager.FX_KEYPRESS_SPACEBAR; break;
                case KIND_RETURN: fx = AudioManager.FX_KEYPRESS_RETURN; break;
                default: fx = AudioManager.FX_KEYPRESS_STANDARD; break;
            }
            // volume is a 0..1 factor; sqrt makes the slider feel more even
            audio.playSoundEffect(fx, (float) Math.sqrt(volume));
        }
    }

    private void vibrate(View v) {
        boolean canVibrate = vibrator != null && vibrator.hasVibrator();
        if (canVibrate && Build.VERSION.SDK_INT >= 29) {
            int effect = strength == 0 ? VibrationEffect.EFFECT_TICK
                    : strength == 1 ? VibrationEffect.EFFECT_CLICK : VibrationEffect.EFFECT_HEAVY_CLICK;
            vibrator.vibrate(VibrationEffect.createPredefined(effect));
        } else if (canVibrate && Build.VERSION.SDK_INT >= 26) {
            long ms = strength == 0 ? 8 : strength == 1 ? 15 : 28;
            int amp = vibrator.hasAmplitudeControl() ? (strength == 0 ? 70 : strength == 1 ? 150 : 255)
                    : VibrationEffect.DEFAULT_AMPLITUDE;
            vibrator.vibrate(VibrationEffect.createOneShot(ms, amp));
        } else if (canVibrate && strength > 0) {
            vibrator.vibrate(strength == 1 ? 15 : 28);
        } else if (v != null) {
            v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
        }
    }
}
