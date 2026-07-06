# Strict Starsector Modding Guardrails

## 1. Language & Runtime Constraints (Java & Kotlin)
- **Java Compatibility**: Target Java 7 bytecode strictly (or Java 8 if targeting the unofficial community JRE runtime update).
- **Java Restraints**: Do NOT use modern Java features (no `var` keywords, no advanced streams, no diamond operators if targeting base Java 7).
- **Kotlin Target**: Always compile Kotlin with `jvmTarget = "1.6"` or `"1.8"` depending on the target game JRE runtime. 
- **Kotlin Standard Library**: Ensure the correct lightweight Kotlin runtime `.jar` (matching the target JRE) is explicitly bundled inside the mod's `/jars` folder.
- **Dependencies**: Never introduce external dependencies or third-party libraries unless they are explicitly placed in the `/jars` folder.

## 2. Memory & Lifecycles
- **Memory Leaks**: Do NOT save references to `ShipAPI`, `CombatEngineAPI`, `EveryFrameCombatPlugin`, or `SectorEntityToken` inside long-lived static variables, singletons, or persistent scripts. They cause massive memory leaks when reloading saves or switching combat sessions.
- **Transience (Java)**: Mark all complex runtime objects that shouldn't be serialized to the `campaign.xml` save file as `transient`.
- **Transience (Kotlin)**: Use the `@Transient` annotation on fields/properties that contain runtime game state data to prevent catastrophic save game corruption.

## 3. Data Integrity & Concurrency
- **Immutable Operations**: Never mutate the game's core data tables, specs, or engine lists directly. Always copy the data structure first.
- **Concurrent Modification**: Always work on local copies of lists or sets (e.g., `new ArrayList<>(engine.getShips())` or `.toList()` in Kotlin) coming from the Starsector API. Modifying or iterating raw engine collections directly causes immediate `ConcurrentModificationException` or `NullPointerException`.
- **Configuration**: Modifying `mod_info.json` must preserve strict JSON formatting without comments.

## 4. Starsector API Null Safety & Architecture
- **Unpredictable Nullability**: Always assume that any object, token, or reference returned directly from the Starsector API can dynamically become `null` for no obvious or apparent reason (e.g., entity destruction, off-map de-spawning).
- **Kotlin Type Interoperability**: Starsector's underlying Java API lacks `@Nullable` / `@NonNull` annotations. Kotlin interprets these as Platform Types (`Type!`).
- **Strict Boundary Rules**: 
  1. **Starsector API Layer**: Never make functions accept non-nullable types (`Type`) if they interface **directly** with incoming data from the Starsector API. Always declare them as nullable (`Type?`). 
  2. **Internal Mod Architecture**: Methods working entirely within **our own custom mod code** that do not directly involve receiving raw parameters from the Starsector API must strictly prefer using non-nullable types (`Type`).
  3. **Adaptive Optional Type Resolution (Our Code Only)**: If our own internal method needs to account for an absent or conditional state, do NOT use a nullable return type (`Type?`). Instead, utilize a non-nullable wrapper type. Choose the package dynamically using this hierarchy:
     - **Project-Specific Custom Optional**: First, look up workspace symbols (LSP) to check if the active project defines its own custom `Optional` class within its local package tree (e.g., `exoticatechnologies.util.datastructures.Optional` or equivalent). If found, you **MUST** import and use it.
     - **Standard Fallback**: If the local project does not define a custom `Optional` class, fall back to standard `java.util.Optional`.
  4. **Intended Tri-State Semantics**: Use this structural separation to explicitly differentiate execution paths:
     - **`null`**: A raw, volatile, unsafe value coming straight from the Starsector engine API layer.
     - **`Optional.empty()`**: A clean, non-null signal from our custom systems indicating data is intentionally absent.
     - **`Optional.of(...)`**: A clean, non-null container holding our validated data.
  Use raw nullable return types (`Type?`) inside our custom code **VERY SPARINGLY**.

- **Strict Null Handling Constructs**:
  1. **Safe Calls**: Use safe calls (`?.`) and `?.let { ... }` blocks freely to safely scope operations on nullable instances.
  2. **No Unsafe Assertions**: Never use the double-bang operator (`variable!!`), **unless** it is placed directly inside an explicit `if (variable != null) { ... }` null check block where safety is fully guaranteed.
  3. **No Elvis Operators**: Do NOT use the Elvis operator (`?:`) for fallbacks. Instead, always opt for the explicit `variable = if (value != null) { value } else { fallbackValue }` block construct to maximize readability and precise control over execution branches.
