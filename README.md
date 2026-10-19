# GoldSrc Mobile Game Maker

Мобильный редактор уровней и игровой логики для GoldSrc прямо на Android.
Полностью свободный инструмент, работающий по принципу **Data/Code Separation**
(как LibertyRecomp / OpenMW): приложение **не содержит** файлов Valve
(`.wad`, `.mdl`, `.bsp`) — пользователь подкладывает свой контент отдельно.

## Юридический статус

- В APK нет ни одной оригинальной ассеты Valve. Только код и пустой каркас каталогов.
- Компиляторы карт (`hlcsg`/`hlbsp`/`hlvis`/`hlrad`) — сторонние свободные утилиты
  (VHLT, ericw-tools; GPL), их бинарники для arm64 встроены в APK и запускаются
  напрямую из `nativeLibraryDir`.
- Движок для запуска карт — свободный Xash3D FWGS (GPL-3.0), **встроен в APK**
  (нативные библиотеки + ассеты движка). Отдельная установка движка не нужна;
  если сборка без встроенного движка (другие ABI), лаунчер по-прежнему умеет
  запускать внешний Xash3D FWGS (`su.xash.engine`).
- Сам код проекта распространяется по лицензии **GNU GPL v3** (см. `LICENSE`) —
  этого требует встраивание GPL-движка. Сторонние компоненты и товарные знаки —
  см. `THIRD_PARTY_NOTICES.md`.
- Half-Life, GoldSrc и связанные обозначения — товарные знаки Valve Corporation.
  Проект не связан с Valve, не одобрен и не спонсируется ею.

## Структура проекта

```
GoldSrcMaker/
├── settings.gradle / build.gradle / gradle.properties
└── app/
    ├── build.gradle
    └── src/main/
        ├── AndroidManifest.xml
        ├── assets/                            # ассеты встроенного движка (gfx/shell, шрифты, touch_*)
        ├── jniLibs/arm64-v8a/                 # встроенный Xash3D FWGS + компиляторы VHLT (GPL)
        ├── java/org/libsdl/app/               # SDL2 для Android (zlib)
        ├── java/com/goldsrc/maker/
        │   ├── MainActivity.java              # лаунчер + проверка ресурсов + init конструктора
        │   ├── core/
        │   │   ├── StorageManager.java        # Scoped Storage: public / SAF-импорт
        │   │   ├── GameFileValidator.java     # проверка halflife.wad и структуры valve/
        │   │   ├── Project.java               # модель проекта
        │   │   ├── ProjectManager.java        # создание/загрузка/сохранение, экспорт .map
        │   │   ├── WadReader.java             # чтение текстур и палитры из WAD2/WAD3
        │   │   ├── WadWriter.java             # запись/патч WAD3 (мип-уровни + палитра)
        │   │   ├── TextureImage.java          # палитровая текстура (индексы + палитра)
        │   │   ├── TextureStore.java          # projects/<имя>/textures.wad
        │   │   ├── ModelData.java / ModelStore.java  # части модели (Box) → models/*.model.json
        │   │   └── MdlWriter.java             # экспорт .mdl (studio v10, статическая)
        │   ├── editor/
        │   │   ├── EditorActivity.java        # хаб: 2D / Блоки / Код / 3D
        │   │   ├── BrushCanvasView.java       # сенсорный 2D-конструктор брашей + выделение энтити
        │   │   ├── Brush.java                 # браш → 6 фейсов Valve 220
        │   │   ├── EntityData.java            # энтити и keyvalues
        │   │   ├── EntityCatalog.java         # каталог классов энтити (поля, defaults, справка)
        │   │   ├── MapData.java               # карта + экспорт в .map (список WAD в worldspawn)
        │   │   ├── TexturesActivity.java      # редактор текстур (пиксель-арт → textures.wad)
        │   │   ├── PixelCanvasView.java       # палитровое рисование: карандаш/ластик/заливка/пипетка
        │   │   ├── PaletteView.java           # палитра 256 цветов
        │   │   └── ModelEditorActivity.java   # редактор моделей: части, превью, экспорт .mdl
        │   ├── scripting/
        │   │   ├── HybridScriptEngine.java    # гибрид: граф ⇄ текст, симуляция, autosave
        │   │   ├── BlockType.java             # каталог блоков (события/действия/условия)
        │   │   ├── BlockNode.java / BlockGraph.java
        │   │   ├── CodeGenerator.java         # блоки → код и код → блоки (регионы)
        │   │   ├── ScriptSimulator.java       # чистая симуляция цепочек блоков (без Android)
        │   │   ├── CodeEditorView.java        # редактор кода с подсветкой
        │   │   └── BlockGraphView.java        # нодальное полотно (drag, tap, long-press)
        │   ├── compiler/
        │   │   ├── CompilerNative.java        # JNI-мост к портам компиляторов
        │   │   ├── CompilerToolchain.java     # поиск/проверка запуска hlcsg/hlbsp/hlvis/hlrad
        │   │   └── MapCompiler.java           # пайплайн CSG→BSP→VIS→RAD + живой лог стадий
        │   ├── engine/
        │   │   └── XashActivity.java          # встроенный движок: порт su.xash.engine.XashActivity
        │   ├── launcher/
        │   │   └── GameLauncher.java          # запуск встроенного/внешнего Xash3D с -basedir/-game/+map
        │   └── render/
        │       ├── VulkanPreviewActivity.java # превью карты: геометрия из проекта
        │       ├── GeometryBuilder.java       # браши/энтити → вершинные буферы (pos+цвет)
        │       └── VulkanSurfaceView.java     # JNI-поверхность Vulkan
        ├── cpp/
        │   ├── CMakeLists.txt
        │   ├── native_bridge.cpp              # fork/exec стадий компиляции
        │   ├── vulkan_renderer.cpp/.h         # Vulkan: swapchain + depth + pipeline + отрисовка карты
        │   ├── spirv_shaders.h                # сгенерированные SPIR-V массивы (коммитится)
        │   └── shaders/triangle.vert/.frag    # GLSL-исходники шейдеров
        └── res/values/strings.xml, themes.xml
```

