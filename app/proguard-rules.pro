# AnyELK release rules.
# Compose, Media3 and Coroutines ship consumer rules; keep app model classes
# used via reflection (ViewModels are instantiated reflectively by androidx).
-keep class com.anyelk.led.** extends androidx.lifecycle.ViewModel { *; }
-keep class com.anyelk.led.ServiceLocator { *; }

-dontwarn org.jetbrains.annotations.**
