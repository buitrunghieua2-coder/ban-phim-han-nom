# Bàn Phím Hán Nôm cho Android — mã nguồn bản 1.4

Bàn phím Android gõ chữ Hán và chữ Nôm bằng âm Quốc ngữ kiểu Telex. Chạy hoàn toàn trên máy, không xin quyền mạng.

## Có gì mới ở bản 1.4

- Nhắm Android 16 (API 36), theo yêu cầu của Google Play. Màn hình Cài đặt và Hướng dẫn hiển thị tràn viền đúng cách trên Android 15–16: nội dung không bị thanh trạng thái, thanh điều hướng hay bàn phím che.
- Đóng gói thêm định dạng AAB để đưa lên Google Play.

## Có gì mới ở bản 1.3

- Mục **Giới thiệu** trong Cài đặt: thông tin mã nguồn mở, nút **Nguồn dữ liệu và giấy phép** và nút mở mã nguồn trên GitHub.

## Có gì mới ở bản 1.2

- **Nhấn giữ một chữ để xem nghĩa.** Áp dụng cho chữ trên thanh gợi ý, chữ gợi ý tiếp theo và chữ trong bảng ▼. Bảng giải nghĩa thay chỗ bàn phím, gồm:
  - âm Hán Việt và âm Nôm;
  - loại chữ, mã Unicode và khối mã;
  - nghĩa Hán Việt theo Thiều Chửu (8.084 chữ);
  - nghĩa khi dùng làm chữ Nôm (tiếng Anh, Wiktionary);
  - nghĩa tiếng Anh từ Unihan cho chữ Thiều Chửu không có;
  - các từ ghép chứa chữ đó.
- Chạm vào một từ ghép để xem nghĩa của từ và của từng chữ trong từ. Nút ‹ để quay lại, **Chèn** để chọn chữ, ✕ hoặc ⌨ để về bàn phím.
- Cặp chữ chỉ gõ một lần sẽ không còn chen lên trước từ thông dụng trong phần gợi ý chữ tiếp theo.
- Sửa lỗi: phần gợi ý chữ tiếp theo đôi khi biến mất ở ứng dụng chạy chậm.
- Sửa lỗi dữ liệu: chữ "Ð" (eth của tiếng Iceland) trong bản Thiều Chửu số hoá được đổi lại thành "Đ".

## Có gì mới ở bản 1.1

- **Bánh răng ⚙ cài đặt** ở bên trái thanh gợi ý (hiện khi không đang gõ). Cài đặt cũng mở được từ ứng dụng, hoặc từ Cài đặt hệ thống → Bàn phím.
- **Giao diện**: Sáng / Tối / Theo hệ thống.
- **5 kiểu bàn phím**: Cổ điển, Son thếp vàng, Giấy dó, Ngọc bích, Lam ngọc. Mỗi kiểu có bản sáng và bản tối.
- **Chiều cao phím** (Thấp / Vừa / Cao), bật/tắt **hàng số**.
- **Rung** khi gõ (Nhẹ / Vừa / Mạnh) và **âm thanh** khi gõ, có thanh chỉnh âm lượng.
- **Thứ tự gợi ý mới**:
  - Chữ phổ biến hiện trước, dựa trên thứ hạng chunom.org, tần suất âm tiết tiếng Việt và tần suất chữ Hán.
  - Sửa lỗi của bản 1.0: khoảng 2.000 âm đọc phụ (trọng số 5 trong dữ liệu gốc) từng bị đưa lên đầu. Ví dụ "phu" ra 伏 trước 夫, "há" ra 下 trước chữ Nôm.
  - Gõ không dấu đoán đúng hơn, ví dụ "co" → 𣎏 (có). Gõ dở một âm cũng gợi ý hợp lý hơn, ví dụ "d" → 特 㐌 底 𦤾 (được, đã, để, đến).