## Рабочая папка (Data/Code Separation)

```
/sdcard/Documents/GoldSrcMaker/
├── valve/                 # ← сюда копируются ВАШИ файлы игры
│   ├── halflife.wad       # обязательный (проверяется при старте)
│   ├── models/ sound/ sprites/ gfx/ ...
│   └── maps/              # скомпилированные карты кладутся сюда
├── projects/<имя>/        # project.json, map.json, main.map, graph.json,
│                          # textures.wad (свои текстуры), models/*.model.json
└── compiled/<имя>.bsp     # результат компиляции
```

Если нет доступа «все файлы» — приложение работает в приватном каталоге и умеет
импортировать папки через SAF (`Импорт ресурсов`), рекурсивно копируя контент.

## Сборка

1. Android Studio (Ladybug+) или `./gradlew assembleDebug` (нужны JDK 17 + NDK).
2. Для запуска карт ничего доустанавливать не нужно: движок Xash3D FWGS встроен
   (arm64-v8a, `com.goldsrc.maker.engine.XashActivity` — порт `su.xash.engine.XashActivity`).
   Лаунчер запускает его с extras `basedir`/`gamedir`/`argv=+map <имя>` и проверяет
   наличие `halflife.wad` и `maps/<имя>.bsp` перед запуском. На ABI без встроенных
   библиотек (x86_64) используется внешний Xash3D FWGS, если он установлен.
3. Для компиляции карт есть два пути:
   - **SAF-импорт**: в приложении кнопка «Импорт компиляторов (SAF)» — выберите папку
     с бинарниками `libhlcsg.so` / `libhlbsp.so` / `libhlvis.so` / `libhlrad.so`.
     Приложение проверит ELF-заголовок, скопирует их в приватный каталог `tools/`
     и **проверит запуск** (Android 10+ может запрещать exec из приватного каталога —
     ограничение W^X). Результат проверки показывается честно.
   - **Надёжный путь**: положите порты компиляторов в
     `app/src/main/jniLibs/<abi>/libhlcsg.so`, `libhlbsp.so`, `libhlvis.so`, `libhlrad.so`
     и пересоберите APK — файлы попадут в `nativeLibraryDir`, откуда запуск разрешён.
   Во время компиляции диалог показывает живой прогресс: текущая стадия + последняя
   строка её лога.
4. Шейдеры Vulkan: исходники `app/src/main/cpp/shaders/*.vert/.frag`; после правки
   запустите `bash tools/shaders/build.sh` (нужен `pkg install glslang`) — он обновит
   `app/src/main/cpp/spirv_shaders.h`, который компилируется в APK.

## Самопроверка без Android SDK

```bash
bash tools/selftest/run.sh    # нужен JDK 17 (в Termux: pkg install openjdk-17)
```

