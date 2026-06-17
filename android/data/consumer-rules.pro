# Keep Moshi reflection-based DTO classes used by the DataLayer bridge
-keep class com.secondserve.data.wearable.dto.** { *; }
-keepclassmembers class com.secondserve.data.wearable.dto.** { *; }

# Keep DataLayerListener so the manifest entry resolves after R8 renaming
-keep class com.secondserve.data.wearable.DataLayerListener { *; }
-keep interface com.secondserve.data.wearable.DataLayerListener$DataLayerListenerEntryPoint { *; }
