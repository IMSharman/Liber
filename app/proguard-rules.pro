# Keep Kotlinx Serialization classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

# Keep Room entities
-keep class com.imsharman.liber.data.local.entity.** { *; }

# Keep PdfBox & Junrar warnings quiet
-dontwarn org.apache.commons.logging.**
-dontwarn java.awt.**
