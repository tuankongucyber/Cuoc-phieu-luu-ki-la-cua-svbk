import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * Lớp khởi chạy chính (Entry Point) của trò chơi Vampire Survivors.
 * Cấu hình cửa sổ JFrame, gắn GamePanel và hiển thị lên màn hình.
 */
public class Main {
    public static void main(String[] args) {
        // Đảm bảo khởi tạo giao diện Swing trên Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(() -> {
            JFrame window = new JFrame("Cuộc phiêu lưu kì lạ của SVBK • Danmaku Survivors • Boss TH06");
            window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            window.setResizable(false);

            GamePanel gamePanel = new GamePanel();
            window.add(gamePanel);
            window.addWindowFocusListener(new java.awt.event.WindowAdapter() {
                @Override public void windowLostFocus(java.awt.event.WindowEvent e) { gamePanel.pauseForFocusLoss(); }
            });

            // Tự động căn chỉnh kích thước khung cửa sổ đúng chuẩn kích thước GamePanel (1000x750)
            window.pack();
            window.setLocationRelativeTo(null);
            window.setVisible(true);
        });
    }
}
