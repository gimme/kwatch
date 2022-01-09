# kwatch

Simple Kotlin library for listening to changes in a file or directory.


## Installation

Add [JitPack](https://jitpack.io/) to the list of repositories:
```kotlin
repositories {
    maven("https://jitpack.io")
}
```
Then add the dependency:
```kotlin
dependencies {
    implementation("com.github.gimme:kwatch:1.0.0")
}
```


## Example Usage

### Listen to file changes
```kotlin
val file = Path.of("file.txt")

file.onChange {
    println("file changed")
}
```

### Watch for directory events
```kotlin
val dir = Path.of("dir")

dir.watch { event ->
    println("${event.path}: ${event.actions}") // Prints e.g. "dir/file.txt: [MODIFY]"
}
```

### Cancellation
When you want to stop the listener, you simply call cancel on the returned object.

```kotlin
val listener = dir.watch {}

listener.cancel()
```