- **Học theo thói quen** (có thể tắt, có nút xoá dữ liệu):
  - Chữ hay chọn được đưa lên trước.
  - Hai chữ thường gõ liền nhau sẽ được gợi ý sau chữ đầu. Sau 2 lần, cặp đó thành một từ ghép gõ được một lần, ví dụ "toiddi" → 碎𠫾.
  - Dữ liệu chỉ lưu trong máy (`files/history.tsv`).
  - Không học khi ô nhập yêu cầu không cá nhân hoá, ví dụ tab ẩn danh.

## Cấu trúc

| Tệp | Nội dung |
|---|---|
| `src/.../TelexEngine.java` | Bộ gõ Telex, giữ nguyên quy tắc bản 1.0 |
| `src/.../HanNomDict.java` | Từ điển, tách âm tiết, xếp hạng, gợi ý chữ tiếp theo, từ ghép đã học |
| `src/.../UserHistory.java` | Dữ liệu thói quen: đếm, độ gần đây, cặp chữ; lưu và đọc tệp |
| `src/.../KeyboardService.java` | Bàn phím (InputMethodService) |
| `src/.../SettingsActivity.java` | Màn hình Cài đặt |
| `src/.../SetupActivity.java` | Màn hình hướng dẫn bật bàn phím |
| `src/.../Theme.java`, `Ui.java` | Bảng màu và các thành phần giao diện |
| `src/.../Feedback.java` | Rung và âm thanh |
| `../data/dict.txt` | Từ điển đã tính lại điểm. Cột 3 là "chi phí": số nhỏ hiện trước |
| `../data/tools/build_dict.py` | Script tạo `../data/dict.txt` từ `../data/sources/dict-v1.0.txt` |
| `src/.../Definitions.java` | Tra nghĩa cho bảng nhấn giữ |
| `../data/nghia.txt` | Dữ liệu giải nghĩa: Thiều Chửu, Unihan, Wiktionary |
| `../data/tools/build_nghia.py` | Script tạo `../data/nghia.txt` từ `../data/sources/nghia/` |
| `assets/GIAY-PHEP.txt` | Ghi công và giấy phép, hiển thị trong Cài đặt → Giới thiệu |
| `test/` | Kiểm thử logic chạy trên máy tính (không cần Android) |

## Build

Cần JDK 17 trở lên và Android SDK gồm platform 36 và build-tools 36. Không dùng Gradle.

```bash
export ANDROID_HOME=/đường/dẫn/sdk
export KEYSTORE=/đường/dẫn/khoá.jks         # khoá ký của bạn — KHÔNG đưa lên git
KS_PASS='mật-khẩu-khoá' ./build.sh           # ra build/BanPhimHanNom-1.4.apk

# thêm bản AAB cho Google Play (cần bundletool 1.18+):
BUNDLETOOL=/đường/dẫn/bundletool KS_PASS='…' ./build.sh   # ra thêm build/BanPhimHanNom-1.4.aab
```

`build.sh` tự chép `../data/dict.txt`, `../data/nghia.txt` và phông `../data/fonts/NomNaTong-Regular.ttf` vào `assets/` trước khi đóng gói.

Muốn tăng phiên bản thì sửa `versionCode`/`versionName` trong `AndroidManifest.xml` và trong `build.sh`.

**Về khoá ký:** bản phát hành chính thức được ký bằng khoá của tác giả (SHA-256 `55:f7:69:f5:…:3a:20`). Bạn tự build thì dùng khoá của mình; bản đó sẽ không cài đè được lên bản chính thức (phải gỡ bản cũ trước).

## Kiểm thử

Chạy trên máy tính, không cần Android:

```bash
mkdir -p /tmp/t && javac -encoding UTF-8 -d /tmp/t src/vn/hannom/keyboard/{TelexEngine,HanNomDict,UserHistory,Definitions}.java test/vn/hannom/keyboard/*.java
java -cp /tmp/t vn.hannom.keyboard.LogicTest ../data/dict.txt
java -cp /tmp/t vn.hannom.keyboard.DefTest ../data/dict.txt ../data/nghia.txt
```

Giấy phép: mã nguồn GPL-3.0 (xem `../LICENSE`); dữ liệu và phông xem `../NGUON-VA-GIAY-PHEP.md`.
