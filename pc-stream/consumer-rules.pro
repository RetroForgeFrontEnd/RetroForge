# moonlight-core's JNI calls back into these by name.
-keep class com.limelight.nvstream.jni.** { *; }
-keep class com.limelight.binding.video.** { *; }
-keep class com.limelight.binding.audio.** { *; }
# Bouncy Castle's provider finds its algorithms (RSA keys for pairing with the PC) by class name.
-keep class org.bouncycastle.jcajce.provider.** { *; }
-keep class org.bouncycastle.jce.provider.** { *; }
