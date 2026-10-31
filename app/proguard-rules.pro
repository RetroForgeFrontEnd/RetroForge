# Network drives (smbj): Kerberos sign-in (org.ietf.jgss) and its event bus's expression filters (javax.el)
# aren't on Android and are never used; RetroForge signs in with a user name and password, or as a guest.
-dontwarn org.ietf.jgss.**
-dontwarn javax.el.**
-dontwarn java.rmi.UnmarshalException
