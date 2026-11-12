# AnyELK

Android-приложение для управления BLE-светодиодными лентами **ELK-BLEDOB /
Lotus Lantern** (сервис `0000fff0-…`, характеристика `0000fff3-…`).

Материалы 3, Jetpack Compose, MVVM, Kotlin Coroutines/Flow, Media3 (ExoPlayer)
и продвинутый FFT-визуализатор музыки.

## Возможности

- **Управление** — цветовое колесо (HSV) в стиле MD3, вкл/выкл, яркость,
  пресеты из палитры Material 3, живой предпросмотр RGB.
- **Устройства** — скан BLE, статусы подключения, авто-подключение
  к последнему устройству.
- **Музыка** — выбор папки через SAF (`ACTION_OPEN_DOCUMENT_TREE`),
  плеер на Media3, FFT-визуализатор: низы → красный, середины → синий,
  верхи → зелёный; адаптивное сглаживание (asymmetric EMA + скользящий пик)
  вместо хаотичного мигания; отправка цветов на ленту с троттлингом ~30 FPS.
- **Настройки** — только автоматическое подключение и порядок пикселей
  (RGB, RBG, GRB, GBR, BRG, BGR → команда `7E 06 81 …`).

## Протокол (кадр 9 байт)

| Команда | Пакет |
|---|---|
| Питание вкл | `7E 07 04 FF 00 01 02 01 EF` |
| Питание выкл | `7E 07 04 00 00 00 02 01 EF` |
| Цвет RGB | `7E 07 05 03 RR GG BB 10 EF` |
| Яркость 0–100 | `7E 04 01 XX 01 FF 02 01 EF` |
| Порядок пикселей | `7E 06 81 P0 P1 P2 FF 00 EF` |
| Синхронизация времени | `7E 06 83 HH MM SS DOW 00 EF` |

Источники: [8none1/elk-bledob](https://github.com/8none1/elk-bledob),
[FergusInLondon/ELK-BLEDOM](https://github.com/FergusInLondon/ELK-BLEDOM),
декомпилированные Lotus Lamp X / Evozi Smart LED.

## Сборка

Локальная сборка не требуется — APK собираются в GitHub Actions
(`.github/workflows/build.yml`):

```bash
gh run download            # скачать артефакты последнего запуска
gh run watch               # следить за сборкой
```

Артефакт `AnyELK-apks` содержит `AnyELK-debug.apk` и `AnyELK-release.apk`
(release подписан CI-ключом, создаваемым в workflow).

## Структура

```
app/src/main/java/com/anyelk/led/
├── data/
│   ├── ble/        # протокол, сканер, коннект, очередь команд
│   ├── music/      # SAF-сканер, ExoPlayer, Visualizer/FFT
│   └── settings/   # DataStore
└── ui/
    ├── screens/    # Control, Devices, Music, Settings (MVVM)
    ├── navigation/ # нижняя навигация
    └── theme/      # Material 3 (dynamic color)
```

## Разрешения

- Bluetooth (scan/connect) — для работы с лентой;
- `RECORD_AUDIO` — только для FFT-визуализатора при включении
  «Отправлять цвета на ленту».
