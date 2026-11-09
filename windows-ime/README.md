# Bàn phím Hán Nôm cho Windows — kiểu gõ cho Weasel (Rime)

Kiểu gõ Hán Nôm chạy trên bộ gõ mã nguồn mở [Weasel 小狼毫](https://github.com/rime/weasel) (Windows 8.1–11). Dùng chung từ điển và cách xếp hạng với bản Android.

## Cấu trúc

| Đường dẫn | Nội dung |
|---|---|
| `goi-cai-dat/` | Nội dung gói phát hành: `CAI-DAT.bat`, `GO-BO.bat`, `HUONG-DAN.txt` và thư mục `rime/` |
| `goi-cai-dat/rime/hannom.schema.yaml` | Kiểu gõ: Telex, chuyển sang không dấu, công tắc **Gõ đủ dấu / Gõ không dấu** |
| `goi-cai-dat/rime/hannom.dict.yaml` | Từ điển Rime, dựng từ `../data/dict.txt` |
| `goi-cai-dat/rime/lua/` | Bộ lọc Lua: xem trước Telex, sửa preedit, **hannom_exact_first.lua** (đưa chữ đúng âm lên đầu) |
| `goi-cai-dat/rime/default.custom.yaml`, `weasel.custom.yaml` | Cấu hình Weasel; giao diện Giấy dó, Sơn thếp vàng |
| `tools/MakeRimeDict.java` | Dựng `hannom.dict.yaml` từ `dict.txt` (dùng `TelexEngine` của bản Android) |
| `tools/make_rime_schema.py` | Script đã dùng để tạo schema ban đầu từ rime-ime-han-nom-data. **Schema trong `goi-cai-dat/` là bản đang được bảo trì**; đừng ghi đè bằng kết quả của script |
| `tools/console.c` | Bàn điều khiển thử nghiệm librime trên Linux (gõ phím, in danh sách chữ gợi ý) |

## Đóng gói

```bash
./make-package.sh 1.2      # ra build/BanPhimHanNom-Windows-1.2.zip (kèm phông từ ../data/fonts)
```

## Dựng lại từ điển Rime

```bash
mkdir -p /tmp/m && javac -encoding UTF-8 -d /tmp/m ../android/src/vn/hannom/keyboard/TelexEngine.java tools/MakeRimeDict.java
java -cp /tmp/m MakeRimeDict ../data/sources/hannom-telex.dict.yaml ../data/dict.txt goi-cai-dat/rime/hannom.dict.yaml
```

Giấy phép: GPL-3.0 (xem `../LICENSE`). Các bộ lọc `hannom_telex_*.lua` lấy từ rime-ime-han-nom-data (MIT, © Mike Vo), đã sửa lỗi. Xem `../NGUON-VA-GIAY-PHEP.md`.
