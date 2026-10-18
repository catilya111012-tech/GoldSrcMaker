# AGENTS.md — контекст проекта для будущих сессий

Этот файл opencode читает автоматически. Здесь: что за проект, как проверять,
что уже сделано и что делать дальше. Пользователь общается по-русски.

## Обязанности каждой сессии

1. **Вести лог диалога** в `docs/SESSION_LOG.md`: после каждой пары
   «сообщение пользователя ↔ твой текстовый ответ» дописывай в конец файла
   запись. В лог попадают **только** текстовые сообщения пользователя и твои
   текстовые ответы — без вызовов инструментов, вывода команд и рассуждений.
   Язык — русский, ответы можно сжато (суть).
2. **Не коммитить и не пушить без явной просьбы пользователя.**

## Проект

GoldSrc Mobile Game Maker — Android-приложение: 2D-редактор карт (браши,
энтити), гибридный конструктор блоков/кода (граф ⇄ текст), компиляция
`.map → .bsp`, запуск карт в Xash3D FWGS, Vulkan 3D-превью.

- Папка: `~/GoldSrcMaker`, git, remote `github.com/catilya111012-tech/GoldSrcMaker.git`
- Java (plain `android.app.Activity`), minSdk 26 / targetSdk 34 / compileSdk 34,
  NDK `25.2.9519653`, abiFilters arm64-v8a, armeabi-v7a, x86_64
- Файлы Valve не распространяются (Data/Code Separation), компиляторы карт —
  сторонние GPL-утилиты (VHLT / ericw-tools), подкладываются пользователем

## Проверка (локально, без Android SDK)

```bash
bash tools/selftest/run.sh    # JDK 17; ждём «SELFTEST OK» и все [PASS]
```

Селфтест компилирует **только чистые Java-классы** (без `android.*`).
Android-зависимые файлы локально не проверяются — их собирает CI
(GitHub Actions). Будь аккуратен с правками таких файлов.

C++: NDK локально нет, но есть трюк — стабы `jni.h`, `android/log.h`,
`android/native_window*.h` лежат в `$PREFIX/tmp/opencode/vkcheck` (vulkan-headers
установлены), проверка:

```bash
clang++ -std=c++17 -fsyntax-only -Wall -Wextra \
  -I $PREFIX/tmp/opencode/vkcheck -I $PREFIX/include \
  app/src/main/cpp/vulkan_renderer.cpp
```

Шейдеры: правь `app/src/main/cpp/shaders/triangle.{vert,frag}`, затем
`bash tools/shaders/build.sh` (нужен `pkg install glslang`) — обновит
`app/src/main/cpp/spirv_shaders.h` (он коммитится, чтобы CI не требовал glslang).

## Ключевые файлы

- `app/src/main/java/com/goldsrc/maker/MainActivity.java` — лаунчер: проекты,
  проверка ресурсов, импорт SAF, компиляция, запуск игры
- `.../editor/EditorActivity.java` — хаб: 2D / Блоки / Код / 3D
- `.../editor/BrushCanvasView.java` — 2D-полотно: браши, энтити, выделение, drag
- `.../editor/EntityCatalog.java` — каталог классов энтити (поля/defaults)
- `.../editor/TexturesActivity.java` + `PixelCanvasView.java` + `PaletteView.java`
  — редактор текстур (палитровое рисование)
- `.../editor/ModelEditorActivity.java` — редактор моделей (части-коробки)
- `.../core/TextureImage.java` / `TextureStore.java` / `WadReader.java` /
  `WadWriter.java` — текстуры: модель, хранилище `textures.wad`, чтение/запись
- `.../core/ModelData.java` / `ModelStore.java` / `MdlWriter.java` — модели:
  данные, JSON-хранилище, экспорт studio v10 `.mdl`
- `.../scripting/HybridScriptEngine.java` + `ScriptSimulator.java` — логика
- `.../compiler/MapCompiler.java` + `CompilerToolchain.java` — пайплайн
  CSG→BSP→VIS→RAD, поиск/проверка запуска бинарников
- `.../launcher/GameLauncher.java` — запуск `su.xash.engine.XashActivity`
  с extras `basedir`/`gamedir`/`argv` (+ проверки)
- `.../render/GeometryBuilder.java` — браши/энтити → вершинные данные
- `app/src/main/cpp/vulkan_renderer.cpp` — Vulkan: swapchain, depth, pipeline
- `app/src/main/cpp/native_bridge.cpp` — fork/exec стадий компиляции
- `tools/selftest/run.sh` — селфтест, лог диалога `docs/SESSION_LOG.md`

## Статус на 2026-10-18

- Пять направлений улучшений завершены: надёжный запуск Xash3D (прямой
  `XashActivity`), новые блоки + симулятор, SAF-импорт компиляторов с проверкой
  запуска и живым прогрессом, редактор энтити (тап → свойства, drag),
  Vulkan (SPIR-V шейдеры, depth, MVP, геометрия карты).
- **Редактор текстур**: палитровое рисование (PixelCanvasView/PaletteView,
  TexturesActivity), хранилище `projects/<имя>/textures.wad` (TextureStore,
  WadWriter с мипами/палитрой), импорт из halflife.wad, экспорт: отдельный WAD
  или патч halflife.wad с `.bak`. При компиляции/экспорте `.map` список WAD
  добавляется в worldspawn (`halflife.wad;<проект>/textures.wad`).
- **Редактор моделей**: ModelData/ModelStore (JSON, `models/*.model.json`),
  ModelEditorActivity (части-коробки), Vulkan-превью модели (MODE_MODEL),
  экспорт `.mdl` (MdlWriter, studio v10: 1 кость, 1 анимация, float-вершины,
  мип-текстуры, палитра пересчитывается под halflife.wad).
- Селфтест: **221 проверка, всё зелёное** (добавлены WadWriterTest,
  TextureStoreTest, ModelStoreTest, MdlWriterTest, тесты модели в GeometryTest;
  в org/json-шиме появился настоящий JSON-парсер — файловые roundtrip теперь
  тестируются).
- **Закоммичено и запушено**: локальный коммит `1aa9144` (обёртка gradlew,
  старый CI) сохранён в ветке `backup/local-before-sync-20261018`; `main`
  приведён к origin и поверх него — коммит со всеми изменениями.
- CI — `build.yml` пользователя (workflow «Build APK»): **только
  `workflow_dispatch`** (пуши сборку не запускают), NDK 25.2.9519653,
  Gradle 8.9 через `gradle/gradle-build-action@v3`; `./gradlew` в репозитории
  есть, но CI не использует. Запуск вручную: `gh workflow run build.yml`,
  следить: `gh run watch`, артефакт: `gh run download <id>`.

## Что делать дальше (идеи)

- Коммит/пуш — только по явной команде пользователя (см. обязанности выше).
- Android-файлы (TexturesActivity, ModelEditorActivity, PixelCanvasView,
  PaletteView, правки MainActivity/EditorActivity/Manifest) локально не
  компилируются — CI соберёт; при правках быть аккуратным с API.
- 3D: текстуры WAD в 3D-превью вместо однотонных цветов, парсинг готового
  `.bsp`, свободная камера.
- Модели: несколько текстур/групп в .mdl, `env_sprite`-спрайты, анимация
  (несколько кадров) — сейчас только статика.
- Компиляторы: собрать порты VHLT под arm64 и положить в `jniLibs/` —
  единственный путь, гарантированно обходящий W^X.
- Проверка лаунчера и редакторов на живом устройстве/эмуляторе с Xash3D FWGS.
- EntityCatalog: больше классов/полей; brush-энтити (func_door, trigger_*).
