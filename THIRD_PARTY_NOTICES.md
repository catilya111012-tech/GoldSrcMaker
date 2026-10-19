# Third-party notices

GoldSrc Mobile Game Maker распространяется по лицензии **GNU GPL v3**
(см. `LICENSE`), потому что в APK встроен свободный игровой движок
Xash3D FWGS (GPL-3.0). Исходный код самого приложения доступен в этом
репозитории — это выполняет требование GPL о предоставлении исходников.

## Распространяемые компоненты

### Встроенный движок: Xash3D FWGS

- Файлы: `app/src/main/jniLibs/arm64-v8a/libxash.so`, `libSDL2.so`,
  `libmenu.so`, `libfilesystem_stdio.so`, `libref_gles3compat.so`,
  `libref_gles1.so`, `libref_gl4es.so`, `libavcodec.so`, `libavformat.so`,
  `libavutil.so`, `libswscale.so`, `libswresample.so`, а также
  `app/src/main/assets/` (gfx/shell, шрифты, touch_default, touch_presets,
  scripts, gamecontrollerdb.txt, cacert.pem, logos).
- Источник: официальный APK `xash3d-fwgs-android.apk` из
  https://github.com/FWGS/xash3d-fwgs (ветка/релиз continuous).
- Лицензия: **GPL-3.0** (https://github.com/FWGS/xash3d-fwgs/blob/master/LICENSE).
  Исходный код движка доступен по ссылке выше.
- Java-обвязка: `app/src/main/java/org/libsdl/app/*` — SDL2 для Android
  (https://github.com/libsdl-org/SDL, тег release-2.32.10), лицензия **zlib**;
  `app/src/main/java/com/goldsrc/maker/engine/XashActivity.java` и
  `.../engine/util/SoftKeyboardPan.java` — порт `su.xash.engine.XashActivity`
  из Xash3D FWGS (GPL-3.0), адаптирован для встроенного движка.
- Внутри движка используется FFmpeg (libav*): лицензия **LGPL-2.1-or-later**
  (https://ffmpeg.org/legal.html). Библиотеки линкуются динамически и
  заменяемы в сборке.

### Игровые библиотеки Half-Life (hlsdk-portable)

- Файлы: `libhl_android_arm64.so` (серверная логика),
  `libclient_android_arm64.so` (клиент/HUD).
- Источник: сборка из https://github.com/FWGS/hlsdk-portable (входит в
  официальный APK Xash3D FWGS).
- Правовой статус: порт Half-Life 1 SDK. Для запуска **необходима
  оригинальная игра Half-Life** (её файлы пользователь предоставляет сам);
  библиотеки не содержат игровых ассетов. Half-Life и GoldSrc — товарные
  знаки Valve Corporation.

### Компиляторы карт (VHLT)

- Файлы: `app/src/main/jniLibs/arm64-v8a/libhlcsg.so`, `libhlbsp.so`,
  `libhlvis.so`, `libhlrad.so`, `libcxx_vhlt.so`.
- Источник: «Vluzacn's custom build of Zoner's Half-Life Tools» (ZHLT),
  версия 34 (`hl*sg v3.4 VL34`), автор vluzacn@163.com, http://www.zhlt.info.
- Лицензия: **GPL** (terms of use в архиве исходников). Локальные патчи
  описаны в `app/src/main/jniLibs/README.md`.

### Прочее

- **androidx.documentfile** — Copyright (C) The Android Open Source Project,
  лицензия Apache License 2.0
  (http://www.apache.org/licenses/LICENSE-2.0).

## Компоненты, которые НЕ распространяются этим репозиторием

- **Файлы игр Valve** (`.wad`, `.mdl`, `.bsp`, `.pak` и т.п.) — не входят в
  состав приложения. Пользователь использует собственные файлы.

## Товарные знаки

Half-Life, GoldSrc и связанные обозначения являются товарными знаками
Valve Corporation. Этот проект не связан с Valve Corporation, не одобрен
и не спонсируется ею.
