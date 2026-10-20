Cuộc phiêu lưu kì lạ của SVBK

## Chơi bản thử nghiệm

- Chạy `java/run.bat` (cần JDK; đường dẫn Java trong script hiện là JDK 27).
- Màn hình Start có **Bắt đầu** và **Cài đặt**.
- **WASD / phím mũi tên**: di chuyển; vũ khí tự tấn công.
- **Esc**: tạm dừng / tiếp tục. Trong Cài đặt, Esc quay về menu trước.
- Khi tạm dừng từ màn hình lên cấp, tiếp tục sẽ trở lại đúng các thẻ nâng cấp đang chọn.
- **1 / 2 / 3 hoặc chuột**: chọn nâng cấp. **Space / Enter**: chơi lại sau khi thua.
- Cài đặt có bật/tắt âm thanh và âm lượng (hiện chưa kèm file âm thanh).
- Chọn độ khó trước khi bắt đầu ván. Có thể chỉnh âm thanh trong lúc tạm dừng.
- Cài đặt được giữ trong phiên mở game; về Start kết thúc ván hiện tại.

| Đặc điểm | Dễ | Thường | Khó |
| --- | --- | --- | --- |
| Máu quái / Boss | ×0.75 | ×1 | ×1.4 |
| Sát thương quái / Boss (kể cả vũ khí) | ×0.7 | ×1 | ×1.35 |
| Tốc độ di chuyển quái / Boss | ×0.8 | ×1 | ×1.2 |
| Khoảng sinh quái ban đầu → tối thiểu (frame) | 85 → 22 | 65 → 15 | 45 → 10 |
| Số lần né của Ninja | 1 | 2 | 3 |
| Hồi né Ninja (frame) | 40 | 25 | 16 |
| Thời gian Boss báo trước lướt (frame) | 45 | 30 | 22 |
| Hồi lướt Boss (frame) | 300 | 220 | 150 |
| Số cú lướt liên tiếp của Boss | 1 | 1 | 2 |

### Kiểm tra menu và độ khó

Từ thư mục gốc, chạy PowerShell:

```powershell
$buildDir = Join-Path $env:TEMP 'svbk-menu-build'
New-Item -ItemType Directory -Force -Path $buildDir | Out-Null
javac -encoding UTF-8 -d $buildDir (Get-ChildItem java -Filter '*.java').FullName
javac -encoding UTF-8 -cp $buildDir -d $buildDir tests/GameMenuTest.java
java '-Djava.awt.headless=true' -cp $buildDir GameMenuTest $buildDir
```

## Ý tưởng phát triển

(mn đóng góp thêm ý tưởng nhé)

4 maps + 1 map đánh trùm, 1 map các loại quái khác nhau, phù hợp bối cảnh ae chọn ( kiểu ae thích jojo thì nhét asset jojo vào, tùy chỉnh cho quái from jojo, touhou cũng thế nói chung thể hiện đặc trưng là đc )
khi tiêu diệt Boss sẽ đc cầm 1 vũ khí sang world khác

Sự kiện xuất hiện ở mini map (giải cứu, chạy đi nhặt đồ, cố thủ).

Cốt truyện: sv bách khoa lạc vào các thế giới, giúp đỡ nv trong giới đó rồi đánh bại con trùm với âm mưu phá đưa game mình xâm chiếm tgiới khác để ké fame 

Đánh boss ẩn (Boss Mixi), hệ thống thành tựu các thứ, ending ẩn các thứ ( cái này t chưa nghĩ ra gì )

Chắc nv cầm 4 vũ khí thôi, tổng 10 cái và các loại buff kiểu đánh nhanh, tăng đạn, tăng vùng cho đỡ phải nhiều vũ khí (Hoặc có khi là 5)

hệ thống skill như né hoặc buff 2 súng (idk cảm giác quá liều, quá liều thì đừng thêm vào)
