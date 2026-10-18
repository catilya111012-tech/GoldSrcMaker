# Компиляторы карт в jniLibs/arm64-v8a

Здесь лежат скомпилированные инструменты компиляции карт (GPL):

- `libhlcsg.so`, `libhlbsp.so`, `libhlvis.so`, `libhlrad.so` — из
  «Vluzacn's custom build of Zoner's Half Life Tools (ZHLT)», версия 34
  (`hl*sg v3.4 VL34`), автор vluzacn@163.com, лицензия GPL
  (`terms of use` в архиве исходников, http://www.zhlt.info).
- `libcxx_vhlt.so` — libc++ (LLVM, Apache-2.0 with LLVM exceptions)
  из Termux, используется как runtime для четырёх бинарников.

Файлы собраны в Termux для Android aarch64 и перелинкованы так, чтобы
работать внутри APK (jniLibs, обход W^X). Это исполняемые ELF (PIE,
интерпретатор `/system/bin/linker64`), упакованные с расширением `.so`,
как требует Android-упаковка нативных библиотек.

Локальные изменения относительно исходников VHLT v34:

1. `hlrad/compress.h`: в `vector_compress` сдвиг `>> (1 + max - exp)` мог
   достигать 128 — неопределённое поведение (падал compat-тест на
   clang/arm64). Заменён на детерминированный `bitshift_positive`:
   `s < 24 ? x >> s : 0` (на x86 значения совпадают с эталонными).
2. Сборка/постобработка: `make [USER_FLAGS=-fPIC] bin/hlrad`, затем
   `patchelf --replace-needed libc++_shared.so libcxx_vhlt.so` и
   `patchelf --remove-rpath`. Бинарник остаётся исполняемым PIE — линковать
   как `-shared` нельзя (на устройстве SIGILL).

Исходники VHLT доступны из апстрима (владелец прав — vluzacn); вместе с
APK распространяются бинарные копии по правилам GPL.
