# Plan de arquitectura — TD-030 + TD-033

## Contexto

`MasterViewModel` (774 líneas) es un god object que mezcla 7 responsabilidades:
estado de UI, lógica de negocio, acceso a Stores, coordinación con el Service,
seeding, historial de sesiones y beep picker. El dominio (`model/`) ya está
aislado y testeado, pero la capa de presentación no sigue un patrón claro.

## TDs

- **TD-030 — Koin DI** (Fase 1)
- **TD-033 — Repository + MVI + Navigation + Testing** (Fases 2-5)

## Skills de referencia

Instaladas en `.devin/skills/` (extraídas de `skills.zip`):

- **android-presentation-mvi** — State/Action/Event, ViewModel con StateFlow
- **android-data-layer** — Repository interfaces, data sources, fakes
- **android-testing** — Turbine, AssertK, UnconfinedTestDispatcher, fakes
- **android-di-koin** — Koin modules, `koinViewModel()`, scoping
- **android-module-structure** — No aplica (1 solo módulo, sin multi-módulo)
- **android-navigation** — Compose Navigation type-safe, SavedStateHandle
- **android-error-handling** — Result/DataError (parcial, sin red no aplica todo)

## Stack actual

- Kotlin 2.3.0, Compose BOM 2024.06, Material 3
- Sin DI, sin red, sin DB (SharedPreferences + JSON)
- Sin módulos Gradle (un solo módulo `app`)
- Sin navegación type-safe (flags en el ViewModel)
- ViewModel con `mutableStateOf` sueltos (no StateFlow)

## Plan por fases

Cada fase deja la app funcional y testeable. Se ejecuta una fase a la vez.

### Fase 1 — Koin DI

**Objetivo:** introducir inyección de dependencias para preparar el terreno
para Repository interfaces (Fase 2) y testing con fakes (Fase 5).

**Cambios:**
1. Agregar dependencias: `koin-android` + `koin-androidx-compose` (Koin 4.2.0)
2. Crear `MasterApp : Application` con `startKoin`
3. Módulo Koin: `WorkoutStore`, `SettingsStore`, `AlarmPlayer` como `single`
4. `MasterViewModel` y `SettingsViewModel` con dependencias inyectadas por constructor
5. `koinViewModel()` en `MainActivity` en vez de `viewModel()`
6. Registrar `MasterApp` en `AndroidManifest.xml`

**Archivos:** `app/build.gradle.kts`, `MasterApp.kt` (nuevo), `MasterViewModel.kt`,
`SettingsViewModel.kt`, `MainActivity.kt`, `AndroidManifest.xml`

**Verificación:** `verify-compile.ps1` + probar en dispositivo

### Fase 2 — Repository interfaces

**Objetivo:** separar interfaces de implementaciones para permitir testing con fakes.

**Cambios:**
1. `TrainingRepository` interface en `domain` (load, save, delete, move, duplicate)
2. `SessionRepository` interface en `domain` (load, add, delete, clear)
3. `SettingsRepository` interface en `domain` (load, save config)
4. `WorkoutStore` implementa `TrainingRepository` + `SessionRepository`
5. `SettingsStore` implementa `SettingsRepository`
6. Módulo Koin: bindings interface → implementación
7. ViewModel recibe interfaces por constructor (no implementaciones)

**Archivos:** `model/TrainingRepository.kt` (nuevo), `model/SessionRepository.kt` (nuevo),
`model/SettingsRepository.kt` (nuevo), `data/WorkoutStore.kt`, `data/SettingsStore.kt`,
`MasterViewModel.kt`, `SettingsViewModel.kt`

**Verificación:** `verify-compile.ps1` + probar en dispositivo

### Fase 3 — MVI

**Objetivo:** consolidar los 15+ `mutableStateOf` en un `State` data class
con `StateFlow`, definir `Action` sealed interface y `Event` para side effects.

**Cambios:**
1. `MasterState` data class (todos los campos de UI en uno solo)
2. `MasterAction` sealed interface (todas las acciones del usuario)
3. `MasterEvent` sealed interface (side effects: navegación, snackbar)
4. `StateFlow<MasterState>` + `Channel<MasterEvent>` en el ViewModel
5. `onAction(action: MasterAction)` en vez de métodos sueltos
6. Composables reciben `state` y `onAction` (no el ViewModel)
7. `SettingsState` + `SettingsAction` + `SettingsEvent` para SettingsViewModel

**Archivos:** `MasterViewModel.kt` (refactor grande), `SettingsViewModel.kt`,
todas las screens (`MasterScreen.kt`, `PlayerScreen.kt`, `HistoryScreen.kt`,
`SettingsScreen.kt`, editores)

**Verificación:** `verify-compile.ps1` + probar en dispositivo

### Fase 4 — Navigation

**Objetivo:** migrar los flags de navegación del ViewModel a rutas type-safe
con Compose Navigation, habilitar back button nativo y `SavedStateHandle`
para process death.

**Cambios:**
1. Rutas `@Serializable`: `TrainingsList`, `TrainingEditor`, `WorkoutEditor`,
   `ExerciseEditor`, `Player`, `History`, `ExerciseHistory`, `Settings`
2. `NavHost` en `MainActivity` con el grafo de navegación
3. Migrar `draft`, `editingWorkoutId`, `showingHistory`, etc. a rutas
4. `SavedStateHandle` para restaurar estado del player tras process death
   (reemplaza el restore manual con SharedPreferences)
5. Back button nativo del sistema

**Archivos:** `MainActivity.kt` (refactor grande), `MasterViewModel.kt` (quitar
flags de navegación), todas las screens (recibir callbacks de navegación)

**Verificación:** `verify-compile.ps1` + probar en dispositivo

### Fase 5 — Testing con Turbine + fakes

**Objetivo:** tests del ViewModel con el patrón de los skills (fakes, Turbine,
UnconfinedTestDispatcher).

**Cambios:**
1. `FakeTrainingRepository`, `FakeSessionRepository`, `FakeSettingsRepository`
2. Tests de `MasterViewModel` con Turbine (verificar emisión de estados)
3. Tests de acciones (OnDeleteTraining, OnSaveTraining, etc.)
4. Tests de events (NavigateToPlayer, etc.)
5. `Dispatchers.setMain(UnconfinedTestDispatcher())` en setup

**Archivos:** `test/.../MasterViewModelTest.kt` (nuevo),
`test/.../FakeTrainingRepository.kt` (nuevo), etc.

**Verificación:** `run-tests.ps1` + `verify-compile.ps1`

## Orden de ejecución

1 → 2 → 3 → 4 → 5

Cada fase es un commit atómico (o pocos). No se avanza a la siguiente
hasta que la anterior esté aprobada en dispositivo.

## Lo que no se hace

- **Multi-módulo Gradle** — 1 sola feature, no justifica
- **Ktor** — sin red
- **Room** — SharedPreferences + JSON es suficiente
- **Version catalog** — el proyecto es pequeño, deps inline es ok
- **Koin compiler plugin** — sin `@KoinViewModel`, modules manuales son suficientes
