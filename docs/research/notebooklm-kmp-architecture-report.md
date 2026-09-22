<!-- Згенеровано Google NotebookLM 2026-09-17 з ноутбука 6c22014f-6f99-4f7d-a0d7-aaab57fb6d29 ("KMP Source Code Architecture — Mobile DAW (Android + iOS)", 92 джерел). Українська версія; англійська — notebooklm-kmp-architecture-report-en.md. Номери версій бібліотек у тексті застарілі, актуальні версії див. kmp-reference-repos.md. Інженерне рішення — у kmp-code-architecture.md. -->

# Архітектура вихідного коду Kotlin Multiplatform для мобільної DAW (Android + iOS)

Дана архітектурна специфікація призначена для проектування мобільних цифрових аудіо-станцій (DAW). Проектування таких систем вимагає гармонізації двох протилежних парадигм: детермінованого native C++ DSP-рушія з нульовою алокацією пам'яті та реактивного UI-фреймворку на базі Compose Multiplatform (CMP) [cite: 1].

## 1. Стислий підсумок рекомендацій

Для забезпечення низької затримки (low latency) та стабільності Compose Multiplatform на iOS, обрано наступний стек [cite: 1, 33]:

| Предмет (Concern) | Рекомендація | Альтернатива (Runner-up) | Чому саме це (Why) |
| :--- | :--- | :--- | :--- |
| **DI** | **Metro** | kotlin-inject | Пряма робота з Kotlin IR, швидкість ініціалізації $O(1)$, нульовий runtime-overhead для low-latency задач [cite: 21, 22]. |
| **Навігація** | **Jetpack Navigation 3** | Decompose | Типізована навігація через `SnapshotStateList<NavKey>`, що гарантує стабільність стану інтерфейсу [cite: 11, 20]. |
| **Persistence** | **Room KMP** | SQLDelight 2.x | `BundledSQLiteDriver` забезпечує ідентичну версію SQLite та поведінку на Android та iOS [cite: 8, 27]. |
| **State Management** | **MVVM (androidx.lifecycle)** | Orbit MVI | Стандартна підтримка у `commonMain` та ідіоматична інтеграція зі Swift через SKIE [cite: 2, 13, 15]. |

---

## 2. Структура модулів Gradle та налаштування збірки

Архітектура базується на декуперованій багатомодульній топології, що керується через `convention plugins` та `libs.versions.toml` [cite: 4, 5].

### Топологія модулів (Module Graph Topology)
*   `:core:audio-engine`: C++ DSP код, JNI (Android) та `cinterop` (iOS). Містить lock-free структури та C ABI міст [cite: 1, 6, 29].
*   `:core:domain`: Чисті бізнес-правила та сутності (AudioTrack, AudioClip) без залежностей від UI [cite: 10].
*   `:core:data`: Persistence (Room Apache 2.0), серіалізація (`kotlinx.serialization` Apache 2.0) та I/O (`kotlinx-io` Apache 2.0) [cite: 8, 9, 10].
*   `:core:ui`: Система дизайну, теми та кастомні аудіо-віджети (фейдери, енкодери) [cite: 10, 11].

### Налаштування iOS Umbrella Framework та XCFramework
Для уникнення дублювання символів та роздуття бінарного файлу на iOS, всі модулі агрегуються в єдиний Umbrella модуль (`:shared:umbrella`) [cite: 4, 8].

**Технічні вимоги:**
1.  **Статична лінківка:** Встановіть `isStatic = true` для бінарних файлів фреймворку [cite: 4, 8].
2.  **XCFramework конфігурація:** Використовуйте `XCFramework()` для збірки. Кожен iOS таргет повинен бути доданий через `xcf.add(this)` у блоці конфігурації фреймворку [cite: 8].
3.  **Автоматизація:** Gradle автоматично реєструє таски `assembleXCFramework`, а також `assemble[Name]DebugXCFramework` для Xcode-інтеграції [cite: 8].

