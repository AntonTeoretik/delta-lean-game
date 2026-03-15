# Delta Lean Game

Экспериментальная среда для визуального программирования и доказательств на Lean.

Проект исследует идею представления программ и доказательств Lean в виде **интерактивного визуального мира**, где элементы языка (теоремы, функции, типы) представлены как объекты на 2D-плоскости. Пользователь взаимодействует с ними через графический интерфейс: создаёт сущности, редактирует их, перемещает по сцене и наблюдает результаты проверки Lean в реальном времени.

Основная цель проекта — создать среду, в которой изучение формальных доказательств и функционального программирования становится более наглядным, интерактивным и исследовательским.

Проект разделён на два больших компонента:

- Frontend — визуализация мира и пользовательский интерфейс.
- Backend — управление проектом Lean, взаимодействие с Lean Language Server и логика приложения.

Frontend намеренно остаётся относительно простым. Основная логика системы находится в backend.

---

# Основная идея

Lean — это язык программирования и доказательств, в котором код проверяется формальной системой типов.

Обычно пользователь взаимодействует с Lean через текстовый редактор. В этом проекте предлагается другая модель:

- каждое определение Lean (например `def`, `theorem`, `inductive`) представляется **узлом (node)**;
- узлы размещаются в **визуальном мире**;
- пользователь может перемещать их, создавать новые, открывать редактор кода;
- backend преобразует состояние мира обратно в Lean-код;
- Lean проверяет код и возвращает diagnostics;
- frontend визуализирует результат проверки.

Таким образом пользователь взаимодействует не напрямую с текстом файла, а с **визуальной моделью программы**, которая синхронизируется с Lean.

---

# Архитектурный обзор

Система состоит из нескольких слоёв:

    Frontend
       │
       │ HTTP (REST)
       │
    Backend API
       │
    Application / Session Layer
       │
    Lean Integration Layer
       │
    Lean Language Server

Дополнительно backend работает с файловой системой Lean-проекта.

    Workspace Filesystem
           │
    Workspace Layer
           │
    Application Layer

Каждый слой имеет отдельную ответственность.

---

# Структура репозитория

    delta-lean-game
    │
    ├ backend
    │
    │  ├ modules
    │  │
    │  │  ├ app
    │  │  ├ domain
    │  │  ├ workspace
    │  │  ├ lean-adapter
    │  │  └ transport
    │
    ├ frontend
    │
    ├ docs
    │
    ├ protocol
    │
    ├ sample-workspaces
    │
    └ scripts

---

# Технологический стек

Текущий целевой стек проекта:

- **Backend:** Kotlin, Ktor, Kotlinx Serialization, Kotlin Coroutines, Gradle (multi-module)
- **Интеграция с Lean:** Lean Language Server (`lake env lean --server`), LSP, JSON-RPC 2.0
- **Frontend:** React + TypeScript + Vite + PixiJS

Этот стек выбран для быстрого итеративного прототипирования: backend отвечает за сессии и интеграцию с Lean, а frontend — за интерактивный 2D-интерфейс.

---

# Backend

Backend написан на **Kotlin** и использует **Gradle multi-module architecture**.

Каждый модуль выполняет отдельную роль.

    backend
     ├ build.gradle.kts
     ├ settings.gradle.kts
     └ modules
        ├ app
        ├ domain
        ├ workspace
        ├ lean-adapter
        └ transport

Архитектура backend построена так, чтобы максимально изолировать разные уровни системы.

---

# Модуль domain

`domain` — это инфраструктурно-независимая модель мира.

Сейчас модуль содержит только чистые структуры данных:

- `WorldSnapshot`
- `WorldFile`
- `WorldItem`
- `ItemKind`
- `ItemStatus`
- `TextPosition` / `TextRange`
- `Diagnostic`
- `ItemLayout`

`domain` не зависит от HTTP, Lean LSP, файловой системы и DTO transport-слоя.

---

# Модуль workspace

`workspace` отвечает за работу с **Lean-проектом на диске**.

Его задачи:

- сканирование workspace и загрузка `.lean` файлов в `WorldSnapshot` (`WorkspaceLoader`)
- разбиение Lean-файла на `imports + items` (`LeanFileSplitter`)
- детерминированная сборка `WorldFile -> text + itemRanges` (`FileAssembler`)
- маппинг diagnostics на item-ы (`DiagnosticMapper` + `DiagnosticService`)
- хранение in-memory сессии мира (`WorkspaceSession`)

Этот модуль не знает ничего о HTTP или frontend.

---

# Модуль lean-adapter

`lean-adapter` отвечает за **интеграцию с Lean Language Server**.

Основные задачи:

- запуск Lean процесса (`lake env lean --server`)
- реализация JSON-RPC транспорта
- отправка сообщений LSP
- получение diagnostics
- управление документами Lean

Этот модуль является адаптером между:

- доменной моделью приложения
- Lean LSP протоколом

---

# Модуль transport

`transport` отвечает за **коммуникацию между backend и frontend**.

Он содержит:

- DTO объекты
- маппинг domain → API
- сериализацию JSON

На текущем этапе realtime-каналы (SSE/WebSocket) не используются.

Этот слой нужен, чтобы frontend не зависел напрямую от доменной модели.

---

# Модуль app

`app` — точка входа backend.

Он отвечает за:

- запуск HTTP сервера
- регистрацию API маршрутов
- создание зависимостей
- загрузку конфигурации
- запуск и координацию workspace/Lean session

Текущие основные endpoint-ы:

- `POST /api/workspace/open`
- `GET /api/world`
- `PUT /api/items/{id}/code`
- `GET /api/files`, `GET/PUT /api/files/{path...}` (legacy/MVP-совместимость)
- `GET /api/diagnostics` (legacy/MVP-совместимость)

Именно здесь соединяются все модули системы.

---

# Frontend

Frontend реализован на **TypeScript** и отвечает только за визуальную часть.

Основные задачи:

- рендеринг 2D мира
- отображение `WorldItem` как узлов
- перемещение узлов на сцене
- редактирование `item.code` через простой textarea
- сохранение через `PUT /api/items/{id}/code`
- отображение `item.status` и `item.diagnostics`

Frontend не содержит сложной бизнес-логики.

На текущем этапе обновления diagnostics в UI происходят через периодический refresh world snapshot (без push-нотификаций).

---

# Sample Workspaces

Директория `sample-workspaces` содержит минимальные Lean проекты, используемые для разработки и тестирования.

Пример:

    sample-workspaces
      └ tiny
          ├ lakefile.toml
          ├ lean-toolchain
          └ Main.lean

Эти проекты используются backend для тестирования импорта workspace.

---

# Документация

Папка `docs` предназначена для архитектурной документации.

В будущем она может содержать:

- описание архитектуры
- описание API
- описание модели данных
- описание Lean интеграции

---

# Protocol

Папка `protocol` предназначена для описания протокола взаимодействия frontend и backend.

Здесь могут находиться:

- JSON схемы
- примеры сообщений
- описание API

---

# Scripts

Папка `scripts` содержит утилиты для разработки.

Например:

- запуск backend
- запуск frontend
- запуск тестового workspace

---

# Статус проекта

Проект находится на стадии активного MVP.

Основные задачи текущего этапа:

- session-based world модель в backend
- split/assemble pipeline для Lean файлов
- редактирование `WorldItem` с записью на диск
- интеграция Lean diagnostics -> item status/diagnostics
- item-oriented frontend (Pixi сцена + редактор)

В дальнейшем возможны эксперименты с:

- образовательными сценариями
- визуальными proof tactics
- игровыми механиками обучения

---
