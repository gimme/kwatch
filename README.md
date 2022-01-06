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


## Basic Usage

### Listen to file changes
```kotlin
val file = Path.of("file.txt")

launch {
    file.onChange {
        println("file changed")
    }
}
```

### Watch directory events
```kotlin
val dir = Path.of("dir")

launch {
    dir.watch { event ->
        println("${event.path}: ${event.actions}") // Prints e.g. "dir/file.txt: [MODIFY]"
    }
}
```

## Advanced Usage

### Stopping
When you want to stop the watcher, you simply cancel the job.

```kotlin
val job = launch {
    dir.watch {}
}

job.cancel()
```

### Catch file changes during initialization

After launching a non-blocking coroutine, there is inherently a chance that the file was modified before the listener
starts. To solve this, you can supply the last known "modified time" which makes sure that the onChange-block gets run
if the modified time has changed.

```kotlin
val file = Path.of("file.txt")
val lastModified = file.getLastModifiedTime()

launch {
    file.onChange(lastModified = lastModified) {
        println("file changed")
    }
}
```

For watching directories, an event always fires right away with the `INIT` action which allows you to perform any
initialization logic.
