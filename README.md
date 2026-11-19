# java-serialization-kotlinx

Reads what `java.io.ObjectOutputStream` writes, without the JVM, and decodes it into `@Serializable`
classes with a kotlinx.serialization format. Kotlin Multiplatform (JVM and Android so far), all of
it common code, over Okio. Decoding only.

## Setup

It's published with [JitPack](https://jitpack.io/#Reco1I/java-serialization-kotlinx):

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.Reco1I:java-serialization-kotlinx:0.1.0")
}
```

## Usage

Decode a stream into `@Serializable` classes, each property from the Java field with its serial name:

```kotlin
@Serializable
class Point(val x: Int, val y: Int, val label: String? = null)

val point = JavaSerialization.decodeFromByteArray<Point>(bytes)
val other = JavaSerialization.decodeFromSource<Point>(source)
```

Or read a stream as `ObjectInputStream` does, primitives and objects in the order they were written:

```kotlin
JavaObjectInputStream(source).use { input ->
    val version = input.readInt()
    val tags = input.decodeObject<List<String>>()
    val raw = input.readObject() // A JavaObject, JavaEnum, JavaArray, JavaClass, String or null.
}
```

How values are decoded:

- **Classes** from Java objects: each property from the field with its serial name (rename it with
  `@SerialName`). Fields the class doesn't have are ignored, and a missing property takes its
  default value, or fails if it has none.
- **Primitives** from Java primitives of the same type, or from their boxes (`java.lang.Integer`…).
  **Strings** from Java strings, or from enum constants as their names.
- **Enums** by the constant's name.
- **Lists, sets and arrays** from Java arrays and the JDK's collections: `ArrayList`, `LinkedList`,
  `ArrayDeque`, `Vector`, `HashSet`, `LinkedHashSet`, `TreeSet`, `EnumSet`, `PriorityQueue`,
  `Arrays.asList`, `List.of`/`Set.of` and the `Collections` wrappers.
- **Maps** from `HashMap`, `LinkedHashMap`, `TreeMap`, `Hashtable`, `EnumMap`, `IdentityHashMap`,
  `ConcurrentHashMap`, `Map.of` and the `Collections` wrappers.
- **Polymorphic values** by their Java class name as serial name:
  `@SerialName("com.example.Shape\$Circle")`.

Mapping errors throw a `SerializationException`, errors of the stream a
`JavaSerializationException`. Dynamic proxies, `Externalizable` objects written with the stream
protocol version 1 and serialized exceptions aren't supported.

## Building

```bash
./gradlew jvmTest                                  # The tests, and checkCommonSources
./gradlew publishAllPublicationsToBuildRepository  # What's published, in build/repository
```

## License

Apache License 2.0, see [LICENSE](LICENSE).