---

## 3. Архітектура шару презентації (Presentation Architecture)

### Матриця порівняння паттернів
| Паттерн | Парадигма | Ергономіка iOS (Swift) | Придатність для DAW |
| :--- | :--- | :--- | :--- |
| **MVVM (androidx)** | UDF | **Дуже висока** (через SKIE) | **Найвища** (стабільність та життєвий цикл) [cite: 13, 15] |
| **Decompose** | Component Trees | Висока | Висока (складна ієрархія вікон) [cite: 11] |
| **Orbit MVI** | Redux MVI | Середня | Висока (аудит транспортних команд) [cite: 11] |
| **Circuit** | Presenter/UI | Низька | Середня (орієнтовано на контентні аплікації) [cite: 11] |

### ViewModel та інтеграція зі Swift
Рекомендується використовувати `androidx.lifecycle.ViewModel` (Apache 2.0) у `commonMain` [cite: 13, 17]. Завдяки SKIE (MIT License), Kotlin `StateFlow` автоматично трансформується у Swift `AsyncSequence`, а запечатані класи (sealed classes) — у Swift `enums` з асоційованими значеннями, що забезпечує ідіоматичність Swift-коду [cite: 10, 14, 15].

### Оптимізація телеметрії (60-120 Гц)
Обробка плейхеда (playhead) та рівнів гучності (meters) вимагає стратегій уникнення "recomposition storms" [cite: 1, 30]:
*   **`derivedStateOf`**: Використовувати виключно для обмеження рекомпозиції на **межах порогів** (threshold boundaries), наприклад, зміна кольору індикатора при переході через 0 dB [cite: 31].
*   **`DrawScope`**: Читання стану безпосередньо у фазі малювання на Canvas, оминаючи фази Composition та Layout [cite: 30, 31].
*   **`graphicsLayer`**: GPU-оптимізація для трансформацій (переміщення плейхеда), що не викликає повного циклу оновлення вузла [cite: 31].

---

## 4. Система навігації

**Jetpack Navigation 3 (Nav3)** (Apache 2.0) базується на `SnapshotStateList<NavKey>`, де кожна операція — це мутація стандартного списку [cite: 11, 19, 20].

*   **Адаптивний лейаут:** Реалізується через `ListDetailSceneStrategy`. Механізм використовує екземпляри `SceneStrategy` для автоматичного перемикання між однопанельним (Bottom Bar) та двопанельним (Navigation Rail) режимами залежно від **класів розміру вікна** (Window Size Classes) [cite: 11].
*   **Альтернативи:** Decompose (MIT License) є найкращим варіантом, якщо проект вимагає повної ізоляції компонентів та керування нативними ViewControllers на iOS [cite: 11].

---

## 5. Впровадження залежностей (Dependency Injection)

| Метрика | Metro | kotlin-inject (Apache 2.0) | Koin (Apache 2.0) |
| :--- | :--- | :--- | :--- |
| **Метод** | Compiler Plugin (IR) | KSP Code Gen | Runtime Service Locator |
| **Runtime Overhead** | **Нульовий (O(1))** | Нульовий (O(1)) | Low-Medium (Map lookups) |
| **Build-time Impact** | Мінімальний | Середній | **Нульовий** |
| **Reflection** | Відсутня | Відсутня | Присутня (опціонально) |

**Рекомендація:** Metro є пріоритетним для DAW, оскільки працює на рівні проміжного представлення (IR) Kotlin, гарантуючи безпеку компіляції без накладних витрат KSP та нульовий overhead під час виконання [cite: 21, 22].

---

## 6. Збереження даних (Persistence)

### Room KMP та SQLite
Використання **Room KMP** (Apache 2.0) з `BundledSQLiteDriver` є критичним для DAW. Це дозволяє уникнути проблем із різними версіями SQLite на iOS та Android, забезпечуючи детерміновану роботу бази даних [cite: 8, 25, 27].

