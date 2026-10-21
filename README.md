# Cuộc phiêu lưu kì lạ của SVBK

Game sinh tồn 2D kết hợp xây build kiểu Vampire Survivors và né đạn kiểu Touhou. Bản gameplay đầu tiên dùng Java/Swing và hình học đơn giản; chưa sản xuất âm thanh hoặc asset.

## Chạy game

Cần **JDK 17 trở lên**, với `java` và `javac` có trong PATH. Trên Windows, mở `java/run.bat`, hoặc chạy từ thư mục dự án:

~~~powershell
.\java\run.bat
~~~

Script tự chuyển đến đúng thư mục và biên dịch UTF-8 vào thư mục tạm. **Toàn bộ mã chạy game là Java**, chỉ dùng thư viện chuẩn JDK. Không cần Maven/Gradle, Rust, JNI hoặc chương trình native. Các trận TH06 đọc dữ liệu script và metadata trong cache cục bộ.

Màn hình Start có **Bắt đầu**, **Cài đặt**, lựa chọn độ khó và seed để tái hiện run. Cài đặt âm thanh vẫn có bật/tắt và âm lượng; bản này chưa kèm file âm thanh. Độ khó khóa trong ván, còn âm thanh có thể chỉnh từ menu tạm dừng. Cài đặt giữ trong phiên mở ứng dụng.

**Test Boss** trên Start cho chọn 14 trận riêng của Rumia, Cirno, Meiling, Patchouli, Sakuya, Remilia, Flandre, Daiyousei và Koakuma, gồm các biến thể giữa màn/Extra. Mỗi trận chạy chuỗi script riêng, không ghép đòn từ Boss khác. Có sẵn 4 vũ khí cấp 4, nội tại, 100 HP và 3 Bomb; không bị ngắt để lên cấp. Enter thử lại đúng Boss, seed và nhánh trận đấu. Bốn nhánh Reimu A/B, Marisa A/B quyết định nhánh đòn của Patchouli; nhân vật người chơi hiện tại vẫn giữ nguyên.

HUD chỉ hiện tên Boss và HP, không hiện tên moveset/Spell Card. Nhân vật có thanh HP trên đầu.

### Bộ chạy đòn TH06

Giao diện, người chơi, vũ khí, sát thương và bộ chạy script đều là Java/Swing. `Th06Vm` đọc và thực thi dữ liệu ECL trực tiếp trong JVM, gồm chuyển pha, RNG, đạn, laser, time-stop và đòn riêng của từng Boss. `Th06Anm` đọc metadata kích thước, lịch animation và các yêu cầu RNG; không tải ảnh. Mỗi tick của `GameWorld` cập nhật bộ mô phỏng đúng một frame; Pause dừng toàn bộ.

Trên máy đang phát triển, dữ liệu đã có trong `%LOCALAPPDATA%/SVBK/TH06Reference/`. Với máy mới: đặt `reference-data.zip` tương thích vào thư mục đó rồi chạy bằng JDK 17+. Java tự đọc archive và chuẩn bị các file `.ecl`/`.anm` cần thiết. Có thể đổi thư mục bằng `-Dsvbk.th06.data=...`. Không cần build thêm bộ chạy hoặc cài toolchain khác. Thiếu dữ liệu thì giao diện báo lỗi, không tự thay bằng bộ đòn trộn cũ.

Dữ liệu gốc không nằm trong Git. Nguồn, phiên bản, thay đổi tích hợp và giới hạn kiểm chứng: [docs/TH06_PORT.md](docs/TH06_PORT.md).

## Điều khiển

| Phím | Tác dụng |
| --- | --- |
| WASD / mũi tên | Di chuyển; vũ khí tự tấn công |
| Giữ Shift | Focus: đi chậm, hiện hitbox nhỏ |
| X | Dash theo hướng đang đi hoặc hướng gần nhất; **không bất tử** |
| Space | Bomb: xóa đạn địch hiện có, sát thương diện rộng, bất tử 1 giây |
| 1 / 2 / 3 hoặc nhấp thẻ | Chọn nâng cấp |
| R | Đổi lựa chọn nâng cấp, tối đa 2 lần/run |
| Esc | Tạm dừng / tiếp tục; trong Cài đặt thì quay lại |
| Enter khi kết thúc | Chơi lại cùng seed |

Chuyển sang cửa sổ khác sẽ tự tạm dừng. Về Start kết thúc ván hiện tại. Thời gian không trôi trong Pause, Cài đặt hoặc màn hình nâng cấp.

## Gameplay hiện có

