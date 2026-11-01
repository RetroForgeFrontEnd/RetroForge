# Network drives (smbj): Kerberos sign-in (org.ietf.jgss) and its event bus's expression filters (javax.el)
# aren't on Android and are never used; RetroForge signs in with a user name and password, or as a guest.
-dontwarn org.ietf.jgss.**
-dontwarn javax.el.**
-dontwarn java.rmi.UnmarshalException

# Steam sign-in (pc-player): JavaSteam's encryption goes through SpongyCastle's provider, which finds its algorithms
# by class name.
-keep class org.spongycastle.jcajce.provider.** { *; }
-keep class org.spongycastle.jce.provider.** { *; }
# Its LDAP certificate store (javax.naming) isn't on Android and is never used.
-dontwarn javax.naming.**
