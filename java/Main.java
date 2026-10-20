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
            JFrame window = new JFrame("Cuộc phiêu lưu kì lạ của SVBK");
            window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            window.setResizable(false);

            GamePanel gamePanel = new GamePanel();
            window.add(gamePanel);

            // Tự động căn chỉnh kích thước khung cửa sổ đúng chuẩn kích thước GamePanel (1000x750)
            window.pack();
            window.setLocationRelativeTo(null);
            window.setVisible(true);
        });
    }
}
