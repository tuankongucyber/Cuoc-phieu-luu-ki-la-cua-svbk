import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Lớp quản lý âm thanh (Sound FX).
 * Thiết kế an toàn: Hỗ trợ nạp các file .wav trong thư mục sound/ hoặc thư mục gốc.
 * Nếu chưa có file âm thanh, trò chơi vẫn chạy bình thường mà không bị crash.
 */
public class Sound {
    private Map<String, Clip> soundClips = new HashMap<>();
    private boolean soundEnabled = true;

    public Sound() {
        // Tải các hiệu ứng âm thanh nếu tồn tại file
        loadSound("shoot", "sounds/shoot.wav");
        loadSound("hit", "sounds/hit.wav");
        loadSound("levelup", "sounds/levelup.wav");
        loadSound("gameover", "sounds/gameover.wav");
        loadSound("gem", "sounds/gem.wav");
    }

    /**
     * Nạp file âm thanh an toàn bằng javax.sound.sampled
     */
    private void loadSound(String key, String filePath) {
        try {
            File file = new File(filePath);
            if (!file.exists()) {
                // Thử tìm ở thư mục hiện tại
                file = new File(file.getName());
            }

            if (file.exists()) {
                AudioInputStream ais = AudioSystem.getAudioInputStream(file);
                Clip clip = AudioSystem.getClip();
                clip.open(ais);
                soundClips.put(key, clip);
            }
        } catch (Exception e) {
            // Không ngắt chương trình nếu nạp âm thanh thất bại
            System.out.println("Ghi chú âm thanh: Chưa tìm thấy file " + filePath + " (Game vẫn chạy bình thường không có âm thanh)");
        }
    }

    /**
     * Phát âm thanh theo tên
     */
    public void play(String key) {
        if (!soundEnabled) return;

        Clip clip = soundClips.get(key);
        if (clip != null) {
            try {
                if (clip.isRunning()) {
                    clip.stop();
                }
                clip.setFramePosition(0);
                clip.start();
            } catch (Exception ignored) {
            }
        }
    }

    public void playShoot() {
        play("shoot");
    }

    public void playHit() {
        play("hit");
    }

    public void playLevelUp() {
        play("levelup");
    }

    public void playGameOver() {
        play("gameover");
    }

    public void playGem() {
        play("gem");
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public void setSoundEnabled(boolean soundEnabled) {
        this.soundEnabled = soundEnabled;
    }
}