Проверяет на реальном выполнении: round-trip «блоки ⇄ текст» (правки, ручной код,
свободные сегменты, JSON), новые блоки (события use/death, переменные, задержки,
здоровье игрока, смена уровня), симуляцию цепочек, экспорт браша в `.map`
(наружные нормали Valve 220, список WAD в worldspawn), чтение текстур из WAD3,
запись/патч WAD3 (мипы, палитра, roundtrip), палитровое хранилище текстур проекта,
модели (JSON-хранилище, bounds/клампы), экспорт `.mdl` (структура studio v10:
оффсеты, текстуры-мипы, кость, анимация), каталог энтити (поля/defaults/скриптовые
ключи) и сборку геометрии для Vulkan (боксы брашей/модели, кубики энтити,
усечение больших карт). `PrintScript` печатает образец кода.

## Гибридный конструктор (OpenCode-среда)

- **Визуальный режим**: «Добавить блок» → цепочка узлов (события → действия → условия).
  Тап по узлу — параметры, long-press — удаление, drag — позиция.
- **Текстовый режим**: тот же код открыт в редакторе с подсветкой (`gs_*` API).
- Синхронизация двусторонняя через регионы `// >>> block:N` … `// <<< block:N`:
  - «Блоки → код» — регенерация (ваш ручной текст вне регионов сохраняется);
  - «Код → блоки» — парсинг чистых регионов в параметры блоков; регионы с ручной
    правкой помечаются флагом `userEdited` и не перезаписываются.
- «Тест логики» — симуляция цепочки без запуска движка.

## 2D-конструктор и энтити

- Тап по энтити выбирает её (жёлтое кольцо) и открывает диалог свойств:
  редактируемые keyvalues по каталогу класса (light, monster_*, items, …),
  смена класса, добавление произвольного свойства, дублирование и удаление.
- Перетаскивание выбранной энтити двигает её по сетке; кнопка «Энтити» задаёт
  класс для новых энтити с разумными defaults (например, light = 300).
- Текстура брашей задаётся кнопкой «Текстура»: ввод вручную, список из вашего
  `halflife.wad` или **текстуры проекта** (см. редактор текстур). Можно открыть
  редактор текстур прямо из диалога.

## Редактор текстур

- Палитровое рисование (как в настоящих GoldSrc-текстурах): карандаш, ластик,
  заливка, пипетка, отмена (до 30 шагов). Размеры 32/64/128/256.
- Палитра берётся из вашего `halflife.wad` (кнопка «Импорт…» / первый запуск),
  поэтому цвета в игре будут точными; без WAD используется палитра по умолчанию.
- Текстуры хранятся в `projects/<имя>/textures.wad` (формат WAD3 с мип-уровнями).
- Экспорт в игру: «Экспорт в игру» →
  - **отдельный WAD** `valve/<проект>_tex.wad` (безопасно), или
  - **дописать в halflife.wad** (при первом экспорте создаётся `halflife.wad.bak`).
- При компиляции карты `textures.wad` проекта автоматически добавляется в
  `"wad"`-ключ worldspawn — свои текстуры попадают в BSP.

## Редактор моделей

- Модель = список частей-коробок: имя, текстура, позиция, размер (1..512), UV-масштаб.
- Модели хранятся в `projects/<имя>/models/<ИМЯ>.model.json`; кнопка «3D-превью»
  показывает модель в Vulkan-рендере.
- «Экспорт .mdl» собирает статическую модель studio v10 (одна кость, одна
  анимация, встроенные текстуры с мипами) в `valve/models/<имя>.mdl` и
  пересчитывает палитру под `halflife.wad`, чтобы цвета совпали в игре.
- В игре модель ставится, например, `cycler` (`model` = `models/<имя>.mdl`)
  или другими модельными энтити.

## 3D-превью (Vulkan)

- `vulkan_renderer.cpp` создаёт instance/device/swapchain, depth-буфер, render pass
  с depth-аттачментом и графический pipeline на SPIR-V-шейдерах (pos + цвет,
  push-constant MVP), проверяя GPU-совместимость (включая AMD Xclipse/RDNA).
- `GeometryBuilder` (чистая Java) превращает браши карты в боксы с затенением по
  граням, а энтити — в цветные кубики; вершины загружаются в vertex buffer.
- Вид — изометрия сверху (45°), камера автоматически охватывает всю карту.
  Геометрия дополнительно проверяется селфтестом.
