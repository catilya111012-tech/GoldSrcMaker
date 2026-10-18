# GoldSrc Mobile Game Maker

Мобильный редактор уровней и игровой логики для GoldSrc прямо на Android.
Полностью свободный инструмент, работающий по принципу **Data/Code Separation**
(как LibertyRecomp / OpenMW): приложение **не содержит** файлов Valve
(`.wad`, `.mdl`, `.bsp`) — пользователь подкладывает свой контент отдельно.

## Юридический статус

- В APK нет ни одной оригинальной ассеты Valve. Только код и пустой каркас каталогов.
- Компиляторы карт (`hlcsg`/`hlbsp`/`hlvis`/`hlrad`) — сторонние свободные утилиты
  (VHLT, ericw-tools; GPL). Приложение их не распространяет: бинарники кладутся
  в проект отдельно (см. ниже) или собираются из исходников через NDK.
- Движок для запуска карт — свободный Xash3D FWGS (F-Droid `su.xash.engine`).
  Лаунчер только передаёт ему параметры запуска.
- Код проекта распространяется по лицензии MIT (см. `LICENSE`),
  сторонние компоненты и товарные знаки — см. `THIRD_PARTY_NOTICES.md`.
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
        ├── java/com/goldsrc/maker/
        │   ├── MainActivity.java              # лаунчер + проверка ресурсов + init конструктора
        │   ├── core/
        │   │   ├── StorageManager.java        # Scoped Storage: public / SAF-импорт
        │   │   ├── GameFileValidator.java     # проверка halflife.wad и структуры valve/
        │   │   ├── Project.java               # модель проекта
        │   │   ├── ProjectManager.java        # создание/загрузка/сохранение, экспорт .map
        │   │   └── WadReader.java             # чтение списка текстур из WAD2/WAD3
        │   ├── editor/
        │   │   ├── EditorActivity.java        # хаб: 2D / Блоки / Код / 3D
        │   │   ├── BrushCanvasView.java       # сенсорный 2D-конструктор брашей
        │   │   ├── Brush.java                 # браш → 6 фейсов Valve 220
        │   │   ├── EntityData.java            # энтити и keyvalues
        │   │   └── MapData.java               # карта + экспорт в .map
        │   ├── scripting/
        │   │   ├── HybridScriptEngine.java    # гибрид: граф ⇄ текст, симуляция, autosave
        │   │   ├── BlockType.java             # каталог блоков (события/действия/условия)
        │   │   ├── BlockNode.java / BlockGraph.java
        │   │   ├── CodeGenerator.java         # блоки → код и код → блоки (регионы)
        │   │   ├── CodeEditorView.java        # редактор кода с подсветкой
        │   │   └── BlockGraphView.java        # нодальное полотно (drag, tap, long-press)
        │   ├── compiler/
        │   │   ├── CompilerNative.java        # JNI-мост к портам компиляторов
        │   │   └── MapCompiler.java           # пайплайн CSG→BSP→VIS→RAD
        │   ├── launcher/
        │   │   └── GameLauncher.java          # запуск Xash3D FWGS с -basedir/-game/+map
        │   └── render/
        │       ├── VulkanPreviewActivity.java
        │       └── VulkanSurfaceView.java     # JNI-поверхность Vulkan
        ├── cpp/
        │   ├── CMakeLists.txt
        │   ├── native_bridge.cpp              # fork/exec стадий компиляции
        │   └── vulkan_renderer.cpp/.h         # Vulkan: instance→swapchain→clear-loop
        └── res/values/strings.xml, themes.xml
```

## Рабочая папка (Data/Code Separation)

```
/sdcard/Documents/GoldSrcMaker/
├── valve/                 # ← сюда копируются ВАШИ файлы игры
│   ├── halflife.wad       # обязательный (проверяется при старте)
│   ├── models/ sound/ sprites/ gfx/ ...
│   └── maps/              # скомпилированные карты кладутся сюда
├── projects/<имя>/        # project.json, map.json, main.map, graph.json, main.angelscript
└── compiled/<имя>.bsp     # результат компиляции
```

Если нет доступа «все файлы» — приложение работает в приватном каталоге и умеет
импортировать папки через SAF (`Импорт ресурсов`), рекурсивно копируя контент.

## Сборка

1. Android Studio (Ladybug+) или `./gradlew assembleDebug` (нужны JDK 17 + NDK).
2. Для запуска карт: установите Xash3D FWGS с F-Droid.
3. Для компиляции карт: положите собранные порты компиляторов в
   `app/src/main/jniLibs/<abi>/libhlcsg.so`, `libhlbsp.so`, `libhlvis.so`, `libhlrad.so`
   (переименованные родные бинарники портов) и пересоберите APK.
   Альтернатива: исходники в `app/src/main/cpp/compiler_tools/` с собственным CMakeLists — подхватятся автоматически.

## Самопроверка без Android SDK

```bash
bash tools/selftest/run.sh    # нужен JDK 17 (в Termux: pkg install openjdk-17)
```

Проверяет на реальном выполнении: round-trip «блоки ⇄ текст» (правки, ручной код,
свободные сегменты, JSON), экспорт браша в `.map` (наружные нормали Valve 220)
и чтение текстур из WAD3. `PrintScript` печатает образец сгенерированного скрипта.

## Гибридный конструктор (OpenCode-среда)

- **Визуальный режим**: «Добавить блок» → цепочка узлов (события → действия → условия).
  Тап по узлу — параметры, long-press — удаление, drag — позиция.
- **Текстовый режим**: тот же код открыт в редакторе с подсветкой (`gs_*` API).
- Синхронизация двусторонняя через регионы `// >>> block:N` … `// <<< block:N`:
  - «Блоки → код» — регенерация (ваш ручной текст вне регионов сохраняется);
  - «Код → блоки» — парсинг чистых регионов в параметры блоков; регионы с ручной
    правкой помечаются флагом `userEdited` и не перезаписываются.
- «Тест логики» — симуляция цепочки без запуска движка.

## 3D-превью

`vulkan_renderer.cpp` создаёт instance/device/swapchain/render pass и крутит
clear-loop (проверка GPU-совместимости, включая AMD Xclipse/RDNA). Следующий шаг —
загрузка геометрии BSP в pipeline (точки расширения обозначены в коде).
