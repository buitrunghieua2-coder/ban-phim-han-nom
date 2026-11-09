# Dữ liệu dùng chung

| Tệp | Nội dung |
|---|---|
| `dict.txt` | Từ điển gõ: `chữ <TAB> âm:thanh ... <TAB> chi phí`. Chi phí nhỏ thì hiện trước. 35.891 mục; 16.766 chữ đơn, trong đó 3.917 chữ ở vùng Unicode mở rộng |
| `nghia.txt` | Dữ liệu giải nghĩa. Dòng `H`: Thiều Chửu (8.084 chữ). Dòng `W`: Wiktionary (6.917 âm và từ). Dòng `U`: Unihan (4.262 chữ) |
| `fonts/` | Phông Nom Na Tong 5.070 (MIT) |
| `sources/` | Dữ liệu gốc: từ điển bản 1.0, rime-ime-han-nom-data, bản số hoá Thiều Chửu, Unihan, Wiktionary |
| `tools/build_dict.py` | Tính lại thứ tự xếp hạng: `python3 tools/build_dict.py sources/dict-v1.0.txt dict.txt` (cần `pip install wordfreq jieba opencc-python-reimplemented`) |
| `tools/build_nghia.py` | Dựng `nghia.txt`: `python3 tools/build_nghia.py sources/nghia/Thieu_Chuu_Dictionary.csv sources/nghia/Unihan_Vietnamese.csv sources/nghia/wiktionary-words dict.txt nghia.txt` |

**Giấy phép:** `dict.txt` và `nghia.txt` theo **CC BY-SA 4.0** (toàn văn trong `LICENSE`). Nguồn gốc và giấy phép từng thành phần xem `../NGUON-VA-GIAY-PHEP.md`.
