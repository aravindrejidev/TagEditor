# jaudiotagger instantiates frame bodies by class name (reflection) and references
# desktop-only classes (java.awt / javax.imageio) that do not exist on Android.
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-dontwarn java.beans.**
-dontwarn sun.**
