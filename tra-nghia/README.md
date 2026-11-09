# Tra Nghĩa Hán Nôm — từ điển nổi cho Windows

Rê chuột lên một chữ Hán hoặc chữ Nôm ở bất kỳ ứng dụng nào, dừng lại một thoáng: ô nổi hiện âm đọc, nghĩa từ ghép, nghĩa Hán Việt (Thiều Chửu), nghĩa Nôm và các từ ghép liên quan.

## Cách hoạt động

- **Chương trình khay hệ thống** viết bằng C++ / Win32, không cần thư viện ngoài. Exe liên kết tĩnh, khoảng 1,2 MB.
- **Đọc chữ dưới con trỏ** bằng UI Automation (TextPattern) trên một luồng riêng:
  - `RangeFromPoint` → kiểm tra khung chữ → lấy vài chữ xung quanh để nhận từ ghép.
  - Xử lý cặp surrogate cho chữ ở vùng Unicode mở rộng: thử vị trí lân cận khi ứng dụng báo lệch.
  - Nhớ vùng văn bản vừa tra để lần sau nhanh hơn.
- **Ô nổi** vẽ bằng Direct2D + DirectWrite:
  - chữ Hán/Nôm dùng phông Nom Na Tong nạp riêng;
  - có thanh cuộn; giao diện Giấy dó hoặc Sơn thếp vàng;
  - hỗ trợ DPI theo từng màn hình.
- **Lõi tra từ điển** (`src/core.cpp`) không phụ thuộc Windows, nên kiểm thử được trên Linux.

## Build (trên Linux, biên dịch chéo bằng mingw-w64)

```bash
sudo apt install g++-mingw-w64-x86-64
./build.sh                      # ra build/TraNghiaHanNom.exe
./make-package.sh 1.4.1         # ra build/TraNghiaHanNom-1.4.1.zip (kèm data và phông)
```

Kiểm thử lõi:

```bash
g++ -std=c++17 -O2 -o /tmp/core_test src/core.cpp test/core_test.cpp
/tmp/core_test ../data/dict.txt ../data/nghia.txt
```

Chạy thử giao diện không cần UI Automation (ví dụ dưới Wine): `TraNghiaHanNom.exe --demo "我越南人" 1 [--dark] [--scroll 300]`.

Giấy phép: GPL-3.0 (xem `../LICENSE`). Dữ liệu và phông: xem `../NGUON-VA-GIAY-PHEP.md`.