### Формат проектів та I/O
*   **Serialization:** `kotlinx.serialization` (Apache 2.0) для JSON або бінарних маніфестів [cite: 9, 10].
*   **System I/O:** `kotlinx-io` (Apache 2.0) або Okio (Apache 2.0) для кросплатформного доступу до файлової системи [cite: 9, 10].
*   **Undo/Redo:** Реалізується через стек імутабельних моделей станів проекту (`data class`) з використанням `ArrayDeque` (double-ended queue) для ефективного керування історією змін [cite: 1, 9].

---

## 7. Конкурентність та багатопотоковість

Нова модель пам'яті Kotlin/Native дозволяє ділитися станом між потоками без "freezing" [cite: 3, 28].
*   **`Dispatchers.Main`**: Взаємодія з UI (Android Looper / iOS GCD через Skiko) [cite: 1, 13].
*   **`Dispatchers.IO`**: Стрімінг аудіо-файлів та робота з БД [cite: 8].
*   **Polling:** Використання фонового опитування (polling) для синхронізації візуальних індикаторів з даними C++ рушія, щоб не блокувати Main потік високою частотою оновлень [cite: 8].

---

## 8. Паттерни Native Interop для C++ рушія

Зв'язок базується на "C ABI facade" для забезпечення максимальної швидкодії [cite: 3, 29].
*   **Android:** JNI міст.
*   **iOS:** `cinterop` (.def файли) для генерації Kotlin-обгорток навколо C-заголовків [cite: 1, 6].
*   **SKIE (MIT License):** Критичний інструмент для DAW. Він конвертує `SharedFlow` у Swift-сумісні потоки, а `StateFlow` — у `AsyncSequence`, що дозволяє SwiftUI-екранам спостерігати за станом аудіо-рушія без посередників [cite: 10, 14, 15].

---

## 9. Стек тестування

*   **Логіка:** `kotlin.test` (Apache 2.0) для Unit-тестування в `commonMain` [cite: 32].
*   **Flow/State:** `Turbine` (Apache 2.0) від Cash App для валідації реактивних ланцюжків ViewModel [cite: 32].
*   **Screenshot Testing:** `Roborazzi` (Apache 2.0) для верифікації UI на JVM [cite: 32].
*   **Integration:** In-memory драйвери Room для тестування міграцій та запитів [cite: 8].

---

## 10. Уроки продуктової розробки та пастки (Pitfalls)

*   **Оптимізація iOS:** Обов'язкове використання `-Xbinary=strip=true` у налаштуваннях Kotlin/Native для видалення зайвих символів та зменшення розміру бінарного файлу [cite: 4, 8].
*   **Холодний старт:** Skiko може мати затримку ініціалізації. Рекомендується **пре-ініціалізація компонентів** аудіо-рушія у фоновому потоці під час відображення Splash-екрана [cite: 1].
*   **Real-time обмеження:** **Категорично заборонено** алокувати будь-які об'єкти Kotlin або викликати GC-залежний код у аудіо-коллбеках C++. Слід використовувати виключно **пре-алоковані C ring buffers** або `COpaquePointer` для передачі даних між DSP та Kotlin [cite: 1, 3].
*   **iOS UI Alignment:** Враховуйте нюанси нативного скролінгу в Compose; для елементів, де нативна інерція є критичною, використовуйте `UIKitView` [cite: 1, 33].

---

## 11. Відкриті питання

*   **Ліцензування кодеків:** Джерела не дають однозначної стратегії щодо легальної дистрибуції пропрієтарних аудіо-кодеків (MP3/AAC) у складі Open Source KMP проектів [cite: 26].
*   **Swift Export:** Вбудований Swift Export від JetBrains знаходиться у фазі активної розробки; SKIE залишається стандартом для стабільних продуктів до повної стабілізації нативного рішення [cite: 14].