# Nguồn dữ liệu, phần mềm và giấy phép

Bàn phím Hán Nôm được xây dựng trên công sức của nhiều người. Bảng dưới liệt kê mọi nguồn bên ngoài mà dự án sử dụng, cách sử dụng và giấy phép tương ứng.

## Dữ liệu

| Nguồn | Dùng cho | Giấy phép |
|---|---|---|
| [rime-ime-han-nom-data](https://github.com/miketvo/rime-ime-han-nom-data) © 2022 Tuong-Minh (Mike) Vo, tổng hợp từ chunom.org, Wiktionary, HVDic, Uỷ ban Phục sinh Hán Nôm; dựa trên Weasel Hannom của Keepout2010 | Dữ liệu gốc của từ điển gõ (`data/sources/hannom-telex.*`); dự án đã chỉnh sửa, khử trùng lặp và xếp hạng lại | MIT, xem [`data/sources/LICENSE-rime-ime-han-nom-data.txt`](data/sources/LICENSE-rime-ime-han-nom-data.txt) |
| *Hán Việt tự điển*, Thiều Chửu (1902–1954). Bản số hoá từ bộ dữ liệu [Cong123779/Han_Nom_Dataset](https://huggingface.co/datasets/Cong123779/Han_Nom_Dataset) (Hugging Face) | Âm và nghĩa Hán Việt (dòng `H` trong `nghia.txt`) | Phạm vi công cộng |
| [Wiktionary](https://vi.wiktionary.org), qua [Kaikki.org](https://kaikki.org) và gói `which-dialect-vi` | Nghĩa chữ Nôm, nghĩa từ (dòng `W` trong `nghia.txt`) | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/) |
| [Unicode Han Database (Unihan)](https://www.unicode.org/charts/unihan.html), trường kDefinition | Nghĩa tiếng Anh bổ sung (dòng `U` trong `nghia.txt`) | [Unicode License v3](https://www.unicode.org/license.txt) |
| [wordfreq](https://github.com/rspeer/wordfreq) © Robyn Speer | Tần suất từ tiếng Việt để xếp hạng gợi ý | Dữ liệu CC BY-SA 4.0 |

Vì `nghia.txt` chứa dữ liệu Wiktionary, và thứ tự trong `dict.txt` dựa trên wordfreq (đều CC BY-SA 4.0), hai tệp này được phát hành theo **CC BY-SA 4.0**. Khi dùng lại, vui lòng ghi nguồn "Dự án Bàn phím Hán Nôm" và các nguồn gốc ở trên, và chia sẻ theo cùng giấy phép.

Các công cụ dùng khi dựng dữ liệu (không đi kèm sản phẩm): [jieba](https://github.com/fxsjy/jieba) (MIT), [OpenCC](https://github.com/BYVoid/OpenCC) (Apache 2.0).

## Phông chữ và biểu tượng

| Nguồn | Dùng cho | Giấy phép |
|---|---|---|
| Phông **Nom Na Tong** 5.070 © 2011–2020 The Vietnamese Nôm Preservation Foundation • Nom Na Group | Hiển thị chữ Nôm trên Android, Windows | MIT, xem [`data/fonts/LICENSE-NomNaTong.txt`](data/fonts/LICENSE-NomNaTong.txt) |
| [Material Icons](https://fonts.google.com/icons) © Google | Biểu tượng bánh răng, mũi tên (Android) | [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) |

## Phần mềm nền

| Phần mềm | Quan hệ với dự án | Giấy phép |
|---|---|---|
| [Weasel 小狼毫](https://github.com/rime/weasel) / [librime](https://github.com/rime/librime) | Bộ gõ nền trên Windows. Dự án chỉ cung cấp kiểu gõ (schema, từ điển, bộ lọc Lua) chạy trên Weasel, không phân phối lại Weasel. | GPL-3.0 / BSD-3-Clause |
| Các bộ lọc Lua `hannom_telex_*.lua` trong `windows-ime/goi-cai-dat/rime/lua/` | Lấy từ rime-ime-han-nom-data, đã sửa lỗi và chỉnh sửa | MIT (như trên) |

## Trợ lý trí tuệ nhân tạo

Mã nguồn, xử lý dữ liệu, hình ảnh, video và giọng đọc thuyết minh được thực hiện với sự hỗ trợ của trợ lý AI **Claude** (Anthropic), theo định hướng và kiểm thử của tác giả. Giọng đọc thuyết minh được tạo bằng [VieNeu-TTS](https://huggingface.co/pnnbao-ump/VieNeu-TTS-v3-Turbo) (Apache 2.0).

---

Nếu bạn là tác giả của một nguồn ở trên và thấy việc ghi công chưa đúng, xin mở một Issue để dự án sửa ngay.
