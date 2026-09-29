# Retrofit/Gson: the bridge DTOs are reflected over by Gson, so their field
# names have to survive minification or every response decodes to nulls.
-keepclassmembers class com.lumisound.android.bridge.model.** { <fields>; }
-keep class com.lumisound.android.bridge.model.** { *; }
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
# The gallery store persists its settings and photo list through Gson too.
-keep class com.lumisound.android.gallery.GalleryPhoto { *; }
-keep class com.lumisound.android.gallery.GalleryBackgroundStore$StoredSettings { *; }