- **100 HP**, hitbox tròn 4 px đối với đạn, 12 px đối với thân quái; Focus không thay hitbox.
- **Graze**: né sát đạn nhận điểm và ít EXP; mỗi viên chỉ tính một lần, không thưởng khi bất tử hoặc Dash.
- **5 vũ khí**, tối đa 4 slot, mỗi vũ khí tối đa cấp 5; **6 nội tại**, tối đa 4 slot, mỗi nội tại tối đa cấp 3.
- **2 tiến hóa**: Wand 5 + Hồi chiêu 3 → Prism; Orbit 5 + Phạm vi 3 → Song quỹ đạo.
- Run thường dùng **Cirno giữa màn và Remilia cuối màn**; Test Boss có đủ 14 trận TH06. Số pha, đường đạn, di chuyển, phân thân và dừng thời gian do script từng Boss quản lý.
- Đàn quái từ 00:00–05:00 → Boss giữa → đàn quái đến 09:00 → Boss cuối. **Các pha có HP không tự hết giờ.** Pha sinh tồn vốn không thể gây sát thương của Flandre vẫn kết thúc theo lịch gốc.
- Vượt Spell Card mà không trúng đòn hay dùng Bomb nhận **capture**. Đồng hồ đàn quái dừng trong trận Boss.
- Nâng cấp đã max không còn xuất hiện trong lựa chọn; thẻ còn nâng được hiển thị **cấp hiện tại/MAX → cấp tiếp theo/MAX**.
- Có đạn đóng băng/tái phóng, dao, laser có báo trước và thực thể phụ theo script của trận đã chọn; hình vẽ vẫn là hình học đơn giản.
- **Dễ / Thường / Khó** tác động lên máu, sát thương, tốc độ, mật độ quái, số/tốc độ đạn, thời gian báo trước và moveset.
- Màn thắng/thua có thống kê build, sát thương từng vũ khí, graze, capture, Bomb và nguyên nhân nhận đòn cuối.

Luật chi tiết, giới hạn và cấu trúc mô phỏng: [docs/GAMEPLAY.md](docs/GAMEPLAY.md).
Tích hợp moveset hiện tại: [docs/TH06_PORT.md](docs/TH06_PORT.md). Bản pattern tự tạo trước đây: [docs/BOSS_REFERENCES.md](docs/BOSS_REFERENCES.md).

## Kiểm thử

~~~powershell
.\tests\run-tests.ps1
.\tests\run-tests.ps1 -Benchmark -Playtest -Original
~~~

Lệnh đầu kiểm tra nền chiến đấu và bộ pattern mẫu cũ trong fixture riêng. `-Original` kiểm tra dữ liệu thật hoàn toàn bằng Java: 14 trận × 3 mức khó, bốn nhánh Patchouli, seed, Bomb, laser, Pause, chơi lại và 514 mốc replay đã đối chiếu với bộ chạy trước khi chuyển đổi. Kiểm thử cũng xác nhận bộ mô phỏng không sinh tiến trình con. `-Benchmark` đo tải 300 quái + 2.000 đạn; `-Playtest` chạy ba build trên run hiện tại. Báo cáo và ảnh render nằm trong thư mục tạm được script in ra.

Kết quả, cấu hình đo và giới hạn của kiểm thử tự động: [docs/VALIDATION.md](docs/VALIDATION.md).

## Ý tưởng phát triển

(mn đóng góp thêm ý tưởng nhé)

4 maps + 1 map đánh trùm, 1 map các loại quái khác nhau, phù hợp bối cảnh ae chọn ( kiểu ae thích jojo thì nhét asset jojo vào, tùy chỉnh cho quái from jojo, touhou cũng thế nói chung thể hiện đặc trưng là đc )
khi tiêu diệt Boss sẽ đc cầm 1 vũ khí sang world khác

Sự kiện xuất hiện ở mini map (giải cứu, chạy đi nhặt đồ, cố thủ).

Cốt truyện: sv bách khoa lạc vào các thế giới, giúp đỡ nv trong giới đó rồi đánh bại con trùm với âm mưu phá đưa game mình xâm chiếm tgiới khác để ké fame

Đánh boss ẩn (Boss Mixi), hệ thống thành tựu các thứ, ending ẩn các thứ ( cái này t chưa nghĩ ra gì )

Chắc nv cầm 4 vũ khí thôi, tổng 10 cái và các loại buff kiểu đánh nhanh, tăng đạn, tăng vùng cho đỡ phải nhiều vũ khí (Hoặc có khi là 5)

hệ thống skill như né hoặc buff 2 súng (idk cảm giác quá liều, quá liều thì đừng thêm vào)
