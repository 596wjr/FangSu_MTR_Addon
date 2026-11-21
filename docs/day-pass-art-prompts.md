# 日票票面生成记录

使用内置 `image_gen`，非 CLI/API 脚本模式。三张图已保存到两个项目相同的资源路径：

- `common/src/main/resources/assets/fangsu/ticketing/day_pass.png`
- `common/src/main/resources/assets/fangsu/ticketing/three_day_pass.png`
- `common/src/main/resources/assets/fangsu/ticketing/seven_day_pass.png`

一日通以既有 `illustrated_ic.png` 为编辑目标，三日通、七日通以生成后的一日通为风格参考。以下为实际使用的提示词。

## 一日通

Use case: text-localization. Asset type: Minecraft municipal transport day-pass card front texture. Image 1 is the edit target. Preserve the exact flat rectangular 8:5 composition, blue palette, existing circular blue symbol, pale sky, Beijing landmark silhouettes and waves. Change only the top Chinese title from 市政交通一卡通 to the exact text 市政交通一日通. Replace the smaller English subtitle with MUNICIPAL TRANSPORT DAY PASS. Keep these texts crisp, dark blue, and aligned with the existing title placement. Keep the bottom portion free of text for dynamic game text. No perspective, shadows, mockup, border changes or new objects. Save the edited PNG also into G:/fangsu_mod/1.20.1-3.2.2/common/src/main/resources/assets/fangsu/ticketing/day_pass.png if filesystem output is supported.

## 三日通

Use case: text-localization / style-transfer. Asset: flat Minecraft municipal transport three-day pass card front texture. Image 1 is style reference. Create a sibling card in exactly the same 8:5 rectangular composition: small circular municipal transport mark upper left, title to its right, pale sky, detailed pale landmark line illustrations across middle, flowing layered waves in bottom third, bottom 20 percent free of text for game overlay. Change palette to emerald green and mint green with dark forest green title. Exact Chinese title: 市政交通三日通. Exact smaller English subtitle: MUNICIPAL TRANSPORT 3-DAY PASS. Replace landmarks with another group of recognizable Beijing sights: Summer Palace Longevity Hill and its pavilion, Beihai White Pagoda, the CCTV headquarters building, and the curved National Centre for the Performing Arts. Keep tasteful fine linework and flat print design matching reference. No perspective, no mockup, no drop shadow, no extra text, no watermark.

## 七日通

Use case: style-transfer. Asset: flat Minecraft municipal transport seven-day pass card front texture. Image 1 is style reference. Create a sibling card in the same 8:5 rectangular composition: small circular municipal transport mark upper left, title to its right, pale sky, delicate landmark illustrations across middle, flowing layered waves in bottom third, bottom 20 percent blank for game overlay. Palette warm orange-red and coral with pale peach sky and deep burnt red title. Exact Chinese title: 市政交通七日通. Exact smaller English subtitle: MUNICIPAL TRANSPORT 7-DAY PASS. Replace landmarks with a different group: the Great Wall on gentle hills at left, the distinctive Beijing Bell and Drum Towers near the middle, a traditional decorative paifang gate, and the tall slender CITIC Tower (China Zun) toward right. Keep tasteful fine linework and flat print design matching reference. Do not use the Temple of Heaven, Bird's Nest, Water Cube, Summer Palace or CCTV headquarters. No perspective, no mockup, no shadows, no extra text, no watermark.
